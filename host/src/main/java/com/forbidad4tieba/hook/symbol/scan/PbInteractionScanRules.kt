package com.forbidad4tieba.hook.symbol.scan

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

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
