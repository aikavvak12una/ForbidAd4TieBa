package com.forbidad4tieba.hook.symbol.scan

import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** Scan-only operand evidence for methods already selected by DexKit. This is not a DEX inventory.
 * The source is the current session's DexKitScanBridge.sourcePath; no stream or body reaches runtime.
 * DexKit 2.2.0 getEncodeId identifies the standard classesN.dex and method_id. DEX 041 containers,
 * handlers, unfamiliar instruction formats and any descriptor/opcode mismatch fail closed. */
internal class PlainUrlDexOperands(sourcePath: String) : AutoCloseable {
    internal data class Field(val descriptor: String, val owner: String, val type: String, val isStatic: Boolean)
    internal data class Call(val descriptor: String, val owner: String, val parameters: List<String>, val returns: String, val isStatic: Boolean)
    internal data class Instruction(val offset: Int, val opcode: Int, val words: List<Int>, val field: Field? = null, val call: Call? = null) {
        val next: Int get() = offset + words.size
        fun arguments(): List<Int>? = when (opcode) {
            in 0x6e..0x72 -> {
                val count = words[0] ushr 12
                if (count > 5) null else listOf(words[2] and 15, words[2] ushr 4 and 15,
                    words[2] ushr 8 and 15, words[2] ushr 12 and 15, words[0] ushr 8 and 15).take(count)
            }
            in 0x74..0x78 -> List(words[0] ushr 8) { words[2] + it }
            else -> null
        }
        fun branch(): Int? = when (opcode) {
            0x28 -> offset + (words[0] ushr 8).toByte().toInt()
            0x29, in 0x32..0x3d -> offset + words[1].toShort().toInt()
            0x2a -> offset + (words[1] or (words[2] shl 16))
            else -> null
        }
    }
    internal data class Code(val owner: String, val parameters: List<String>, val isStatic: Boolean,
        val registers: Int, val instructions: List<Instruction>) {
        val parameterBase: Int get() = registers - parameters.size - if (isStatic) 0 else 1
    }
    /** Immutable evidence for one DexKit-selected method; also permits pure malformed-DEX tests. */
    internal data class SelectedEvidence(val descriptor: String, val owner: String, val parameters: List<String>,
        val isStatic: Boolean, val opcodes: List<Int>, val fields: List<Pair<Field, Boolean>> = emptyList(),
        val calls: List<Call> = emptyList())

    private val zip = ZipFile(sourcePath)
    private val dexes = HashMap<Int, Dex?>()
    private val headers = HashMap<Int, Boolean>()

    fun read(method: MethodData): Code? = runCatching {
        val identity = method.getEncodeId()
        val dexId = (identity ushr 32).toInt()
        val methodId = identity.toInt()
        if (dexId !in 0..1023 || methodId < 0) return null
        // A preceding 041 logical container changes subsequent dexId mapping in DexKit.
        if ((0..dexId).any { !standardHeader(it) }) return null
        val dex = if (dexes.containsKey(dexId)) dexes[dexId] else {
            val entry = zip.getEntry(entryName(dexId)) ?: return null
            runCatching {
                require(entry.size in 112..134217728)
                val data = ByteArray(entry.size.toInt())
                zip.getInputStream(entry).use { stream ->
                    var at = 0
                    while (at < data.size) {
                        val count = stream.read(data, at, data.size - at)
                        require(count > 0)
                        at += count
                    }
                    require(stream.read() == -1)
                }
                Dex(data)
            }.getOrNull().also { dexes[dexId] = it }
        } ?: return null
        val evidence = SelectedEvidence(method.descriptor, method.declaredClassName, method.paramTypeNames,
            Modifier.isStatic(method.modifiers), method.opCodes, method.usingFields.map { use ->
                Field(use.field.descriptor, use.field.declaredClassName, use.field.typeName,
                    Modifier.isStatic(use.field.modifiers)) to use.usingType.isRead()
            }, method.invokes.map { call ->
                Call(call.descriptor, call.declaredClassName, call.paramTypeNames, call.returnTypeName,
                    Modifier.isStatic(call.modifiers))
            })
        dex.read(methodId, evidence)
    }.getOrNull()

    private fun standardHeader(index: Int): Boolean = headers.getOrPut(index) {
        val entry = zip.getEntry(entryName(index)) ?: return@getOrPut false
        zip.getInputStream(entry).use { stream ->
            val header = ByteArray(8)
            var count = 0
            while (count < header.size) {
                val read = stream.read(header, count, header.size - count)
                if (read < 0) return@use false
                count += read
            }
            standardVersion(header)
        }
    }

    override fun close() { try { zip.close() } finally { dexes.clear(); headers.clear() } }

    private class Dex(private val bytes: ByteArray) {
        private val strings = HashMap<Int, String>()
        init {
            require(bytes.size >= 112 && standardVersion(bytes.copyOfRange(0, 8)))
            require(uint(32) == bytes.size && uint(36) == 112 && uint(40) == 0x12345678)
        }
        private fun ushort(at: Int): Int {
            require(at >= 0 && at.toLong() + 2 <= bytes.size)
            return (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
        }
        private fun uint(at: Int): Int {
            val value = ushort(at) or (ushort(at + 2) shl 16)
            require(value >= 0)
            return value
        }
        private fun item(sizeAt: Int, offsetAt: Int, index: Int, width: Int): Int {
            require(index >= 0 && index < uint(sizeAt))
            val at = uint(offsetAt).toLong() + index.toLong() * width
            require(at >= 112 && at + width <= bytes.size)
            return at.toInt()
        }
        private inner class Cursor(var at: Int) {
            fun uleb(): Int {
                var value = 0L
                for (shift in 0..28 step 7) {
                    require(at in bytes.indices)
                    val byte = bytes[at++].toInt() and 255
                    value = value or ((byte and 127).toLong() shl shift)
                    if (byte and 128 == 0) { require(value <= Int.MAX_VALUE); return value.toInt() }
                }
                error("Invalid ULEB128")
            }
        }
        private fun string(index: Int): String = strings.getOrPut(index) {
            val cursor = Cursor(uint(item(56, 60, index, 4)))
            val length = cursor.uleb()
            val start = cursor.at
            while (cursor.at in bytes.indices && bytes[cursor.at] != 0.toByte()) {
                // Selected host descriptors use ASCII; do not guess MUTF-8 names.
                require(bytes[cursor.at].toInt() in 1..127)
                cursor.at++
            }
            require(cursor.at in bytes.indices && cursor.at - start == length)
            String(bytes, start, length, Charsets.US_ASCII)
        }
        private fun type(index: Int): String = string(uint(item(64, 68, index, 4)))
        private fun methodDescriptor(index: Int): String {
            val method = item(88, 92, index, 8)
            val proto = item(72, 76, ushort(method + 2), 12)
            val parameters = uint(proto + 8)
            val parameterTypes = if (parameters == 0) "" else {
                val count = uint(parameters)
                require(parameters.toLong() + 4 + count.toLong() * 2 <= bytes.size)
                (0 until count).joinToString("") { type(ushort(parameters + 4 + it * 2)) }
            }
            return type(ushort(method)) + "->" + string(uint(method + 4)) + "(" + parameterTypes + ")" + type(uint(proto + 4))
        }
        private fun fieldDescriptor(index: Int): String {
            val field = item(80, 84, index, 8)
            return type(ushort(field)) + "->" + string(uint(field + 4)) + ":" + type(ushort(field + 2))
        }
        fun read(methodId: Int, method: SelectedEvidence): Code? {
            if (methodDescriptor(methodId) != method.descriptor || method.parameters.any { it == "long" || it == "double" }) return null
            val classIndex = ushort(item(88, 92, methodId, 8))
            val definitions = (0 until uint(96)).map { item(96, 100, it, 32) }.filter { uint(it) == classIndex }
            val definition = definitions.singleOrNull() ?: return null
            val data = uint(definition + 24).takeIf { it != 0 } ?: return null
            val cursor = Cursor(data)
            val staticFields = cursor.uleb()
            val instanceFields = cursor.uleb()
            val directMethods = cursor.uleb()
            val virtualMethods = cursor.uleb()
            require(staticFields.toLong() + instanceFields + directMethods + virtualMethods <= bytes.size)
            repeat(staticFields + instanceFields) { cursor.uleb(); cursor.uleb() }
            var codeOffset: Int? = null
            for (count in listOf(directMethods, virtualMethods)) {
                var index = 0
                repeat(count) {
                    index = Math.addExact(index, cursor.uleb())
                    val access = cursor.uleb()
                    val code = cursor.uleb()
                    if (index == methodId) {
                        require(codeOffset == null && Modifier.isStatic(access) == method.isStatic)
                        codeOffset = code
                    }
                }
            }
            val code = codeOffset?.takeIf { it != 0 } ?: return null
            require(code % 4 == 0 && ushort(code + 6) == 0) // no exception handler paths
            val registers = ushort(code)
            require(ushort(code + 2) == method.parameters.size + if (method.isStatic) 0 else 1)
            val count = uint(code + 12)
            require(code.toLong() + 16 + count.toLong() * 2 <= bytes.size)
            val expectedUnits = method.opcodes.fold(0L) { total, opcode ->
                total + (width(opcode) ?: return null)
            }
            // Selected navigation methods are small. Bound allocation before boxing the body,
            // and require DexKit's exact opcode widths to account for all declared code units.
            require(count <= MAX_CODE_UNITS && count.toLong() == expectedUnits)
            val words = List(count) { ushort(code + 16 + it * 2) }
            val instructions = ArrayList<Instruction>()
            var offset = 0
            val fieldUses = method.fields.groupBy { it.first.descriptor to it.second }
            val calls = method.calls.groupBy { it.descriptor }
            val seenFields = ArrayList<Pair<String, Boolean>>()
            val seenCalls = ArrayList<String>()
            for (opcode in method.opcodes) {
                require(offset < words.size && words[offset] and 255 == opcode)
                val width = width(opcode) ?: return null
                require(offset + width <= words.size && (opcode != 0 || words[offset] == 0))
                val units = words.subList(offset, offset + width).toList()
                val field = if (opcode in 0x52..0x6d) {
                    val key = fieldDescriptor(units[1]) to (opcode in 0x52..0x58 || opcode in 0x60..0x66)
                    // Identical descriptor/direction records carry identical type metadata;
                    // the raw index selects identity, while the multiset below validates repeats.
                    val use = fieldUses[key]?.firstOrNull() ?: return null
                    seenFields += key
                    use.first
                } else null
                val call = if (opcode in 0x6e..0x72 || opcode in 0x74..0x78) {
                    val descriptor = methodDescriptor(units[1])
                    val invoked = calls[descriptor]?.firstOrNull() ?: return null
                    seenCalls += descriptor
                    invoked
                } else null
                instructions += Instruction(offset, opcode, units, field, call)
                offset += width
            }
            require(offset == words.size && seenFields.groupingBy { it }.eachCount() == fieldUses.mapValues { it.value.size } &&
                seenCalls.groupingBy { it }.eachCount() == calls.mapValues { it.value.size })
            return Code(method.owner, method.parameters, method.isStatic, registers, instructions)
        }
    }

    companion object {
        private fun entryName(dexId: Int): String = if (dexId == 0) "classes.dex" else "classes${dexId + 1}.dex"
        private const val MAX_CODE_UNITS = 8192
        internal fun selectedCode(bytes: ByteArray, methodId: Int, evidence: SelectedEvidence): Code? =
            runCatching { Dex(bytes).read(methodId, evidence) }.getOrNull()
        private fun standardVersion(header: ByteArray): Boolean = header.size == 8 &&
            header.take(4) == listOf(100, 101, 120, 10).map(Int::toByte) && header[7] == 0.toByte() &&
            (String(header, 4, 3, Charsets.US_ASCII).toIntOrNull()?.let { it in 35..40 } == true)

        // Only formats used by this navigation chain and its Titan guard tail are decoded.
        internal fun width(opcode: Int): Int? = when (opcode) {
            0x00, 0x01, 0x04, 0x07, in 0x0a..0x12, 0x1d, 0x1e, 0x21, 0x28 -> 1
            0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29,
            in 0x32..0x3d, in 0x44..0x6d -> 2
            0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x2a, in 0x6e..0x72, in 0x74..0x78 -> 3
            0x18 -> 5
            else -> null
        }
    }
}
