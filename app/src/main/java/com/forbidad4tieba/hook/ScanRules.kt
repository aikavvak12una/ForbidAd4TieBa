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

internal class FeedTemplateLoadMoreRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        val superCls = cls.superclass ?: return null
        if (superCls.name != "com.baidu.tieba.feed.list.TemplateAdapter") return null

        val methods = cls.declaredMethods
        val loadMoreMethod = methods.firstOrNull { m ->
            m.returnType == Void.TYPE && 
            m.parameterTypes.size == 1 && 
            List::class.java.isAssignableFrom(m.parameterTypes[0])
        } ?: return null

        return ScanMatch(cls.name, loadMoreMethod.name, "", 100)
    }
}

internal class SplashAdHelperRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        val fields = cls.declaredFields
        val methods = cls.declaredMethods
        
        // Characteristic: A static final Lazy field and static boolean methods
        val hasLazy = fields.any { it.type.name == "kotlin.Lazy" }
        if (!hasLazy) return null

        val boolMethods = methods.filter { it.returnType == Boolean::class.javaPrimitiveType && it.parameterTypes.isEmpty() }
        if (boolMethods.size < 2) return null
        
        // SplashForbidAdHelperKt is usually named uniquely, but if obfuscated, these characteristics hold.
        val aMethod = boolMethods.minByOrNull { it.name.length } ?: return null
        
        return ScanMatch(cls.name, aMethod.name, "", 100)
    }
}

internal class CloseAdDataRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        val superCls = cls.superclass ?: return null
        if (superCls.name != "com.baidu.adp.lib.OrmObject.toolsystem.orm.object.OrmObject") return null

        val methods = cls.declaredMethods
        val intMethods = methods.filter { it.returnType == Int::class.javaPrimitiveType && it.parameterTypes.isEmpty() }
        
        if (intMethods.size < 2) return null

        // 取前两个 int 返回方法（CloseAdData 的 int 方法都是广告控制标志位，全部返回 1 即可）
        return ScanMatch(cls.name, "${intMethods[0].name},${intMethods[1].name}", "", 100)
    }
}

internal class Nd7Rule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        val superCls = cls.superclass ?: return null
        if (superCls.name != "android.text.style.ImageSpan") return null
        
        val methods = cls.declaredMethods
        val intMethods = methods.filter { it.returnType == Int::class.javaPrimitiveType && it.parameterTypes.isEmpty() }
        val boolMethods = methods.filter { it.returnType == Boolean::class.javaPrimitiveType && it.parameterTypes.isEmpty() }
        
        // In newer versions of Tieba, nd7's i0/l methods might be removed or signature changed.
        // We fallback to just returning the class name if it matches the ImageSpan structure.
        if (intMethods.isEmpty() || boolMethods.isEmpty()) {
            return ScanMatch(cls.name, "getSize,draw", "", 50)
        }
        
        val lMethod = intMethods[0]
        val i0Method = boolMethods[0]
        
        return ScanMatch(cls.name, "${i0Method.name},${lMethod.name}", "", 100)
    }
}

internal class ZgaRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        if (!cls.isInterface) return null
        val methods = cls.declaredMethods
        
        val stringMethods = methods.filter { 
            it.returnType == String::class.java && 
            it.parameterTypes.size > 2 // Typical zga methods take multiple params including String, context, etc.
        }
        
        if (stringMethods.isEmpty()) return null
        
        val methodNames = stringMethods.map { it.name }.joinToString(",")
        return ScanMatch(cls.name, methodNames, "", 100)
    }
}
