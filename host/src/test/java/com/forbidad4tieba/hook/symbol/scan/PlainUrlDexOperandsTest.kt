package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.*
import org.junit.Test

class PlainUrlDexOperandsTest {
    private val selected = PlainUrlDexOperands.SelectedEvidence("Lfixture/Owner;->selected()V", "fixture.Owner",
        emptyList(), true, listOf(0x0e))

    /** One exact method_id, one class_def and one encoded direct method; no inventory search. */
    private fun dex(words: List<Int> = listOf(0x0e)): ByteArray {
        val stringStart = 200 // Keep descriptors before code so truncated-code tests isolate code bounds.
        val bytes = ByteArray(maxOf(512, 274 + words.size * 2 + 16))
        "dex\n035\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes.uint(32, bytes.size)
        bytes.uint(36, 112)
        bytes.uint(40, 0x12345678)
        bytes.uint(56, 3); bytes.uint(60, 112) // string_ids
        bytes.uint(64, 2); bytes.uint(68, 124) // type_ids
        bytes.uint(72, 1); bytes.uint(76, 132) // proto_ids
        bytes.uint(88, 1); bytes.uint(92, 144) // method_ids
        bytes.uint(96, 1); bytes.uint(100, 152) // class_defs
        bytes.uint(124, 0); bytes.uint(128, 2) // owner and void types
        bytes.uint(136, 1) // selected proto return type = void
        bytes.uint(148, 1) // selected method name string
        bytes.uint(176, 184) // class_data_off
        listOf(0, 0, 1, 0, 0, 9, 0x80, 2).forEachIndexed { index, value ->
            bytes[184 + index] = value.toByte() // code_off = 256, public static
        }
        bytes.uint(268, words.size)
        words.forEachIndexed { index, word -> bytes.ushort(272 + index * 2, word) }
        var nextString = stringStart
        listOf("Lfixture/Owner;", "selected", "V").forEachIndexed { index, value ->
            bytes.uint(112 + index * 4, nextString)
            bytes[nextString++] = value.length.toByte()
            value.toByteArray(Charsets.US_ASCII).copyInto(bytes, nextString)
            nextString += value.length + 1
        }
        return bytes
    }
    private fun ByteArray.ushort(at: Int, value: Int) {
        this[at] = value.toByte(); this[at + 1] = (value ushr 8).toByte()
    }
    private fun ByteArray.uint(at: Int, value: Int) {
        ushort(at, value); ushort(at + 2, value ushr 16)
    }
    private fun read(bytes: ByteArray, methodId: Int = 0,
        evidence: PlainUrlDexOperands.SelectedEvidence = selected) = PlainUrlDexOperands.selectedCode(bytes, methodId, evidence)

    @Test fun exactSelectedMethodAndStandardVersionsDecodeTheSameRawBody() {
        for (version in 35..40) {
            val bytes = dex()
            version.toString().padStart(3, '0').toByteArray(Charsets.US_ASCII).copyInto(bytes, 4)
            val code = requireNotNull(read(bytes))
            assertEquals("fixture.Owner", code.owner)
            assertEquals(0, code.registers)
            assertEquals(listOf(0x0e), code.instructions.map { it.opcode })
        }
    }

    @Test fun shortWrongSizeEndianAndContainerHeadersFailClosed() {
        assertNull(read(ByteArray(7)))
        assertNull(read(dex().copyOf(111)))
        assertNull(read(dex().apply { uint(32, size - 1) }))
        assertNull(read(dex().apply { uint(36, 120) }))
        assertNull(read(dex().apply { uint(40, 0x78563412) }))
        assertNull(read(dex().apply { "041".toByteArray(Charsets.US_ASCII).copyInto(this, 4) }))
    }

    @Test fun methodIdDescriptorAndClassDataStaticnessMustMatchDexKitSelection() {
        assertNull(read(dex(), methodId = 1))
        assertNull(read(dex(), evidence = selected.copy(descriptor = "Lfixture/Owner;->other()V")))
        assertNull(read(dex(), evidence = selected.copy(isStatic = false)))
        assertNull(read(dex().apply { uint(176, 0) }))
        assertNull(read(dex().apply { this[190] = 0x80.toByte(); this[191] = 0x80.toByte(); this[192] = 0x80.toByte()
            this[193] = 0x80.toByte(); this[194] = 0x80.toByte() }))
    }

    @Test fun truncatedMalformedAndUnhandledCodeItemsFailBeforeReturningEvidence() {
        assertNull(read(dex().copyOf(273).apply { uint(32, size) }))
        assertNull(read(dex().apply { uint(268, Int.MAX_VALUE) }))
        assertNull(read(dex().apply { ushort(262, 1) })) // try_items/handlers are unsupported
        assertNull(read(dex().apply { ushort(258, 1) })) // ins_size differs from selected signature
        assertNull(read(dex(listOf(0x0013)), evidence = selected.copy(opcodes = listOf(0x13)))) // missing second unit
        assertNull(read(dex(listOf(0x0100)), evidence = selected.copy(opcodes = listOf(0x00)))) // packed-switch payload
        assertNull(read(dex(listOf(0x0026, 0, 0)), evidence = selected.copy(opcodes = listOf(0x26)))) // unfamiliar format
    }

    @Test fun opcodeWidthsMustConsumeExactlyTheBodyWithinTheNavigationBudget() {
        assertNull(read(dex(), evidence = selected.copy(opcodes = listOf(0x00))))
        assertNull(read(dex(listOf(0x0e, 0x0e))))
        assertNull(read(dex(List(8193) { 0x0e }), evidence = selected.copy(opcodes = List(8193) { 0x0e })))
        assertNotNull(read(dex(listOf(0x0013, 256, 0x0e)), evidence = selected.copy(opcodes = listOf(0x13, 0x0e))))
    }
}
