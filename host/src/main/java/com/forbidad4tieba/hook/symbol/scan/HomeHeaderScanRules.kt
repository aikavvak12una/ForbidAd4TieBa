package com.forbidad4tieba.hook.symbol.scan

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class SearchBoxSetHintRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf(StableTiebaHookPoints.TB_SEARCH_BOX_VIEW_CLASS)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (!ViewGroup::class.java.isAssignableFrom(cls)) return null
        val method = cls.declaredMethods.singleOrNull {
            it.name == "setHintTextDataList" && !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                it.parameterCount == 2 && List::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        } ?: return null
        return ScanMatch(cls.name, method.name, "", 140)
    }
}

internal class HomeSearchBoxOwnerRule(private val ownerClassName: String, private val searchBoxClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) = listOf(ownerClassName)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != ownerClassName) return null
        val field = cls.declaredFields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type.name == searchBoxClassName } ?: return null
        val methods = cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) }
        val getter = uniqueSemanticCandidate("HomeSearch.Getter", methods.filter {
            it.parameterCount == 0 && it.returnType == field.type &&
                ScanDexQueries.method(it, logger)?.usingFields?.any { use ->
                    use.field.declaredClassName == cls.name && use.field.name == field.name
                } == true
        }, logger) ?: return null
        val init = uniqueSemanticCandidate("HomeSearch.Init", methods.filter {
            it.returnType == Void.TYPE && it.parameterTypes.map { type -> type.name } == listOf(
                "com.baidu.adp.widget.ListView.BdTypeRecyclerView", "androidx.coordinatorlayout.widget.CoordinatorLayout",
            ) && ScanDexQueries.method(it, logger)?.calls(searchBoxClassName, "setSearchOnClickListener") == true
        }, logger) ?: return null
        return ScanMatch(cls.name, init.name, getter.name, 140)
    }
}

internal class HomeTopBarRightSlotRule(private val targetClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) = listOf(targetClassName)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != targetClassName || !ViewGroup::class.java.isAssignableFrom(cls)) return null
        val methods = cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
        fun getter(name: String, type: Class<*>) = methods.singleOrNull {
            it.name == name && it.parameterCount == 0 && type.isAssignableFrom(it.returnType)
        }
        if (getter("getSearchIconView", ImageView::class.java) == null || getter("getGameIconView", View::class.java) == null ||
            getter("getRedDotView", View::class.java) == null || getter("getTopBarTip", View::class.java) == null) return null
        val viewApi = ScanReflection.collectInstanceMethods(ViewGroup::class.java).filter {
            Modifier.isPublic(it.modifiers) || Modifier.isProtected(it.modifiers)
        }
        // Observe all state entries, excluding actual SDK overrides by signature, regardless of spelling.
        val stateMethods = methods.filter {
            it.returnType == Void.TYPE && it.parameterCount == 0 && viewApi.none { api ->
                api.name == it.name && api.parameterTypes.contentEquals(it.parameterTypes)
            }
        }.map(Method::getName).distinct().sorted()
        if (stateMethods.isEmpty()) return null
        return ScanMatch(cls.name, stateMethods.joinToString(","), "", 140)
    }
}
