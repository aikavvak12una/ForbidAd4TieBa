package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier

/** Trace the native preview builder and its row adapter; sample obfuscated names never enter the rule. */
internal object InlineReplySymbolScanner {
    const val VIEW = "com.baidu.tieba.pb.pagebrowser.comment.floor.CommentFloorView"
    const val SUB = "com.baidu.tieba.pb.pagebrowser.comment.domain.sub.SubUseCaseKt"
    const val POST = "com.baidu.tieba.tbadkcore.data.PostData"
    const val THREAD = "com.baidu.tbadk.core.data.ThreadData"
    const val FORUM = "com.baidu.tbadk.core.data.ForumData"
    const val ADAPTER = "com.baidu.tieba.feed.list.TemplateAdapter"

    fun scan(cl: ClassLoader, logger: ScanLogger?): String? =
        ScanDexQueries.query("InlineReplyRepair", logger) { bridge ->
            fun <T> one(label: String, values: List<T>): T = requireNotNull(
                uniqueSemanticCandidate("InlineReplyRepair.$label", values.distinct(), logger),
            ) { "Missing or ambiguous $label" }
            fun methods(name: String) = checkNotNull(bridge.getClassData(name)).methods
            val bind = one("bind", methods(VIEW).filter {
                it.returnTypeName == "void" && it.paramTypeNames.size == 1 &&
                    "floorData" in it.usingStrings && it.calls("com.baidu.tbadk.core.TbadkCoreApplication", "getSkinType")
            })
            val row = bind.paramTypeNames.single()
            check(methods(row).any { "CommentFloorData(dataList=" in it.usingStrings })
            val preview = one("preview", methods(SUB).filter {
                it.returnTypeName == "java.util.List" && it.paramTypeNames == listOf(POST, THREAD, FORUM) &&
                    "subDatas" in it.usingStrings
            })
            val children = one("children", preview.invokes.filter {
                it.declaredClassName == POST && it.paramTypeNames.isEmpty() && it.returnTypeName == "java.util.ArrayList"
            })
            val count = one("count", preview.invokes.filter {
                it.declaredClassName == POST && it.paramTypeNames.isEmpty() && it.returnTypeName == "int"
            })
            val build = one("build", methods(SUB).filter {
                it.returnTypeName == "void" && it.paramTypeNames == listOf(POST, "java.util.List", THREAD, FORUM) &&
                    it.invokes.any { call -> call.descriptor == preview.descriptor }
            })
            val wrapper = one("wrapper", build.invokes.filter {
                it.isConstructor && it.paramTypeNames.size == 1 &&
                    bridge.getClassData(it.paramTypeNames.single())?.fields?.any { field -> field.typeName == POST } == true
            }).declaredClassName
            val adapterType = Class.forName(ADAPTER, false, cl)
            val adapter = one("adapter", bind.usingFields.map { it.field }.filter {
                it.declaredClassName == VIEW && !Modifier.isStatic(it.modifiers) &&
                    adapterType.isAssignableFrom(it.getFieldInstance(cl).type)
            }.distinctBy { it.descriptor })
            val parser = requireNotNull(CommentFilterSymbolScanner.scan(CommentFilterSymbolScanner.Path.FLOOR_NATIVE, cl, logger))
            val parsed = CommentFilterSymbolScanner.restore(parser, CommentFilterSymbolScanner.Path.FLOOR_NATIVE, cl)
            val parserDex = checkNotNull(bridge.getMethodData(parsed))
            val setter = one("parsedChildrenSetter", parserDex.invokes.filter {
                it.declaredClassName == parsed.declaringClass.name && it.returnTypeName == "void" &&
                    it.paramTypeNames == listOf("java.util.ArrayList")
            })
            val replies = one("parsedChildren", setter.instanceFields(parsed.declaringClass.name, "java.util.ArrayList", FieldUsingType.Write))
            JSONObject().put("bind", bind.methodName).put("row", row).put("adapter", adapter.fieldName)
                .put("children", children.methodName).put("count", count.methodName)
                .put("build", build.methodName).put("wrapper", wrapper)
                .put("parser", parser).put("replies", replies.fieldName).toString()
        }
}
