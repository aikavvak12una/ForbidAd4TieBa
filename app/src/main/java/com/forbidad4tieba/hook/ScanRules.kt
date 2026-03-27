package com.forbidad4tieba.hook

import android.content.Context
import android.view.View

internal data class ScanMatch(
    val className: String,
    val methodName: String,
    val fieldName: String,
    val score: Int,
)

internal abstract class ScanRule {
    abstract fun match(cls: Class<*>, cl: ClassLoader): ScanMatch?
}

internal class SettingsLevel1Rule(private val navClass: Class<*>) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        val method = cls.declaredMethods.firstOrNull { m ->
            if (m.returnType != Void.TYPE) return@firstOrNull false
            val p = m.parameterTypes
            p.size == 2 &&
                Context::class.java.isAssignableFrom(p[0]) &&
                navClass.isAssignableFrom(p[1])
        } ?: return null

        val containerField = cls.declaredFields.firstOrNull {
            it.type.name == "android.widget.RelativeLayout"
        } ?: cls.declaredFields.firstOrNull {
            View::class.java.isAssignableFrom(it.type)
        } ?: return null

        var score = 100
        score -= cls.declaredMethods.size / 5
        score -= cls.simpleName.length
        return ScanMatch(cls.name, method.name, containerField.name, score)
    }
}

internal class HomeTabsLevel1Rule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        val fields = cls.declaredFields
        val listField = fields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return null
        if (!fields.any { it.type == Int::class.javaPrimitiveType }) return null

        val methods = cls.declaredMethods
        val hasFactory = methods.any { method ->
            val p = method.parameterTypes
            p.size == 4 &&
                p[0] == Int::class.javaPrimitiveType &&
                p[1] == String::class.java &&
                p[2] == String::class.java &&
                p[3] == Boolean::class.javaPrimitiveType
        }
        if (!hasFactory) return null

        val hasListGetter = methods.any {
            it.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(it.returnType)
        }
        if (!hasListGetter) return null

        val noArgVoid = methods.filter {
            it.parameterTypes.isEmpty() && it.returnType == Void.TYPE
        }
        if (noArgVoid.isEmpty()) return null

        val rebuildMethod = noArgVoid.minByOrNull { it.name.length } ?: return null

        var score = 100
        score -= methods.size / 4
        score -= cls.simpleName.length

        return ScanMatch(cls.name, rebuildMethod.name, listField.name, score)
    }
}
