package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.scan.FeedTemplateKeyRule
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Restore members once from Feed's cached names and their semantic owners. */
internal object FeedTargetRestoration {
    fun templateKey(cl: ClassLoader, name: String?): Method? {
        if (name.isNullOrBlank()) return null
        val rule = FeedTemplateKeyRule()
        val candidates = rule.candidateNames(emptyList(), cl, null).mapNotNull { className ->
            val type = ScanReflection.safeFindClass(className, cl) ?: return@mapNotNull null
            val match = rule.match(type, cl, null) ?: return@mapNotNull null
            if (match.methodName != name) return@mapNotNull null
            type.getDeclaredMethod(name)
        }
        return candidates.singleOrNull()?.apply { isAccessible = true }
    }

    fun payload(key: Method, name: String?): Method? {
        if (name.isNullOrBlank()) return null
        return runCatching { key.declaringClass.getDeclaredMethod(name) }.getOrNull()
            ?.takeIf { !Modifier.isStatic(it.modifiers) && !it.returnType.isPrimitive && it.returnType != String::class.java }
            ?.apply { isAccessible = true }
    }

    fun cardList(cl: ClassLoader, bindSpec: String?, name: String?): Field? {
        if (bindSpec.isNullOrBlank() || name.isNullOrBlank()) return null
        val view = ScanReflection.safeFindClass(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, cl) ?: return null
        val bind = RestoredMembers.resolveMethodByCachedSpec(view, bindSpec, bindSpec.substringBefore('|'))
            ?.takeIf { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE } ?: return null
        val data = bind.parameterTypes.singleOrNull() ?: return null
        return instanceField(data, name, List::class.java)
    }

    fun headParams(cl: ClassLoader, name: String?): Field? {
        if (name.isNullOrBlank()) return null
        val head = ScanReflection.safeFindClass("com.baidu.tieba.feed.component.uistate.CardHeadUiState", cl) ?: return null
        return instanceField(head, name, Map::class.java)
    }

    fun recommendData(cl: ClassLoader, methodName: String?, fieldName: String?): Pair<Method, Field>? {
        if (methodName.isNullOrBlank() || fieldName.isNullOrBlank()) return null
        val view = ScanReflection.safeFindClass("com.baidu.tieba.feed.component.RecommendCardView", cl) ?: return null
        val candidates = view.declaredMethods.asSequence()
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.isBridge && it.returnType == Void.TYPE }
            .mapNotNull { it.parameterTypes.singleOrNull() }
            .filter { !it.isPrimitive && it.name.startsWith("com.baidu.tieba.") }
            .distinct()
            .mapNotNull { state ->
                val method = ScanReflection.collectInstanceMethods(state).singleOrNull {
                    it.name == methodName && !it.isSynthetic && !it.isBridge && it.parameterCount == 0 &&
                        !it.returnType.isPrimitive && it.returnType != String::class.java
                } ?: return@mapNotNull null
                if (ScanReflection.collectInstanceFields(state).none { it.type == method.returnType }) return@mapNotNull null
                val field = instanceField(method.returnType, fieldName, List::class.java) ?: return@mapNotNull null
                method.apply { isAccessible = true } to field
            }.distinct().toList()
        return candidates.singleOrNull()
    }

    internal fun instanceField(owner: Class<*>, name: String, type: Class<*>): Field? =
        ScanReflection.collectInstanceFields(owner).singleOrNull {
            it.name == name && type.isAssignableFrom(it.type)
        }?.apply { isAccessible = true }
}
