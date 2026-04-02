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
        
        if (intMethods.size < 3) return null
        
        val g1 = intMethods.firstOrNull { it.name != "H1" && it.name != "I1" && it.name != "K1" } ?: intMethods[0]
        val j1 = intMethods.firstOrNull { it != g1 && it.name != "H1" && it.name != "I1" && it.name != "K1" } ?: intMethods[1]

        return ScanMatch(cls.name, "${g1.name},${j1.name}", "", 100)
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

/**
 * 扫描 PersonalizePageView 中的刷新方法（原 x1 / r1，会随版本改名）。
 *
 * 识别特征：
 * 1. 类名固定：com.baidu.tieba.homepage.personalize.PersonalizePageView（非混淆）
 * 2. 方法签名：public void, 无参数, 声明在本类中（非继承）
 * 3. 区分特征：该方法是 PersonalizeFragment 中某个方法调用的唯一 PersonalizePageView 的
 *    public void() 方法。由于无法通过反射检查方法体，我们通过 PersonalizeFragment 的字段
 *    类型关系来间接识别——PersonalizeFragment 持有一个 PersonalizePageView 类型的字段，
 *    并且该 Fragment 中存在调用该刷新方法的桥接方法。
 *
 * 由于纯反射无法可靠区分 34 个 public void() 方法中哪个是刷新方法，
 * 我们使用 PersonalizeFragment 作为交叉验证点：在 Fragment 的 public void() 方法中，
 * 寻找那些可能调用 PersonalizePageView 刷新方法的桥接方法（通过方法数量和命名模式推断）。
 *
 * 退化策略：如果精确匹配失败，回退到对已知方法名列表的暴力搜索。
 */
internal class PersonalizeRefreshRule : ScanRule() {
    companion object {
        private const val PPV_CLASS = "com.baidu.tieba.homepage.personalize.PersonalizePageView"
        private const val FRAGMENT_CLASS = "com.baidu.tieba.homepage.personalize.PersonalizeFragment"
        // 已知的方法名列表（按发现顺序），用于快速匹配
        private val KNOWN_NAMES = listOf("x1", "r1")
    }

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? {
        if (cls.name != PPV_CLASS) return null

        // 优先尝试已知方法名（零开销快速路径）
        for (name in KNOWN_NAMES) {
            val found = cls.declaredMethods.any {
                it.name == name &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.isEmpty() &&
                    java.lang.reflect.Modifier.isPublic(it.modifiers)
            }
            if (found) return ScanMatch(cls.name, name, "", 100)
        }

        // 无已知方法名匹配，通过 PersonalizeFragment 交叉验证
        val fragmentClass = try {
            Class.forName(FRAGMENT_CLASS, false, cl)
        } catch (_: Throwable) {
            null
        }

        if (fragmentClass != null) {
            // PersonalizeFragment 持有 PersonalizePageView 的字段
            val hasViewField = fragmentClass.declaredFields.any {
                it.type == cls
            }
            if (hasViewField) {
                // Fragment 中的 public void() 桥接方法通常只有少量几个。
                // 刷新桥接方法通常是名字较短的那个（如 Y, W 等）
                val fragmentBridgeMethods = fragmentClass.declaredMethods.filter {
                    java.lang.reflect.Modifier.isPublic(it.modifiers) &&
                        it.returnType == Void.TYPE &&
                        it.parameterTypes.isEmpty() &&
                        it.name.length <= 2  // 短混淆名
                }

                // 在 PPV 中查找与 Fragment 桥接方法对应的方法
                // 桥接方法和目标方法通常具有相近的命名模式
                val ppvCandidates = cls.declaredMethods.filter {
                    java.lang.reflect.Modifier.isPublic(it.modifiers) &&
                        it.returnType == Void.TYPE &&
                        it.parameterTypes.isEmpty() &&
                        it.name.length == 2 && // 典型混淆名长度 (如 x1, r1)
                        it.name[1].isDigit() && it.name[1] == '1' // 一般以数字1结尾
                }

                if (ppvCandidates.size == 1) {
                    return ScanMatch(cls.name, ppvCandidates[0].name, "", 90)
                }
                // 如果有多个候选，选字母序较大的（混淆名倾向分配在方法列表后部）
                if (ppvCandidates.isNotEmpty()) {
                    val best = ppvCandidates.maxByOrNull { it.name }!!
                    return ScanMatch(cls.name, best.name, "", 70)
                }
            }
        }

        return null
    }
}

