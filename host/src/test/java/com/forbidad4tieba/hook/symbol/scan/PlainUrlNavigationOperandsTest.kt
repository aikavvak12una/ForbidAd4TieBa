package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Call
import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Code
import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Field
import com.forbidad4tieba.hook.symbol.scan.PlainUrlDexOperands.Instruction
import org.junit.Assert.*
import org.junit.Test

class PlainUrlNavigationOperandsTest {
    private val message = "com.baidu.adp.framework.message.CustomResponsedMessage"
    private val page = "com.baidu.tbadk.TbPageContext"
    private val basePage = "com.baidu.adp.base.BdPageContext"
    private val string = "java.lang.String"
    private val context = "android.content.Context"
    private val payload = "renamed.Payload"
    private val span = "renamed.Span"
    private val handler = "renamed.Navigation"
    private val type = Field("Lrenamed/Payload;->category:I", payload, "int", false)
    private val url = Field("Lrenamed/Payload;->destination:Ljava/lang/String;", payload, string, false)
    private val text = Field("Lrenamed/Payload;->caption:Ljava/lang/String;", payload, string, false)
    private val getData = Call("Lcom/baidu/adp/framework/message/CustomResponsedMessage;->getData()Ljava/lang/Object;",
        message, emptyList(), "java.lang.Object", false)
    private val getPage = Call("Lrenamed/Activity;->getPageContext()Lcom/baidu/tbadk/TbPageContext;",
        "renamed.Activity", emptyList(), page, false)
    private val wrapper = Call("Lrenamed/Span;->c(Lcom/baidu/tbadk/TbPageContext;ILjava/lang/String;Ljava/lang/String;)V",
        span, listOf(page, "int", string, string), "void", true)
    private val delegate = Call("Lrenamed/Span;->d(Lcom/baidu/tbadk/TbPageContext;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        span, listOf(page, "int", string, string, string), "void", true)
    private val router = Call("Lrenamed/Span;->b(Landroid/content/Context;Lrenamed/Navigation;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        span, listOf(context, handler, "int", string, string, string), "void", true)
    private val initialized = setOf(type.descriptor, url.descriptor, text.descriptor)
    private val navigation = mapOf(wrapper.descriptor to PlainUrlNavigationOperands.Arguments(1, 2, 3))

    private data class Op(val words: List<Int>, val field: Field? = null, val call: Call? = null)
    private fun code(owner: String, parameters: List<String>, static: Boolean, registers: Int, ops: List<Op>): Code {
        var offset = 0
        return Code(owner, parameters, static, registers, ops.map {
            Instruction(offset, it.words[0] and 255, it.words, it.field, it.call).also { insn -> offset = insn.next }
        })
    }
    private fun invoke(call: Call, registers: List<Int>, range: Boolean = false): Op {
        val opcode = if (range) { if (call.isStatic) 0x77 else 0x74 } else { if (call.isStatic) 0x71 else 0x6e }
        return if (range) Op(listOf(opcode or (registers.size shl 8), 0, registers.first()), call = call)
        else {
            val packed = registers.take(4).mapIndexed { index, register -> register shl (4 * index) }.fold(0, Int::or)
            Op(listOf(opcode or (registers.size shl 12) or ((registers.getOrNull(4) ?: 0) shl 8), 0, packed), call = call)
        }
    }
    private fun load(field: Field, destination: Int, receiver: Int = 5) =
        Op(listOf((if (field.type == "int") 0x52 else 0x54) or (destination shl 8) or (receiver shl 12), 0), field)
    private fun result(destination: Int) = Op(listOf(0x0c or (destination shl 8)))
    private fun move(destination: Int, source: Int) = Op(listOf(0x07 or (destination shl 8) or (source shl 12)))
    private fun constant(destination: Int, value: Int) = Op(listOf(0x13 or (destination shl 8), value and 65535))
    private val returns = Op(listOf(0x0e))
    private fun consumerOps(loads: List<Op> = listOf(load(type, 1), load(url, 2), load(text, 3)),
        arguments: List<Int> = listOf(0, 1, 2, 3), range: Boolean = false,
        dataCall: Call = getData, dataReceiver: Int = 7): List<Op> =
        listOf(invoke(dataCall, if (dataCall.isStatic) emptyList() else listOf(dataReceiver)), result(5),
            Op(listOf(0x1f or (5 shl 8), 0)), invoke(getPage, listOf(6)), result(0)) +
            loads + invoke(wrapper, arguments, range) + returns
    private fun select(ops: List<Op>, initializedFields: Set<String> = initialized) =
        PlainUrlNavigationOperands.consumer(code("renamed.Activity", listOf(message), false, 8, ops), navigation, payload, initializedFields)

    @Test fun actualInvokeRegistersDetermineRolesWhenFieldLoadOrderChanges() {
        val expected = listOf(PlainUrlMessageDataSymbolScanner.Roles(type.descriptor, url.descriptor, text.descriptor))
        assertEquals(expected, select(consumerOps()))
        // Same fields, same opcode metadata: destination precedes URL in the load stream.
        // The invoke still forwards URL register 3 as its third argument.
        assertEquals(expected, select(consumerOps(listOf(load(type, 1), load(text, 2), load(url, 3)), listOf(0, 1, 3, 2))))
        assertEquals(expected, select(consumerOps(range = true)))
    }

    @Test fun identicalMetadataWithExchangedInvokeArgumentsExchangesTheSelectedStringRoles() {
        val ops = consumerOps()
        val swapped = consumerOps(arguments = listOf(0, 1, 3, 2))
        assertEquals(ops.map { it.words[0] and 255 }, swapped.map { it.words[0] and 255 })
        assertEquals(ops.mapNotNull { it.field }, swapped.mapNotNull { it.field })
        assertEquals(ops.mapNotNull { it.call }, swapped.mapNotNull { it.call })
        assertEquals(listOf(PlainUrlMessageDataSymbolScanner.Roles(type.descriptor, text.descriptor, url.descriptor)), select(swapped))
    }

    @Test fun overwrittenRegistersDuplicateArgumentsAndDifferentReceiversCannotProveRoles() {
        assertTrue(select(consumerOps(listOf(load(type, 1), load(url, 2), load(text, 2)))).isEmpty())
        assertTrue(select(consumerOps(arguments = listOf(0, 1, 2, 2))).isEmpty())
        assertTrue(select(consumerOps(listOf(load(type, 0), load(url, 2), load(text, 3)), listOf(0, 0, 2, 3))).isEmpty())
        assertTrue(select(consumerOps(listOf(load(type, 1), load(url, 2), load(text, 3, receiver = 7)))).isEmpty())
        assertTrue(select(consumerOps(), initialized - text.descriptor).isEmpty())
    }

    @Test fun incomingMessageAndItsExactGetDataSignatureAreRequired() {
        assertTrue(select(consumerOps(dataReceiver = 6)).isEmpty())
        assertTrue(select(consumerOps(dataCall = getData.copy(returns = string))).isEmpty())
        assertTrue(select(consumerOps(dataCall = getData.copy(isStatic = true))).isEmpty())
        assertTrue(select(consumerOps(dataCall = getData.copy(parameters = listOf("int")))).isEmpty())
        val staticCode = code("renamed.Activity", listOf(message), true, 8, consumerOps())
        assertTrue(PlainUrlNavigationOperands.consumer(staticCode, navigation, payload, initialized).isEmpty())
    }

    @Test fun unknownInstructionMalformedArityAndBranchIntoWindowFailClosed() {
        assertTrue(select(consumerOps().toMutableList().apply { add(3, Op(listOf(0x90, 0))) }).isEmpty())
        assertTrue(select(consumerOps().toMutableList().apply {
            val at = lastIndex - 1
            this[at] = this[at].copy(words = listOf(0x6071, 0, 0x3210))
        }).isEmpty())
        assertTrue(select(consumerOps().toMutableList().apply { add(3, Op(listOf(0x0538, 6))) }).isEmpty())
        assertTrue(select(consumerOps().toMutableList().apply {
            val at = lastIndex - 1
            this[at] = this[at].copy(call = wrapper.copy(isStatic = false))
        }).isEmpty())
    }

    @Test fun twoIncomingPayloadResultsMergedBeforeTheWindowHaveUnknownReceiverProvenance() {
        val base = consumerOps()
        val ops = base.take(3) + Op(listOf(0x0738, 10)) + base.take(3) + Op(listOf(0x0029, 2)) + base.drop(3)
        // One path keeps the first getData result, the other overwrites it with a second result.
        // No edge enters the window; rejecting this requires the forward merge itself.
        assertTrue(select(ops).isEmpty())
    }

    @Test fun earlyGuardsMayShareTheReturnAfterNavigationWithoutEnteringArgumentPreparation() {
        val base = consumerOps()
        val lengthBeforeReturn = base.dropLast(1).sumOf { it.words.size }
        val ops = listOf(Op(listOf(0x0738, lengthBeforeReturn + 2))) + base
        assertEquals(listOf(PlainUrlMessageDataSymbolScanner.Roles(type.descriptor, url.descriptor, text.descriptor)), select(ops))
    }

    @Test fun extendedMovesAndRangeInvokesPreserveIncomingMessageIdentity() {
        val base = consumerOps()
        val ops = listOf(Op(listOf(0x0508, 31)), Op(listOf(0x0009, 4, 30))) +
            listOf(invoke(getData, listOf(5), range = true), result(5), base[2], invoke(getPage, listOf(4)), result(0)) +
            base.drop(5)
        val code = code("renamed.Activity", listOf(message), false, 32, ops)
        assertEquals(listOf(PlainUrlMessageDataSymbolScanner.Roles(type.descriptor, url.descriptor, text.descriptor)),
            PlainUrlNavigationOperands.consumer(code, navigation, payload, initialized))
    }

    private fun wrapperCode(arguments: List<Int> = listOf(1, 2, 3, 4, 0)): Code =
        code(span, wrapper.parameters, true, 5, listOf(constant(0, 0), invoke(delegate, arguments), returns))
    private fun delegateCode(stringSources: List<Int> = listOf(9, 10, 11)): Code {
        val original = Call("Lcom/baidu/adp/base/BdPageContext;->getOrignalPage()Ljava/lang/Object;",
            basePage, emptyList(), "java.lang.Object", false)
        val activity = Call("Lcom/baidu/adp/base/BdPageContext;->getPageActivity()Landroid/app/Activity;",
            basePage, emptyList(), "android.app.Activity", false)
        return code(span, delegate.parameters, true, 12, listOf(invoke(original, listOf(7)), result(2),
            invoke(activity, listOf(7)), result(1), move(3, 8), move(4, stringSources[0]), move(5, stringSources[1]),
            move(6, stringSources[2]), invoke(router, (1..6).toList(), range = true), returns))
    }
    private fun routeCode(urlRegister: Int = 7, textRegister: Int = 8, optionalRegister: Int = 9): Code {
        val one = Call("Lrenamed/Navigation;->open(Landroid/content/Context;Ljava/lang/String;)V",
            handler, listOf(context, string), "void", false)
        val two = Call("Lrenamed/Navigation;->show(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V",
            handler, listOf(context, string, string), "void", false)
        // p0..p5 occupy r4..r9; compare p2 before the route calls.
        return code(span, router.parameters, true, 10, listOf(Op(listOf(0x0638, 2)),
            invoke(one, listOf(5, 4, urlRegister)), invoke(two, listOf(5, 4, urlRegister, textRegister)),
            invoke(two, listOf(5, 4, urlRegister, optionalRegister)), returns))
    }
    private fun chain(c: Code = wrapperCode(), d: Code = delegateCode(), b: Code = routeCode()) =
        PlainUrlNavigationOperands.chain(c, d, b, delegate.descriptor, router.descriptor)

    @Test fun inheritedPageGettersAndRealWrapperDelegateRouterRegistersProveRoles() {
        assertEquals(PlainUrlNavigationOperands.Arguments(1, 2, 3), chain())
        assertEquals(PlainUrlNavigationOperands.Arguments(1, 3, 2), chain(c = wrapperCode(listOf(1, 2, 4, 3, 0))))
        assertEquals(PlainUrlNavigationOperands.Arguments(1, 3, 2), chain(d = delegateCode(listOf(10, 9, 11))))
        assertEquals(PlainUrlNavigationOperands.Arguments(1, 2, 3),
            chain(c = wrapperCode(listOf(1, 2, 4, 3, 0)), d = delegateCode(listOf(10, 9, 11))))
    }

    @Test fun optionalNullDuplicateStringAndUnknownRouteOriginsCannotBecomeDisplayText() {
        assertNull(chain(c = wrapperCode(listOf(1, 2, 3, 3, 0))))
        assertNull(chain(d = delegateCode(listOf(9, 9, 11))))
        assertNull(chain(b = routeCode(textRegister = 9, optionalRegister = 9)))
        assertNull(chain(b = routeCode(urlRegister = 0)))
        val overwritten = routeCode().copy(instructions = routeCode().instructions.toMutableList().apply {
            val first = this[1]
            this[1] = first.copy(call = first.call?.copy(owner = "renamed.Other"))
        })
        assertNull(chain(b = overwritten))
        val badGetter = delegateCode().copy(instructions = delegateCode().instructions.toMutableList().apply {
            this[2] = this[2].copy(call = this[2].call?.copy(returns = string))
        })
        assertNull(chain(d = badGetter))
    }

    @Test fun forwardTypeBranchKeepsTextParameterBeforeOtherPathsOverwriteItsRegister() {
        val base = routeCode().instructions
        val one = requireNotNull(base[1].call)
        val two = requireNotNull(base[2].call)
        val b = code(span, router.parameters, true, 10, listOf(constant(0, 256),
            Op(listOf(0x0632, 9)), constant(8, 1024), invoke(one, listOf(5, 4, 7)), Op(listOf(0x0029, 8)),
            invoke(two, listOf(5, 4, 7, 8)), invoke(two, listOf(5, 4, 7, 9)), returns))
        assertEquals(PlainUrlNavigationOperands.Arguments(1, 2, 3), chain(b = b))
    }

    @Test fun aUrlRegisterOverwrittenOnOnePathIsUnknownAtAForwardMerge() {
        val base = routeCode().instructions
        val one = requireNotNull(base[1].call)
        val two = requireNotNull(base[2].call)
        val b = code(span, router.parameters, true, 10, listOf(constant(0, 1), Op(listOf(0x0632, 6)),
            constant(7, 0), Op(listOf(0x0029, 2)), invoke(one, listOf(5, 4, 7)),
            invoke(two, listOf(5, 4, 7, 8)), invoke(two, listOf(5, 4, 7, 9)), returns))
        assertNull(chain(b = b))
    }
}
