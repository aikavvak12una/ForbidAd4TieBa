package com.forbidad4tieba.hook.symbol.scan

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.AbsListView
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal class PbCommentScrollRule(private val pbFragmentClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesUsingStrings(logger, "pb", "scroll")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val fragment = ScanReflection.safeFindClass(pbFragmentClassName, cl) ?: return null
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val owner = cls.declaredFields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == fragment } ?: return null
        val methods = cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) }
        val state = uniqueSemanticCandidate("PbScroll.State", methods.filter {
            it.returnType == Void.TYPE && it.parameterCount == 2 && it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                ScanDexQueries.method(it, logger)?.usingStrings?.containsAll(listOf("pb", "scroll")) == true
        }, logger) ?: return null
        val scroll = uniqueSemanticCandidate("PbScroll.Position", methods.filter {
            it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(
                state.parameterTypes[0], Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            ))
        }, logger) ?: return null
        val listView = ScanReflection.safeFindClass(StableTiebaHookPoints.BD_LIST_VIEW_CLASS, cl) ?: return null
        val bottomFields = fragment.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type.isInterface &&
                listView.declaredFields.any { !Modifier.isStatic(it.modifiers) && it.type == field.type } &&
                field.type.declaredMethods.count { it.name == "onScrollToBottom" && it.parameterCount == 0 && it.returnType == Void.TYPE } == 1
        }
        val bottom = uniqueSemanticCandidate("PbScroll.BottomListener", bottomFields, logger)
        val bottomMethod = bottom?.type?.declaredMethods?.singleOrNull {
            it.name == "onScrollToBottom" && it.parameterCount == 0 && it.returnType == Void.TYPE
        }
        return ScanMatch(cls.name, scroll.name, listOf(owner.name, bottom?.name.orEmpty(), bottomMethod?.name.orEmpty()).joinToString(","), 140)
    }
}

internal class BdListViewBottomScrollRule(private val listViewClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesWithFieldType(listViewClassName, logger)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val listView = ScanReflection.safeFindClass(listViewClassName, cl) ?: return null
        if (!AbsListView.OnScrollListener::class.java.isAssignableFrom(cls) || cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!ownerRegistersScrollListener(listViewClassName, cls.name, AbsListView.OnScrollListener::class.java.name, "setOnScrollListener", logger)) return null
        val owner = cls.declaredFields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == listView } ?: return null
        val scroll = cls.declaredMethods.singleOrNull {
                it.name == "onScroll" && it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(
                    AbsListView::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                ))
        } ?: return null
        return ScanMatch(cls.name, scroll.name, owner.name, 140)
    }
}

internal class BdRecyclerViewBottomScrollRule(private val recyclerViewClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesWithFieldType(recyclerViewClassName, logger)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val ownerClass = ScanReflection.safeFindClass(recyclerViewClassName, cl) ?: return null
        val recycler = ScanReflection.safeFindClass(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, cl) ?: return null
        val listener = ScanReflection.safeFindClass("${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}\$OnScrollListener", cl) ?: return null
        if (!listener.isAssignableFrom(cls) || cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!ownerRegistersScrollListener(recyclerViewClassName, cls.name, listener.name, "addOnScrollListener", logger)) return null
        val owner = cls.declaredFields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == ownerClass } ?: return null
        val scroll = cls.declaredMethods.singleOrNull {
                it.name == "onScrolled" && it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(recycler, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        } ?: return null
        return ScanMatch(cls.name, scroll.name, owner.name, 140)
    }
}

private fun ownerRegistersScrollListener(
    owner: String,
    listener: String,
    listenerApi: String,
    registrationMethod: String,
    logger: ScanLogger?,
): Boolean = ScanDexQueries.methods(owner, logger).any { setup ->
    setup.invokes.any { it.isConstructor && it.declaredClassName == listener && it.paramTypeNames == listOf(owner) } &&
        setup.invokes.any { it.methodName == registrationMethod && it.paramTypeNames == listOf(listenerApi) }
}

internal class PbGestureScaleRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesWithFieldType(ScaleGestureDetector::class.java.name, logger)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (cls.declaredFields.none { it.type == ScaleGestureDetector::class.java }) return null
        val methods = cls.declaredMethods.filter { !Modifier.isStatic(it.modifiers) }
        val dispatch = uniqueSemanticCandidate("PbScale.Dispatch", methods.filter {
            it.returnType == Boolean::class.javaPrimitiveType && it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java)) &&
                ScanDexQueries.method(it, logger)?.calls(ScaleGestureDetector::class.java.name, "onTouchEvent") == true
        }, logger) ?: return null
        val setter = uniqueSemanticCandidate("PbScale.ListenerSetter", methods.filter {
            it.returnType == Void.TYPE && it.parameterCount == 1 && booleanCallback(it.parameterTypes[0])
        }, logger) ?: return null
        val listener = uniqueSemanticCandidate("PbScale.Listener", cls.declaredClasses.filter {
            ScaleGestureDetector.SimpleOnScaleGestureListener::class.java.isAssignableFrom(it)
        }, logger) ?: return null
        val onScale = listener.declaredMethods.singleOrNull {
            it.name == "onScale" && it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.contentEquals(arrayOf(ScaleGestureDetector::class.java))
        } ?: return null
        return ScanMatch(cls.name, "${dispatch.name},${setter.name},${onScale.name}", listener.name, 140)
    }

    private fun booleanCallback(type: Class<*>): Boolean = type.isInterface &&
        type.declaredMethods.filter { Modifier.isAbstract(it.modifiers) }.singleOrNull()?.let {
            it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
        } == true
}
