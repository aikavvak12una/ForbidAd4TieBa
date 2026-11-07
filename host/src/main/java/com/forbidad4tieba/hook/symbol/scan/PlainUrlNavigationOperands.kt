package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Call
import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Code
import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Instruction

/** Private to PlainUrl's selected consumer -> page wrapper -> dispatch chain.
 * Tracks register provenance, not values or host behavior. Only acyclic normal guard paths and
 * the instruction forms below are supported; an unknown write/path cannot silently retain a value. */
internal object PlainUrlNavigationOperands {
    private const val PAGE = "com.baidu.tbadk.TbPageContext"
    private const val PAGE_BASE = "com.baidu.adp.base.BdPageContext"
    private const val CONTEXT = "android.content.Context"
    private const val STRING = "java.lang.String"
    private const val CUSTOM_MESSAGE = "com.baidu.adp.framework.message.CustomResponsedMessage"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"

    internal data class Arguments(val type: Int, val url: Int, val text: Int)
    private sealed interface Origin {
        data class Parameter(val index: Int) : Origin
        data class Constant(val value: Int) : Origin
        data class Result(val at: Int, val call: Call, val receiver: Origin?) : Origin
        data class FieldValue(val field: PlainUrlDexOperands.Field, val receiver: Origin) : Origin
    }
    private data class CallSite(val instruction: Instruction, val arguments: List<Origin?>)
    private data class Trace(val instructions: List<Instruction>, val calls: List<CallSite>, val targets: Set<Int>, val typeTested: Boolean)
    private data class State(val registers: MutableList<Origin?>, var result: Origin.Result? = null) {
        fun copyState() = State(registers.toMutableList(), result)
        fun merge(other: State) {
            registers.indices.forEach { if (registers[it] != other.registers[it]) registers[it] = null }
            if (result != other.result) result = null
        }
    }

    /** Derive wrapper argument roles from the route's real consumption, then prove c -> d -> b.
     * The null optional string is followed separately so profile metadata cannot become display text. */
    fun chain(wrapper: Code, delegate: Code, route: Code, delegateDescriptor: String, routeDescriptor: String): Arguments? = runCatching {
        require(wrapper.isStatic && delegate.isStatic && route.isStatic)
        require(wrapper.parameters == listOf(PAGE, "int", STRING, STRING) &&
            delegate.parameters == listOf(PAGE, "int", STRING, STRING, STRING) && route.parameters.size == 6 &&
            route.parameters.first() == CONTEXT && route.parameters.drop(2) == listOf("int", STRING, STRING, STRING))
        val c = trace(wrapper) ?: return null
        val d = trace(delegate) ?: return null
        val b = trace(route) ?: return null
        val forwardC = c.calls.singleOrNull { it.instruction.call?.descriptor == delegateDescriptor }?.arguments ?: return null
        val forwardD = d.calls.singleOrNull { it.instruction.call?.descriptor == routeDescriptor }?.arguments ?: return null
        require(forwardC.size == 5 && forwardD.size == 6 && forwardC[0] == Origin.Parameter(0) &&
            forwardC[1] == Origin.Parameter(1) && forwardD[2] == Origin.Parameter(1))
        fun pageResult(value: Origin?, name: String, returns: String): Boolean = value is Origin.Result &&
            value.receiver == Origin.Parameter(0) && value.call.owner in setOf(PAGE, PAGE_BASE) &&
            !value.call.isStatic && value.call.descriptor.substringAfter("->").substringBefore('(') == name &&
            value.call.parameters.isEmpty() && value.call.returns == returns
        require(pageResult(forwardD[0], "getPageActivity", "android.app.Activity") &&
            pageResult(forwardD[1], "getOrignalPage", "java.lang.Object"))
        val cStrings = forwardC.drop(2)
        require(cStrings.toSet() == setOf(Origin.Parameter(2), Origin.Parameter(3), Origin.Constant(0)))
        val dStrings = forwardD.drop(3)
        require(dStrings.toSet() == setOf(Origin.Parameter(2), Origin.Parameter(3), Origin.Parameter(4)))
        val carried = dStrings.map { value -> forwardC[(value as Origin.Parameter).index] }
        val optional = carried.indexOf(Origin.Constant(0)).takeIf { it >= 0 }?.plus(3) ?: return null
        val handler = route.parameters[1]
        require(b.calls.isNotEmpty() && b.typeTested)
        val single = ArrayList<Int>()
        val double = ArrayList<Pair<Int, Int>>()
        for (site in b.calls) {
            val call = requireNotNull(site.instruction.call)
            require(call.owner == handler && !call.isStatic && call.returns == "void" &&
                site.arguments[0] == Origin.Parameter(1) && site.arguments[1] == Origin.Parameter(0))
            val url = (site.arguments.getOrNull(2) as? Origin.Parameter)?.index ?: return null
            require(url in 3..5)
            when (call.parameters) {
                listOf(CONTEXT, STRING), listOf(CONTEXT, STRING, "boolean") -> single += url
                listOf(CONTEXT, STRING, STRING) -> {
                    val text = (site.arguments.getOrNull(3) as? Origin.Parameter)?.index ?: return null
                    require(text in 3..5 && text != url)
                    double += url to text
                }
                else -> return null
            }
        }
        val url = single.distinct().singleOrNull() ?: return null
        require(double.isNotEmpty() && double.all { it.first == url })
        val urlParameter = carried[url - 3] as? Origin.Parameter ?: return null
        val textParameter = setOf(2, 3).single { it != urlParameter.index }
        val text = carried.indexOf(Origin.Parameter(textParameter)).takeIf { it >= 0 }?.plus(3) ?: return null
        require(text != optional && double.any { it.second == text } && double.all { it.second == text || it.second == optional })
        Arguments(1, urlParameter.index, textParameter)
    }.getOrNull()

    /** Each role follows the actual invoke register, independent of the order of the three igets. */
    fun consumer(
        code: Code,
        navigation: Map<String, Arguments>,
        payloadOwner: String,
        initialized: Set<String>,
    ): List<PlainUrlMessageDataSymbolScanner.Roles> = runCatching {
        require(!code.isStatic && code.parameters == listOf(CUSTOM_MESSAGE))
        val trace = trace(code) ?: return emptyList()
        trace.calls.mapNotNull { site ->
            val call = requireNotNull(site.instruction.call)
            val arguments = navigation[call.descriptor] ?: return@mapNotNull null
            val at = trace.instructions.indexOfFirst { it.offset == site.instruction.offset }
            if (at < 5 || at + 1 >= trace.instructions.size) return@mapNotNull null
            val window = trace.instructions.subList(at - 5, at + 2)
            if (window[0].opcode !in setOf(0x6e, 0x74) || window[1].opcode != 0x0c ||
                window.subList(2, 5).any { it.opcode !in setOf(0x52, 0x54) } ||
                window[5].opcode !in setOf(0x71, 0x77) || window[6].opcode != 0x0e ||
                // Early guards may share the final return; they must not enter argument preparation.
                window.subList(1, 6).any { it.offset in trace.targets }) return@mapNotNull null
            val getter = window[0].call ?: return@mapNotNull null
            if (getter.descriptor.substringAfter("->").substringBefore('(') != "getPageContext" ||
                getter.parameters.isNotEmpty() || getter.returns != PAGE) return@mapNotNull null
            val context = site.arguments.firstOrNull() as? Origin.Result ?: return@mapNotNull null
            if (context.at != window[0].offset) return@mapNotNull null
            val loads = window.subList(2, 5)
            if (loads.map { it.words[0] ushr 8 and 15 }.distinct().size != 3) return@mapNotNull null
            val type = site.arguments.getOrNull(arguments.type) as? Origin.FieldValue ?: return@mapNotNull null
            val url = site.arguments.getOrNull(arguments.url) as? Origin.FieldValue ?: return@mapNotNull null
            val text = site.arguments.getOrNull(arguments.text) as? Origin.FieldValue ?: return@mapNotNull null
            if (type.receiver != url.receiver || type.receiver != text.receiver || type.receiver !is Origin.Result ||
                type.receiver.receiver != Origin.Parameter(0) || type.receiver.call.owner != CUSTOM_MESSAGE ||
                type.receiver.call.descriptor.substringAfter("->").substringBefore('(') != "getData" ||
                type.receiver.call.isStatic || type.receiver.call.parameters.isNotEmpty() ||
                type.receiver.call.returns != "java.lang.Object") return@mapNotNull null
            val selected = listOf(type.field, url.field, text.field)
            if (selected.any { it.owner != payloadOwner || it.isStatic || it.descriptor !in initialized } ||
                type.field.type != "int" || url.field.type != STRING || text.field.type != STRING ||
                url.field.descriptor == text.field.descriptor ||
                selected.map { it.descriptor }.toSet() != loads.map { it.field?.descriptor }.toSet()) return@mapNotNull null
            PlainUrlMessageDataSymbolScanner.Roles(type.field.descriptor, url.field.descriptor, text.field.descriptor)
        }.distinct()
    }.getOrDefault(emptyList())

    private fun trace(code: Code): Trace? = runCatching {
        require(code.parameterBase >= 0 && code.parameters.none { it == "long" || it == "double" })
        var instructions = code.instructions
        val first = instructions.firstOrNull() ?: return null
        if (first.opcode == 0x62 && first.field?.type == INTERCEPTABLE) {
            val guard = instructions.getOrNull(1) ?: return null
            val register = first.words[0] ushr 8
            val end = guard.branch() ?: return null
            require(first.field.isStatic && first.field.owner == code.owner && register < code.parameterBase &&
                guard.opcode == 0x39 && guard.words[0] ushr 8 == register && end > guard.next)
            val endIndex = instructions.indexOfFirst { it.offset == end }
            require(endIndex > 2 && instructions[endIndex - 1].opcode == 0x0e)
            instructions = instructions.subList(2, endIndex)
        }
        require(instructions.isNotEmpty() && instructions.last().opcode == 0x0e)
        val offsets = instructions.mapTo(HashSet()) { it.offset }
        val targets = instructions.mapNotNull { instruction -> instruction.branch()?.also {
            require(it > instruction.offset && it in offsets)
        } }.toSet()
        val initial = State(MutableList(code.registers) { null })
        var register = code.parameterBase
        if (!code.isStatic) initial.registers[register++] = Origin.Parameter(-1)
        code.parameters.indices.forEach { initial.registers[register++] = Origin.Parameter(it) }
        val states = hashMapOf(instructions.first().offset to initial)
        val calls = ArrayList<CallSite>()
        var typeTested = false
        for (instruction in instructions) {
            val incoming = states[instruction.offset] ?: continue
            val state = incoming.copyState()
            val word = instruction.words[0]
            fun value(index: Int): Origin? { require(index in state.registers.indices); return state.registers[index] }
            fun write(index: Int, value: Origin?) { require(index in state.registers.indices); state.registers[index] = value }
            val pending = state.result
            state.result = null
            when (instruction.opcode) {
                0x00, 0x0e -> Unit
                0x01, 0x07 -> write(word ushr 8 and 15, value(word ushr 12))
                0x02, 0x08 -> write(word ushr 8, value(instruction.words[1]))
                0x03, 0x09 -> write(instruction.words[1], value(instruction.words[2]))
                0x0a, 0x0c -> {
                    require(pending != null && instructions.any { it.offset == pending.at && it.next == instruction.offset })
                    write(word ushr 8, pending)
                }
                0x12 -> write(word ushr 8 and 15, Origin.Constant((word ushr 12).let { if (it >= 8) it - 16 else it }))
                0x13 -> write(word ushr 8, Origin.Constant(instruction.words[1].toShort().toInt()))
                0x14 -> write(word ushr 8, Origin.Constant(instruction.words[1] or (instruction.words[2] shl 16)))
                0x15 -> write(word ushr 8, Origin.Constant(instruction.words[1] shl 16))
                0x1f -> value(word ushr 8) // check-cast preserves the register's provenance
                0x20 -> { value(word ushr 12); write(word ushr 8 and 15, null) }
                0x52, 0x54 -> {
                    val field = requireNotNull(instruction.field)
                    require(!field.isStatic)
                    val receiver = value(word ushr 12)
                    write(word ushr 8 and 15, receiver?.let { Origin.FieldValue(field, it) })
                }
                0x62 -> { require(instruction.field?.isStatic == true); write(word ushr 8, null) }
                in 0x6e..0x72, in 0x74..0x78 -> {
                    val call = requireNotNull(instruction.call)
                    val args = instruction.arguments() ?: return null
                    require(call.parameters.none { it == "long" || it == "double" } &&
                        args.size == call.parameters.size + if (call.isStatic) 0 else 1)
                    require(call.isStatic == (instruction.opcode == 0x71 || instruction.opcode == 0x77))
                    val origins = args.map(::value)
                    calls += CallSite(instruction, origins)
                    if (call.returns != "void") state.result = Origin.Result(instruction.offset, call, if (call.isStatic) null else origins.first())
                }
                in 0x32..0x37 -> { if (value(word ushr 8 and 15) == Origin.Parameter(2) || value(word ushr 12) == Origin.Parameter(2)) typeTested = true }
                in 0x38..0x3d -> { if (value(word ushr 8) == Origin.Parameter(2)) typeTested = true }
                in 0x28..0x2a -> Unit
                else -> return null
            }
            val successors = when (instruction.opcode) {
                0x0e -> emptyList()
                in 0x28..0x2a -> listOf(requireNotNull(instruction.branch()))
                in 0x32..0x3d -> listOf(requireNotNull(instruction.branch()), instruction.next)
                else -> listOf(instruction.next)
            }
            successors.forEach { target ->
                require(target in offsets)
                states[target]?.merge(state) ?: run { states[target] = state.copyState() }
            }
        }
        Trace(instructions, calls, targets, typeTested)
    }.getOrNull()
}
