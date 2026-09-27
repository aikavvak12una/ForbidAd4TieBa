package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class FeedTemplateKeyRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): List<String> {
        val holder = ScanReflection.safeFindClass("com.baidu.tieba.feed.list.TemplateAdapter\$TemplateVH", cl) ?: return emptyList()
        return holder.declaredFields.map { it.type }.filter { it.isInterface }.map { it.name }.distinct()
    }

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (!cls.isInterface) return null
        val holder = ScanReflection.safeFindClass("com.baidu.tieba.feed.list.TemplateAdapter\$TemplateVH", cl) ?: return null
        if (holder.declaredFields.none { it.type == cls } || holder.declaredMethods.none {
                it.returnType == Void.TYPE && cls in it.parameterTypes
            }) return null
        val methods = cls.declaredMethods
        val key = methods.singleOrNull { it.parameterCount == 0 && it.returnType == String::class.java } ?: return null
        val payload = methods.singleOrNull {
            it.parameterCount == 0 && !it.returnType.isPrimitive && it.returnType != String::class.java
        } ?: return null
        return ScanMatch(cls.name, key.name, payload.name, 140)
    }
}

internal class FeedTemplateLoadMoreRule(
    private val hasLoadMoreEvidence: (Method, ScanLogger?) -> Boolean = { method, logger ->
        ScanDexQueries.method(method, logger)?.let { dex ->
            "加载更多数据异常，请检查调用逻辑！" in dex.usingStrings &&
                dex.invokes.any { it.methodName == "notifyItemRangeInserted" } &&
                dex.calls(StableTiebaHookPoints.BD_RECYCLER_VIEW_CLASS, "getHeaderViewsCount")
        } == true
    },
) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf(StableTiebaHookPoints.FEED_TEMPLATE_ADAPTER_CLASS)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val selected = uniqueSemanticCandidate("Feed.LoadMore", cls.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterCount == 1 &&
                List::class.java.isAssignableFrom(it.parameterTypes[0]) && hasLoadMoreEvidence(it, logger)
        }, logger) ?: return null
        return ScanMatch(cls.name, selected.name, "", 140)
    }
}

internal class FeedCardBindRule(
    private val hasBindEvidence: (Method, ScanLogger?) -> Boolean = { method, logger ->
        ScanDexQueries.method(method, logger)?.let { dex ->
            val calledNames = dex.invokes.map { it.methodName }.toSet()
            calledNames.containsAll(listOf("getTag", "setTag", "setOnLongClickListener")) &&
                dex.instanceFields(method.declaringClass.name, method.parameterTypes.single().name, FieldUsingType.Write).size == 1 &&
                ScanDexQueries.methods(method.parameterTypes.single().name, logger).any {
                    it.methodName == "toString" && it.usingStrings.any { value -> value.startsWith("CardData(") }
                }
        } == true
    },
) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tieba.feed.card.FeedCardView")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val selected = uniqueSemanticCandidate("Feed.Bind", cls.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE && method.parameterCount == 1 &&
                !method.parameterTypes.single().isPrimitive && isCardDataLike(method.parameterTypes.single()) &&
                hasBindEvidence(method, logger)
        }, logger) ?: return null
        return ScanMatch(cls.name, selected.name, "", 140)
    }

    private fun isCardDataLike(cls: Class<*>): Boolean {
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        return fields.any { List::class.java.isAssignableFrom(it.type) } &&
            fields.count { it.type == String::class.java } >= 2 &&
            fields.count { it.type == Boolean::class.javaPrimitiveType } >= 2 &&
            fields.any { Map::class.java.isAssignableFrom(it.type) }
    }
}

internal class FeedHeadParamsFieldRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tieba.feed.component.uistate.CardHeadUiState")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tieba.feed.component.uistate.CardHeadUiState") return null
        val methods = ScanDexQueries.methods(cls.name, logger)
        if (methods.none { "feed_head" in it.usingStrings && it.returnTypeName == "java.lang.String" }) return null
        val getter = uniqueSemanticCandidate("Feed.HeadParamsGetter", methods.filter {
            !Modifier.isStatic(it.modifiers) && it.paramCount == 0 && it.returnTypeName == "java.util.Map"
        }, logger) ?: return null
        val field = uniqueSemanticCandidate("Feed.HeadParamsField", getter.instanceFields(cls.name, "java.util.Map", FieldUsingType.Read), logger)
            ?.getFieldInstance(cl) ?: return null
        if (Modifier.isStatic(field.modifiers) || !Map::class.java.isAssignableFrom(field.type)) return null
        return ScanMatch(cls.name, "", field.name, 140)
    }
}
