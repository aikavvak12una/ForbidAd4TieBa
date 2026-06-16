package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ImageView
import android.widget.TextView
import android.widget.TextSwitcher
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.io.IOException
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal data class ScanMatch(
    val className: String,
    val methodName: String,
    val fieldName: String,
    val score: Int,
)

internal abstract class ScanRule {
    open val minScore: Int = 80
    open val minScoreGap: Int = 0
    open fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = null
    open fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? = match(cls, cl)
}

private data class ScanRuleClassShape(
    val methods: Array<Method>,
    val fields: Array<Field>,
    val constructors: Array<Constructor<*>>,
)

private fun scanRuleClassShape(tag: String, cls: Class<*>, logger: ScanLogger?): ScanRuleClassShape? {
    return scanSubStep("ScanRules.$tag.${cls.name}.ClassShape", logger, null) {
        ScanRuleClassShape(
            methods = cls.declaredMethods,
            fields = cls.declaredFields,
            constructors = cls.declaredConstructors,
        )
    }
}

private fun scanRuleMethods(tag: String, cls: Class<*>, logger: ScanLogger?): Array<Method>? {
    return scanSubStep("ScanRules.$tag.${cls.name}.Methods", logger, null) {
        cls.declaredMethods
    }
}

private fun scanRuleFields(tag: String, cls: Class<*>, logger: ScanLogger?): Array<Field>? {
    return scanSubStep("ScanRules.$tag.${cls.name}.Fields", logger, null) {
        cls.declaredFields
    }
}

private fun scanRuleNestedClasses(tag: String, cls: Class<*>, logger: ScanLogger?): Array<Class<*>>? {
    return scanSubStep("ScanRules.$tag.${cls.name}.NestedClasses", logger, null) {
        cls.declaredClasses
    }
}

private fun scanRuleClassForName(tag: String, className: String, cl: ClassLoader, logger: ScanLogger?): Class<*>? {
    return scanSubStep("ScanRules.$tag.$className.ClassForName", logger, null) {
        Class.forName(className, false, cl)
    }
}

internal fun kotlinMetadataStrings(cls: Class<*>, logger: ScanLogger? = null): List<String> {
    val metadata = cls.getAnnotation(kotlin.Metadata::class.java) ?: return emptyList()
    val out = ArrayList<String>(8)
    for (methodName in arrayOf("d1", "d2")) {
        val values = scanSubStep("ScanRules.${cls.name}.Metadata.$methodName", logger, null) {
            kotlin.Metadata::class.java.getMethod(methodName).invoke(metadata) as? Array<*>
        } ?: continue
        for (value in values) {
            if (value is String && value.isNotEmpty()) out.add(value)
        }
    }
    return out
}

internal class SettingsLevel1Rule(private val navClass: Class<*>) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val shape = scanRuleClassShape("SettingsLevel1Rule", cls, logger) ?: return null
        val methods = shape.methods
        val fields = shape.fields

        // Pattern A: void xxx(Context, NavigationBar).
        val methodA = methods.firstOrNull { m ->
            if (m.returnType != Void.TYPE) return@firstOrNull false
            val p = m.parameterTypes
            p.size == 2 &&
                Context::class.java.isAssignableFrom(p[0]) &&
                navClass.isAssignableFrom(p[1])
        }

        // 模式 B：void xxx(View)，我的页面写法，NavigationBar 在内部通过 findViewById 获取。
        // 类不能是 abstract，需要有 RelativeLayout 字段、ImageView 字段和 Context 构造函数。
        // Method cannot be onClick.
        val methodB = if (methodA == null) {
            if (Modifier.isAbstract(cls.modifiers)) null
            else {
                val hasNavBarField = fields.any { navClass.isAssignableFrom(it.type) }
                val hasRelativeLayoutField = fields.any { it.type.name == "android.widget.RelativeLayout" }
                val hasImageViewField = fields.any { it.type == ImageView::class.java }
                val hasContextConstructor = shape.constructors.any { ctor ->
                    ctor.parameterTypes.size == 1 &&
                        Context::class.java.isAssignableFrom(ctor.parameterTypes[0])
                }
                if (!hasRelativeLayoutField || !hasImageViewField || !hasContextConstructor) null
                else methods.firstOrNull { m ->
                    if (m.returnType != Void.TYPE) return@firstOrNull false
                    if (m.name == "onClick") return@firstOrNull false
                    val p = m.parameterTypes
                    p.size == 1 && View::class.java.isAssignableFrom(p[0]) && !hasNavBarField
                }
            }
        } else null

        val method = methodA ?: methodB ?: return null

        val containerField = fields.firstOrNull {
            it.type.name == "android.widget.RelativeLayout"
        } ?: fields.firstOrNull {
            View::class.java.isAssignableFrom(it.type)
        } ?: return null

        var score = 100
        score -= methods.size / 5
        score -= cls.simpleName.length
        // Pattern B is the primary settings entry; score it higher.
        if (methodB != null) score += 20
        return ScanMatch(cls.name, method.name, containerField.name, score)
    }
}

internal class HomeTabsLevel1Rule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val shape = scanRuleClassShape("HomeTabsLevel1Rule", cls, logger) ?: return null
        val fields = shape.fields
        val listField = fields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return null
        if (!fields.any { it.type == Int::class.javaPrimitiveType }) return null

        val methods = shape.methods
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

internal class EnterForumWebControllerRule(
    private val tbWebViewClass: Class<*>,
) : ScanRule() {
    override val minScoreGap: Int = 20

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!cls.name.startsWith("com.baidu.tieba.")) return null

        val shape = scanRuleClassShape("EnterForumWebControllerRule", cls, logger) ?: return null
        val fields = shape.fields
        val constructors = shape.constructors
        val methods = shape.methods.filter { !Modifier.isStatic(it.modifiers) }
        val hasWebViewGetter = methods.any { method ->
            method.parameterTypes.isEmpty() &&
                tbWebViewClass.isAssignableFrom(method.returnType)
        }
        if (!hasWebViewGetter) return null

        val stringVoidMethods = methods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        }
        if (stringVoidMethods.isEmpty()) return null

        val targetMethod = stringVoidMethods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name.length <= 2) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        ) ?: return null

        val hasUniqueIdField = fields.any { it.type.name == "com.baidu.adp.BdUniqueId" }
        val hasUniqueIdConstructor = constructors.any { constructor ->
            constructor.parameterTypes.any { it.name == "com.baidu.adp.BdUniqueId" }
        }
        val hasViewField = fields.any { View::class.java.isAssignableFrom(it.type) }

        var score = 90
        if (hasUniqueIdField) score += 36
        if (hasUniqueIdConstructor) score += 30
        if (hasViewField) score += 8
        if (cls.simpleName.length <= 4) score += 6
        score -= methods.size / 6
        score -= stringVoidMethods.size
        score -= cls.simpleName.length
        return ScanMatch(
            className = cls.name,
            methodName = targetMethod.name,
            fieldName = "",
            score = score,
        )
    }
}

internal class ForumBottomSheetInitScrollRule(
    private val bottomSheetClassName: String,
) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != bottomSheetClassName) return null
        val methods = scanRuleMethods("ForumBottomSheetInitScrollRule", cls, logger) ?: return null
        val targetMethod = methods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[2].name == "kotlin.jvm.functions.Function0"
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Method>(
                    { if (it.name == "c0") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            ) ?: return null

        var score = 100
        if (targetMethod.name == "c0") score += 20
        score -= methods.size / 4
        return ScanMatch(cls.name, targetMethod.name, "", score)
    }
}

internal class AutoRefreshTriggerRule(
    private val preferredMethodNames: List<String> = listOf("w1"),
) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tieba.homepage.personalize.PersonalizePageView") return null

        val methods = scanRuleMethods("AutoRefreshTriggerRule", cls, logger)?.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty() &&
                method.name.length <= 3 &&
                method.name.endsWith("1") &&
                method.name.firstOrNull()?.isLowerCase() == true
        } ?: return null
        if (methods.isEmpty()) return null

        val targetMethod = preferredMethodNames
            .asSequence()
            .mapNotNull { preferred -> methods.singleOrNull { it.name == preferred } }
            .firstOrNull()
            ?: return null

        var score = 80
        val preferredIdx = preferredMethodNames.indexOf(targetMethod.name)
        if (preferredIdx >= 0) {
            score += (20 - preferredIdx * 3)
        }
        if (targetMethod.name.length == 2) score += 6
        score -= methods.size / 2
        return ScanMatch(
            className = cls.name,
            methodName = targetMethod.name,
            fieldName = "",
            score = score,
        )
    }
}

internal class AutoLoadMoreConfigRule(
    private val parserClassName: String,
    private val preferredMethodName: String = "a",
) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null

        val isParserCompanion = cls.name == "${parserClassName}\$Companion" ||
            cls.name == "${parserClassName}\$a" ||
            cls.enclosingClass?.name == parserClassName
        if (!isParserCompanion) return null

        val candidates = scanRuleMethods("AutoLoadMoreConfigRule", cls, logger)?.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Int::class.javaPrimitiveType
        } ?: return null
        if (candidates.isEmpty()) return null

        val targetMethod = candidates.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name == preferredMethodName) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        ) ?: return null

        var score = 90
        if (targetMethod.name == preferredMethodName) score += 15
        if (cls.name.endsWith("\$Companion") || cls.name.endsWith("\$a")) score += 10
        score -= candidates.size / 2
        score -= cls.simpleName.length
        return ScanMatch(
            className = cls.name,
            methodName = targetMethod.name,
            fieldName = "",
            score = score,
        )
    }
}

internal class PbCommentScrollRule(
    private val pbFragmentClassName: String,
) : ScanRule() {
    override val minScore: Int = 100
    override val minScoreGap: Int = 10

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (cls.enclosingClass?.name != pbFragmentClassName && !cls.name.startsWith("${pbFragmentClassName}\$")) {
            return null
        }

        val pbFragmentClass = scanRuleClassForName("PbCommentScrollRule", pbFragmentClassName, cl, logger)
            ?: return null

        val fragmentField = scanRuleFields("PbCommentScrollRule", cls, logger)?.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) && field.type == pbFragmentClass
        } ?: return null

        val bottomListenerField = findBottomListenerField(pbFragmentClass, logger)
        val bottomMethod = bottomListenerField?.let { findBottomMethod(it.type, logger) }

        val methods = scanRuleMethods("PbCommentScrollRule", cls, logger)
            ?.filter { !Modifier.isStatic(it.modifiers) }
            ?: return null
        val scrollCandidates = methods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }
        if (scrollCandidates.isEmpty()) return null

        val scrollMethod = scrollCandidates.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name == "b") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        ) ?: return null

        val listType = scrollMethod.parameterTypes[0]
        val hasStateCallback = methods.any { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == listType &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType
        }
        if (!hasStateCallback) return null

        var score = 120
        if (scrollMethod.name == "b") score += 15
        if (cls.name.contains("${pbFragmentClassName}\$")) score += 10
        if (Modifier.isFinal(fragmentField.modifiers)) score += 5
        if (bottomListenerField?.type?.name?.startsWith("${StableTiebaHookPoints.BD_LIST_VIEW_CLASS}\$") == true) {
            score += 8
        }
        if (bottomMethod?.name == "onScrollToBottom") score += 8
        score -= scrollCandidates.size * 3

        return ScanMatch(
            className = cls.name,
            methodName = scrollMethod.name,
            fieldName = listOf(
                fragmentField.name,
                bottomListenerField?.name.orEmpty(),
                bottomMethod?.name.orEmpty(),
            ).joinToString(","),
            score = score,
        )
    }

    private fun findBottomListenerField(pbFragmentClass: Class<*>, logger: ScanLogger?): java.lang.reflect.Field? {
        val fields = scanRuleFields("PbCommentScrollRule.BottomListener", pbFragmentClass, logger)?.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name.startsWith("${StableTiebaHookPoints.BD_LIST_VIEW_CLASS}\$") &&
                findBottomMethod(field.type, logger) != null
        } ?: return null
        return fields.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (Modifier.isFinal(it.modifiers)) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    }

    private fun findBottomMethod(type: Class<*>, logger: ScanLogger?): java.lang.reflect.Method? {
        val methods = scanRuleMethods("PbCommentScrollRule.BottomMethod", type, logger)?.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        } ?: return null
        return methods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name == "onScrollToBottom") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    }
}

internal class BdListViewBottomScrollRule(
    private val listViewClassName: String,
) : ScanRule() {
    override val minScore: Int = 100
    override val minScoreGap: Int = 10

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val listViewClass = scanRuleClassForName("BdListViewBottomScrollRule", listViewClassName, cl, logger)
            ?: return null
        val scanTargets = if (cls.name == listViewClassName) {
            scanRuleNestedClasses("BdListViewBottomScrollRule", cls, logger)?.asSequence() ?: return null
        } else {
            sequenceOf(cls)
        }
        val candidates = scanTargets.mapNotNull { target ->
            val targetShape = scanRuleClassShape("BdListViewBottomScrollRule.Target", target, logger)
                ?: return@mapNotNull null
            val ownerField = targetShape.fields.firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) && field.type == listViewClass
            } ?: return@mapNotNull null
            val methods = targetShape.methods.filter { !Modifier.isStatic(it.modifiers) }
            val onScrollCandidates = methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    AbsListView::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Int::class.javaPrimitiveType
            }
            val onScroll = pickScrollMethod(onScrollCandidates, preferredName = "onScroll")
                ?: return@mapNotNull null
            val listType = onScroll.parameterTypes[0]
            val hasStateCallback = methods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == listType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType
            }
            if (!hasStateCallback) return@mapNotNull null

            var score = 120
            if (AbsListView.OnScrollListener::class.java.isAssignableFrom(target)) score += 20
            if (target.name.startsWith("${listViewClassName}\$")) score += 10
            if (onScroll.name == "onScroll") score += 8
            if (ownerField.name.length == 1) score += 4
            score -= methods.size / 2
            ScanMatch(target.name, onScroll.name, ownerField.name, score)
        }
        return candidates.maxByOrNull { it.score }
    }

    private fun pickScrollMethod(methods: List<java.lang.reflect.Method>, preferredName: String): java.lang.reflect.Method? {
        return methods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name == preferredName) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    }
}

internal class BdRecyclerViewBottomScrollRule(
    private val recyclerViewClassName: String,
) : ScanRule() {
    override val minScore: Int = 100
    override val minScoreGap: Int = 10

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val ownerClass = scanRuleClassForName("BdRecyclerViewBottomScrollRule.Owner", recyclerViewClassName, cl, logger)
            ?: return null
        val recyclerViewClass = scanRuleClassForName(
            "BdRecyclerViewBottomScrollRule.RecyclerView",
            StableTiebaHookPoints.RECYCLER_VIEW_CLASS,
            cl,
            logger,
        ) ?: return null
        val recyclerScrollListenerClass =
            scanRuleClassForName(
                "BdRecyclerViewBottomScrollRule.ScrollListener",
                "${StableTiebaHookPoints.RECYCLER_VIEW_CLASS}\$OnScrollListener",
                cl,
                logger,
            ) ?: return null
        val scanTargets = if (cls.name == recyclerViewClassName) {
            scanRuleNestedClasses("BdRecyclerViewBottomScrollRule", cls, logger)?.asSequence() ?: return null
        } else {
            sequenceOf(cls)
        }
        val candidates = scanTargets.mapNotNull { target ->
            val targetShape = scanRuleClassShape("BdRecyclerViewBottomScrollRule.Target", target, logger)
                ?: return@mapNotNull null
            val ownerField = targetShape.fields.firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) && field.type == ownerClass
            } ?: return@mapNotNull null
            val methods = targetShape.methods.filter { !Modifier.isStatic(it.modifiers) }
            val onScrolledCandidates = methods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == recyclerViewClass &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType
            }
            val onScrolled = pickScrollMethod(onScrolledCandidates, preferredName = "onScrolled")
                ?: return@mapNotNull null
            val hasStateCallback = methods.any { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == recyclerViewClass &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType
            }
            if (!hasStateCallback) return@mapNotNull null

            var score = 120
            if (recyclerScrollListenerClass.isAssignableFrom(target)) score += 20
            if (target.name.startsWith("${recyclerViewClassName}\$")) score += 10
            if (onScrolled.name == "onScrolled") score += 8
            if (ownerField.name.length == 1) score += 4
            score -= methods.size / 2
            ScanMatch(target.name, onScrolled.name, ownerField.name, score)
        }
        return candidates.maxByOrNull { it.score }
    }

    private fun pickScrollMethod(methods: List<java.lang.reflect.Method>, preferredName: String): java.lang.reflect.Method? {
        return methods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name == preferredName) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    }
}

internal class MainTabBottomLevel1Rule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val shape = scanRuleClassShape("MainTabBottomLevel1Rule", cls, logger) ?: return null
        val fields = shape.fields.filter { !Modifier.isStatic(it.modifiers) }
        val listField = fields.firstOrNull { List::class.java.isAssignableFrom(it.type) } ?: return null
        val hasContextField = fields.any { Context::class.java.isAssignableFrom(it.type) }
        if (!hasContextField) return null

        val methods = shape.methods.filter { !Modifier.isStatic(it.modifiers) }
        val addMethod = methods.firstOrNull { method ->
            method.returnType == Void.TYPE && method.parameterTypes.size == 1
        } ?: return null
        val getListMethod = methods.firstOrNull { method ->
            method.parameterTypes.isEmpty() && List::class.java.isAssignableFrom(method.returnType)
        } ?: return null

        val delegateClass = addMethod.parameterTypes[0]
        val structureMethod = pickStructureMethod(delegateClass, logger) ?: return null

        val structureClass = structureMethod.returnType
        val structureFields = scanRuleFields("MainTabBottomLevel1Rule.Structure", structureClass, logger)
            ?.filter { !Modifier.isStatic(it.modifiers) }
            ?: return null
        val typeField = pickTypeField(structureFields) ?: return null
        val dynamicIconField = structureFields.firstOrNull { it.type.name.contains("DynamicIconData") }
        val fragmentField = structureFields.firstOrNull {
            isFragmentLikeClass(it.type)
        }

        var score = 120
        if (cls.name == "com.baidu.tbadk.mainTab.MaintabAddResponedData") score += 40
        if (addMethod.name == "addFragment") score += 20
        if (getListMethod.name == "getList") score += 20
        if (structureMethod.name == "getFragmentTabStructure") score += 20
        if (typeField.name == "type") score += 10
        if (dynamicIconField != null) score += 8
        if (fragmentField != null) score += 8
        if (listField.name == "mList") score += 4
        score -= methods.size / 4
        score -= cls.simpleName.length

        val packedMethods = listOf(addMethod.name, getListMethod.name, structureMethod.name).joinToString(",")
        val packedFields = listOf(
            typeField.name,
            dynamicIconField?.name.orEmpty(),
            fragmentField?.name.orEmpty(),
        ).joinToString(",")
        return ScanMatch(cls.name, packedMethods, packedFields, score)
    }

    private fun pickStructureMethod(delegateClass: Class<*>, logger: ScanLogger?): java.lang.reflect.Method? {
        val candidates = scanRuleMethods("MainTabBottomLevel1Rule.Delegate", delegateClass, logger)?.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                !method.returnType.isPrimitive &&
                hasTabStructureLikeFields(method.returnType, logger)
        } ?: return null
        if (candidates.isEmpty()) return null
        return candidates.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (Modifier.isAbstract(it.modifiers)) 1 else 0 },
                { if (it.name.startsWith("get")) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    }

    private fun pickTypeField(fields: List<java.lang.reflect.Field>): java.lang.reflect.Field? {
        val intFields = fields.filter { it.type == Int::class.javaPrimitiveType }
        if (intFields.isEmpty()) return null
        return intFields.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { field ->
                    when {
                        field.name == "type" -> 0
                        field.name.contains("type", ignoreCase = true) -> 1
                        field.name.length <= 2 -> 2
                        else -> 3
                    }
                },
                { field -> if (field.name.contains("Res", ignoreCase = true)) 1 else 0 },
                { field -> if (field.name.contains("Drawable", ignoreCase = true)) 1 else 0 },
                { field -> if (field.name.contains("Icon", ignoreCase = true)) 1 else 0 },
                { it.name.length },
                { it.name },
            ),
        )
    }

    private fun hasTabStructureLikeFields(structureClass: Class<*>, logger: ScanLogger?): Boolean {
        val fields = scanRuleFields("MainTabBottomLevel1Rule.TabStructure", structureClass, logger)
            ?.filter { !Modifier.isStatic(it.modifiers) }
            ?: return false
        val hasTypeLikeInt = fields.any { it.type == Int::class.javaPrimitiveType }
        val hasFragmentField = fields.any {
            isFragmentLikeClass(it.type)
        }
        return hasTypeLikeInt && hasFragmentField
    }

    private fun isFragmentLikeClass(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current.name == "androidx.fragment.app.Fragment") return true
            current = current.superclass
        }
        return false
    }
}

internal class FeedTemplateKeyRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (!cls.isInterface) return null
        if (!isTemplateDataInterface(cls, cl, logger)) return null

        val methods = scanRuleMethods("FeedTemplateKeyRule", cls, logger) ?: return null
        if (methods.size < 2 || methods.size > 6) return null

        val stringMethods = methods.filter {
            it.parameterTypes.isEmpty() && it.returnType == String::class.java
        }
        if (stringMethods.size != 1) return null

        val payloadMethods = methods.filter {
            it.parameterTypes.isEmpty() &&
                !it.returnType.isPrimitive &&
                it.returnType != String::class.java
        }
        if (payloadMethods.size != 1) return null

        val keyMethod = stringMethods.minByOrNull { it.name.length } ?: return null
        val payloadMethod = payloadMethods.singleOrNull() ?: return null
        if (keyMethod.name.length > 3) return null
        if (payloadMethod.name.length > 3) return null
        var score = 130
        if (payloadMethod.name == "d") score += 8
        score -= methods.size * 2
        score -= cls.simpleName.length
        return ScanMatch(cls.name, keyMethod.name, payloadMethod.name, score)
    }

    private fun isTemplateDataInterface(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): Boolean {
        val vhClass = scanRuleClassForName(
            "FeedTemplateKeyRule.TemplateVH",
            "com.baidu.tieba.feed.list.TemplateAdapter\$TemplateVH",
            cl,
            logger,
        ) ?: return false

        val vhShape = scanRuleClassShape("FeedTemplateKeyRule.TemplateVH", vhClass, logger) ?: return false
        val hasField = vhShape.fields.any { it.type == cls }
        val hasBindMethod = vhShape.methods.any { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.any { it == cls }
        }
        return hasField && hasBindMethod
    }
}

internal class FeedTemplateLoadMoreRule : ScanRule() {
    override val minScore: Int = 105
    override val minScoreGap: Int = 8

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val superCls = cls.superclass ?: return null
        if (cls.name != "com.baidu.tieba.feed.list.FeedTemplateAdapter" &&
            superCls.name != "com.baidu.tieba.feed.list.TemplateAdapter") return null

        val shape = scanRuleClassShape("FeedTemplateLoadMoreRule", cls, logger) ?: return null
        val methods = shape.methods
        val loadMoreCandidates = methods.filter { m ->
            m.returnType == Void.TYPE &&
                m.parameterTypes.size == 1 &&
                List::class.java.isAssignableFrom(m.parameterTypes[0])
        }

        if (loadMoreCandidates.isEmpty()) return null

        val metadata = kotlinMetadataStrings(cls, logger)
        val hasLoadMoreMetadata = metadata.any { it.contains("loadMoreRefreshList") }
        val hasRefreshMetadata = metadata.any { it.contains("refreshList") }
        val hasRecyclerViewField = shape.fields.any {
            it.type.name == "androidx.recyclerview.widget.RecyclerView"
        }
        val hasPreloadInterface = cls.interfaces.any {
            it.name.contains("IFeedImagePreload") || it.name.endsWith(".ak9")
        }

        val loadMoreMethod = loadMoreCandidates.firstOrNull { it.name == "J" }
            ?: loadMoreCandidates.firstOrNull { it.name == "L" && hasFeedTemplateAdapterRefreshShape(methods) }
            ?: loadMoreCandidates.singleOrNull()
            ?: return null

        var score = 100
        if (cls.name == "com.baidu.tieba.feed.list.FeedTemplateAdapter") score += 30
        if (superCls.name == "com.baidu.tieba.feed.list.TemplateAdapter") score += 20
        if (loadMoreMethod.name == "J") score += 20
        if (hasLoadMoreMetadata) score += 30
        if (hasRefreshMetadata) score += 8
        if (hasRecyclerViewField) score += 8
        if (hasPreloadInterface) score += 6
        if (loadMoreCandidates.size == 1) score += 10
        score -= methods.size / 4
        score -= cls.simpleName.length / 2
        return ScanMatch(cls.name, loadMoreMethod.name, "", score)
    }

    private fun hasFeedTemplateAdapterRefreshShape(methods: Array<java.lang.reflect.Method>): Boolean {
        val hasListBoolean = methods.any { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        val oneArgListCount = methods.count { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                List::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        return hasListBoolean && oneArgListCount >= 2
    }
}

internal class SplashAdHelperRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val shape = scanRuleClassShape("SplashAdHelperRule", cls, logger) ?: return null
        val fields = shape.fields
        val methods = shape.methods

        // Top-level Kotlin helper: static Lazy field plus static boolean methods.
        val hasLazy = fields.any {
            Modifier.isStatic(it.modifiers) && it.type.name == "kotlin.Lazy"
        }
        if (!hasLazy) return null

        val boolMethods = methods.filter {
            Modifier.isStatic(it.modifiers) &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.isEmpty()
        }
        if (boolMethods.size < 2) return null

        // Pick the stable short method name when present.
        val aMethod = boolMethods.minWithOrNull(
            compareBy<java.lang.reflect.Method>(
                { if (it.name == "a") 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        ) ?: return null

        var score = 110
        if (cls.name.endsWith("SplashForbidAdHelperKt")) score += 30
        if (boolMethods.size == 2) score += 8
        score -= cls.simpleName.length / 2
        return ScanMatch(cls.name, aMethod.name, "", score)
    }
}

internal class CloseAdDataRule : ScanRule() {
    override val minScore: Int = 120

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tbadk.data.CloseAdData") return null
        val superCls = cls.superclass ?: return null
        if (superCls.name != "com.baidu.adp.lib.OrmObject.toolsystem.orm.object.OrmObject") return null

        val shape = scanRuleClassShape("CloseAdDataRule", cls, logger) ?: return null
        val fields = shape.fields.filter { !Modifier.isStatic(it.modifiers) }
        val intFields = fields.filter { it.type == Int::class.javaPrimitiveType }
        val boolFields = fields.filter { it.type == Boolean::class.javaPrimitiveType }
        val stringFields = fields.filter { it.type == String::class.java }
        if (intFields.size < 3 || boolFields.size != 1 || stringFields.size != 1) return null

        val methods = shape.methods
        val intMethods = methods.filter {
            !Modifier.isStatic(it.modifiers) &&
                it.returnType == Int::class.javaPrimitiveType &&
                it.parameterTypes.isEmpty()
        }

        if (intMethods.size !in 2..3) return null

        val selectedMethods = intMethods.sortedWith(compareBy({ it.name.length }, { it.name }))
        var closeAdScore = 160
        if (selectedMethods.size == 3) closeAdScore += 12
        closeAdScore -= methods.size / 4
        return ScanMatch(cls.name, selectedMethods.joinToString(",") { it.name }, "", closeAdScore)

        var score = 100
    }
}

internal class ZgaRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val methods = scanRuleMethods("ZgaRule", cls, logger) ?: return null

        val stringMethods = methods.filter {
            Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers) &&
                it.returnType == String::class.java &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == String::class.java
        }

        if (stringMethods.size < 2) return null

        val hasIoException = stringMethods.any { method ->
            method.exceptionTypes.any { it == IOException::class.java }
        }
        if (!hasIoException) return null
        val methodNames = stringMethods
            .map { it.name }
            .distinct()
            .sortedWith(compareBy<String>({ it.length }, { it }))
            .joinToString(",")

        var score = 80
        if (cls.simpleName == "zga") score += 60
        if (stringMethods.size == 2) score += 20
        score += 15
        score -= methods.size / 4
        score -= cls.simpleName.length
        return ScanMatch(cls.name, methodNames, "", score)
    }
}

internal class SearchBoxSetHintRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (!ViewGroup::class.java.isAssignableFrom(cls)) return null

        val methods = scanRuleMethods("SearchBoxSetHintRule", cls, logger) ?: return null
        val targetMethod = methods.firstOrNull { method ->
            if (method.returnType != Void.TYPE) return@firstOrNull false
            val p = method.parameterTypes
            p.size == 2 &&
                List::class.java.isAssignableFrom(p[0]) &&
                p[1] == Boolean::class.javaPrimitiveType
        } ?: return null

        val hasSwitcherGetter = methods.any { method ->
            method.parameterTypes.isEmpty() &&
                (TextSwitcher::class.java.isAssignableFrom(method.returnType) ||
                    method.returnType.name == "com.baidu.tbadk.util.TbTextSwitcher")
        }
        if (!hasSwitcherGetter) return null

        var score = 100
        if (targetMethod.name == "setHintTextDataList") score += 20
        score -= cls.simpleName.length
        score -= methods.size / 8

        return ScanMatch(cls.name, targetMethod.name, "", score)
    }
}

internal class HomeSearchBoxOwnerRule(
    private val ownerClassName: String,
    private val searchBoxClassName: String,
) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != ownerClassName) return null

        val shape = scanRuleClassShape("HomeSearchBoxOwnerRule", cls, logger) ?: return null
        val fields = shape.fields
        if (fields.none { it.type.name == searchBoxClassName }) return null

        val methods = shape.methods.filter { !Modifier.isStatic(it.modifiers) }
        val getterMethod = methods
            .filter { method ->
                method.parameterTypes.isEmpty() &&
                    method.returnType.name == searchBoxClassName
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Method>(
                    { if (it.name == "k") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            ) ?: return null

        val initMethod = methods
            .filter { method ->
                val p = method.parameterTypes
                method.returnType == Void.TYPE &&
                    p.size == 2 &&
                    p[0].name == "com.baidu.adp.widget.ListView.BdTypeRecyclerView" &&
                    p[1].name == "androidx.coordinatorlayout.widget.CoordinatorLayout"
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Method>(
                    { if (it.name == "l") 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            ) ?: return null

        var score = 120
        if (getterMethod.name == "k") score += 12
        if (initMethod.name == "l") score += 12
        score -= methods.size / 8

        return ScanMatch(cls.name, initMethod.name, getterMethod.name, score)
    }
}

internal class OriginalImageMethodsRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tbadk.coreExtra.view.UrlDragImageView") return null

        val methods = scanRuleMethods("OriginalImageMethodsRule", cls, logger) ?: return null
        val voidNoArgMethods = methods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty() &&
                !Modifier.isStatic(method.modifiers) &&
                method.name.length <= 3
        }
        val primaryReadyMethod =
            voidNoArgMethods.singleOrNull { it.name == "F" }?.name

        val triggerMethod =
            voidNoArgMethods.singleOrNull { it.name == "I" }?.name
                ?: return null

        val voidStringMethods = methods.filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java &&
                !Modifier.isStatic(method.modifiers) &&
                method.name.length <= 3
        }
        val directStartMethod =
            voidStringMethods.singleOrNull { it.name == "e0" }?.name

        var score = 100
        if (primaryReadyMethod == "F") score += 10
        if (triggerMethod == "I") score += 20
        if (directStartMethod == "e0") score += 10
        return ScanMatch(
            cls.name,
            "${primaryReadyMethod.orEmpty()},$triggerMethod,${directStartMethod.orEmpty()}",
            "",
            score,
        )
    }
}

internal class FreeCopyPopupRule : ScanRule() {
    override val minScore: Int = 100
    override val minScoreGap: Int = 8

    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!cls.name.startsWith("com.baidu.tieba.")) return null

        val roundLayoutClass = scanRuleClassForName(
            "FreeCopyPopupRule.RoundLinearLayout",
            "com.baidu.tbadk.core.dialog.RoundLinearLayout",
            cl,
            logger,
        )
        val emTextViewClass = scanRuleClassForName(
            "FreeCopyPopupRule.EMTextView",
            "com.baidu.tbadk.core.elementsMaven.view.EMTextView",
            cl,
            logger,
        )

        val shape = scanRuleClassShape("FreeCopyPopupRule", cls, logger) ?: return null
        val fields = shape.fields.filter { !Modifier.isStatic(it.modifiers) }
        val roundFields = fields.filter { field ->
            field.type.name == "com.baidu.tbadk.core.dialog.RoundLinearLayout" ||
                roundLayoutClass?.isAssignableFrom(field.type) == true
        }
        if (roundFields.isEmpty()) return null

        val textFields = fields.filter { field ->
            field.type.name == "com.baidu.tbadk.core.elementsMaven.view.EMTextView" ||
                emTextViewClass?.isAssignableFrom(field.type) == true
        }
        val textField = textFields.singleOrNull() ?: return null

        val contentViewMethods = shape.methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                View::class.java.isAssignableFrom(method.returnType)
        }
        val contentViewMethod = contentViewMethods.singleOrNull { it.name == "c" }
            ?: contentViewMethods.singleOrNull()
            ?: return null

        val hasContextField = fields.any { Context::class.java.isAssignableFrom(it.type) }
        val hasListField = fields.any { List::class.java.isAssignableFrom(it.type) }
        val hasTextViewField = fields.any { TextView::class.java.isAssignableFrom(it.type) }
        if (!hasContextField || !hasListField || !hasTextViewField) return null

        var score = 100
        if (cls.name == "com.baidu.tieba.ba6") score += 35
        if (contentViewMethod.name == "c") score += 12
        if (textField.name == "g") score += 8
        if (roundFields.any { it.name == "b" }) score += 4
        score -= contentViewMethods.size * 3
        score -= shape.methods.size / 12
        score -= cls.simpleName.length

        return ScanMatch(
            className = cls.name,
            methodName = contentViewMethod.name,
            fieldName = textField.name,
            score = score,
        )
    }
}

internal class HomeTopBarRightSlotRule(
    private val targetClassName: String,
) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != targetClassName) return null
        if (!ViewGroup::class.java.isAssignableFrom(cls)) return null

        val methods = scanRuleMethods("HomeTopBarRightSlotRule", cls, logger)
            ?.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            ?: return null
        if (!hasGetter(methods, "getSearchIconView") { ImageView::class.java.isAssignableFrom(it) }) return null
        if (!hasGetter(methods, "getGameIconView") { View::class.java.isAssignableFrom(it) }) return null
        if (!hasGetter(methods, "getRedDotView") { View::class.java.isAssignableFrom(it) }) return null
        if (!hasGetter(methods, "getTopBarTip") { View::class.java.isAssignableFrom(it) }) return null

        val hasScrollProgressMethod = methods.any { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Float::class.javaPrimitiveType
        }
        if (!hasScrollProgressMethod) return null

        val hasTabSelectedMethod = methods.any { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType
        }
        if (!hasTabSelectedMethod) return null

        val stateMethods = methods
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty() &&
                    !method.name.startsWith("on")
            }
            .map { it.name }
            .distinct()
            .sortedWith(compareBy<String>({ it.length }, { it }))

        if (stateMethods.isEmpty()) return null

        var score = 120
        if (stateMethods.contains("x")) score += 20
        if (stateMethods.contains("y")) score += 10
        score -= stateMethods.size
        score -= methods.size / 8

        return ScanMatch(
            className = cls.name,
            methodName = stateMethods.joinToString(","),
            fieldName = "",
            score = score,
        )
    }

    private fun hasGetter(
        methods: List<java.lang.reflect.Method>,
        name: String,
        returnTypeMatch: (Class<*>) -> Boolean,
    ): Boolean {
        return methods.any { method ->
            method.name == name &&
                method.parameterTypes.isEmpty() &&
                returnTypeMatch(method.returnType)
        }
    }
}

internal class PbFallingViewRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (!ViewGroup::class.java.isAssignableFrom(cls)) return null

        val methods = scanRuleMethods("PbFallingViewRule", cls, logger) ?: return null

        val initMethod = methods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0])
        } ?: return null

        val showMethod = methods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Boolean::class.javaPrimitiveType
        } ?: return null

        val clearMethod = methods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        } ?: return null

        var score = 80
        if (cls.name == StableTiebaHookPoints.PB_FALLING_VIEW_CLASS) score += 30
        if (initMethod.name == "u") score += 8
        if (showMethod.name == "C") score += 8
        if (clearMethod.name == "G") score += 8
        score -= cls.simpleName.length
        score -= methods.size / 12

        val packedMethods = "${initMethod.name},${showMethod.name},${clearMethod.name}"
        return ScanMatch(cls.name, packedMethods, "", score)
    }
}

internal class ShareTrackUrlBuilderRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null

        val methods = scanRuleMethods("ShareTrackUrlBuilderRule", cls, logger) ?: return null
        val composeMethod = methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == String::class.java &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == String::class.java &&
                method.parameterTypes[2] == String::class.java &&
                method.parameterTypes[3] == Boolean::class.javaPrimitiveType
        } ?: return null

        val appendMethod = methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == String::class.java &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == String::class.java
        } ?: return null

        val shareItemTypeName = "com.baidu.tbadk.coreExtra.share.ShareItem"
        val hasShareMutator = methods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType.name == shareItemTypeName &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].name == shareItemTypeName &&
                method.parameterTypes[1] == String::class.java
        }
        if (!hasShareMutator) return null

        var score = 120
        if (cls.name == "com.baidu.tieba.no6") score += 40
        if (composeMethod.name == "A") score += 10
        if (appendMethod.name == "e") score += 10
        score -= methods.size / 6
        score -= cls.simpleName.length

        val packedMethods = "${composeMethod.name},${appendMethod.name}"
        return ScanMatch(cls.name, packedMethods, "", score)
    }
}

internal class ImageViewerShareItemRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        val shape = scanRuleClassShape("ImageViewerShareItemRule", cls, logger) ?: return null
        val fields = shape.fields.filter { !Modifier.isStatic(it.modifiers) }
        val stringFields = fields.filter { it.type == String::class.java }
        val uriFields = fields.filter { it.type == Uri::class.java }
        if (stringFields.size < 3 || uriFields.isEmpty()) return null

        val methods = shape.methods
        val hasUriSetter = methods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Uri::class.java
        }
        val hasStaticJsonBuilder = methods.any { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == cls &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].name == "org.json.JSONObject" &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        if (!hasUriSetter && !hasStaticJsonBuilder) return null

        val picked = LinkedHashSet<String>(5)
        val titleField = pickStringField(
            fields = stringFields,
            picked = picked,
            exactNames = listOf("title", "wbtitle", "weiboTitle", "qqTitle", "qqzoneTitle"),
            containsKeywords = listOf("title"),
        )
        val contentField = pickStringField(
            fields = stringFields,
            picked = picked,
            exactNames = listOf("content", "wbcontent", "weiboContent", "qqzoneContent", "shareAbstract"),
            containsKeywords = listOf("content", "desc", "abstract"),
        )
        val linkField = pickStringField(
            fields = stringFields,
            picked = picked,
            exactNames = listOf("linkUrl", "spareLinkUrl", "url"),
            containsKeywords = listOf("link", "url", "schema"),
        )
        val localFileField = pickLocalFileField(stringFields, picked)
        val imageUrlField = pickImageUrlField(stringFields, picked)
        val resolvedLocalFileField = localFileField ?: pickStringField(stringFields, picked)
        val imageUriField = pickUriField(uriFields) ?: return null

        val packedFields = listOf(
            titleField?.name.orEmpty(),
            contentField?.name.orEmpty(),
            linkField?.name.orEmpty(),
            imageUriField.name,
            imageUrlField?.name.orEmpty(),
            resolvedLocalFileField?.name.orEmpty(),
        ).joinToString(",")

        var score = 100
        if (cls.name == "com.baidu.tbadk.coreExtra.share.ShareItem") score += 30
        if (titleField != null) score += 4
        if (contentField != null) score += 4
        if (linkField != null) score += 4
        if (imageUrlField != null) score += 4
        if (resolvedLocalFileField != null) score += 2
        score -= methods.size / 8
        score -= cls.simpleName.length
        return ScanMatch(
            className = cls.name,
            methodName = "",
            fieldName = packedFields,
            score = score,
        )
    }

    private fun pickStringField(
        fields: List<java.lang.reflect.Field>,
        picked: MutableSet<String>,
        exactNames: List<String> = emptyList(),
        containsKeywords: List<String> = emptyList(),
    ): java.lang.reflect.Field? {
        val candidate = if (exactNames.isNotEmpty()) {
            fields.firstOrNull { field ->
                field.name !in picked && exactNames.any { exact ->
                    field.name.equals(exact, ignoreCase = true)
                }
            } ?: fields.firstOrNull { field ->
                field.name !in picked && containsKeywords.any { key ->
                    field.name.contains(key, ignoreCase = true)
                }
            } ?: fields.firstOrNull { it.name !in picked }
        } else if (containsKeywords.isEmpty()) {
            fields.firstOrNull { it.name !in picked }
        } else {
            fields.firstOrNull { field ->
                field.name !in picked && containsKeywords.any { key -> field.name.contains(key, ignoreCase = true) }
            } ?: fields.firstOrNull { it.name !in picked }
        }
        if (candidate != null) picked.add(candidate.name)
        return candidate
    }

    private fun pickUriField(fields: List<java.lang.reflect.Field>): java.lang.reflect.Field? {
        return fields.minWithOrNull(
            compareBy<java.lang.reflect.Field>(
                { if (it.name == "imageUri") 0 else 1 },
                { if (it.name.contains("image", ignoreCase = true) && it.name.contains("uri", ignoreCase = true)) 0 else 1 },
                { if (it.name.contains("uri", ignoreCase = true)) 0 else 1 },
                { it.name.length },
                { it.name },
            ),
        )
    }

    private fun pickImageUrlField(
        fields: List<java.lang.reflect.Field>,
        picked: MutableSet<String>,
    ): java.lang.reflect.Field? {
        val candidate = fields.firstOrNull { field ->
            field.name !in picked && field.name.equals("imageUrl", ignoreCase = true)
        } ?: fields.firstOrNull { field ->
            field.name !in picked &&
                field.name.contains("image", ignoreCase = true) &&
                field.name.contains("url", ignoreCase = true)
        }
        if (candidate != null) picked.add(candidate.name)
        return candidate
    }

    private fun pickLocalFileField(
        fields: List<java.lang.reflect.Field>,
        picked: MutableSet<String>,
    ): java.lang.reflect.Field? {
        val candidate = fields.firstOrNull { field ->
            field.name !in picked && field.name.equals("localFile", ignoreCase = true)
        } ?: fields.firstOrNull { field ->
            field.name !in picked &&
                (
                    field.name.contains("local", ignoreCase = true) ||
                        field.name.contains("file", ignoreCase = true) ||
                        field.name.contains("path", ignoreCase = true)
                    )
        }
        if (candidate != null) picked.add(candidate.name)
        return candidate
    }
}

internal class ImageViewerShareConfigRule(
    private val shareItemClassName: String,
) : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null

        val shape = scanRuleClassShape("ImageViewerShareConfigRule", cls, logger) ?: return null
        val fields = shape.fields.filter { !Modifier.isStatic(it.modifiers) }
        val imageDialogField = fields.filter { it.type == Boolean::class.javaPrimitiveType }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name.contains("image", ignoreCase = true)) 0 else 1 },
                    { if (it.name.contains("viewer", ignoreCase = true)) 0 else 1 },
                    { if (it.name.contains("dialog", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            ) ?: return null

        val shareItemField = fields.firstOrNull { it.type.name == shareItemClassName } ?: return null

        val methods = shape.methods
        val addOutsideMethod = methods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == View.OnClickListener::class.java
        } ?: return null

        val getRequestDataMethod = methods.firstOrNull { method ->
            method.parameterTypes.isEmpty() && Map::class.java.isAssignableFrom(method.returnType)
        } ?: return null

        val setRequestDataMethod = methods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Map::class.java.isAssignableFrom(method.parameterTypes[0])
        } ?: return null

        val allMethods = (methods + cls.methods).distinctBy { method ->
            "${method.name}#${method.parameterTypes.joinToString(",") { it.name }}#${method.returnType.name}"
        }
        val getContextMethod = allMethods.firstOrNull { method ->
            method.parameterTypes.isEmpty() &&
                Context::class.java.isAssignableFrom(method.returnType)
        } ?: return null

        val packedMethods = listOf(
            addOutsideMethod.name,
            getRequestDataMethod.name,
            setRequestDataMethod.name,
            getContextMethod.name,
        ).joinToString(",")
        val packedFields = listOf(imageDialogField.name, shareItemField.name).joinToString(",")

        var score = 110
        if (shareItemField.name == "shareItem") score += 10
        if (imageDialogField.name.contains("image", ignoreCase = true)) score += 8
        score -= cls.simpleName.length
        score -= methods.size / 8
        return ScanMatch(cls.name, packedMethods, packedFields, score)
    }
}

internal class ImageViewerShareItemViewRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!View::class.java.isAssignableFrom(cls)) return null
        val methods = scanRuleMethods("ImageViewerShareItemViewRule", cls, logger) ?: return null
        val byRes = methods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return null
        val byText = methods.firstOrNull { method ->
            method.name == byRes.name &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == String::class.java
        } ?: return null

        var score = 100
        if (byRes.name == "setItemName") score += 20
        score -= cls.simpleName.length
        score -= methods.size / 10
        return ScanMatch(
            className = cls.name,
            methodName = "${byRes.name},${byText.name}",
            fieldName = "",
            score = score,
        )
    }
}

internal class ImageViewerShareIconResourceRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tieba.R\$drawable") return null
        val fieldMatch = (scanRuleFields("ImageViewerShareIconResourceRule", cls, logger) ?: return null)
            .asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    field.type == Int::class.javaPrimitiveType
            }
            .mapNotNull { field ->
                val score = scoreShareIconResourceName(field.name)
                if (score > 0) field to score else null
            }
            .maxWithOrNull(
                compareBy<Pair<java.lang.reflect.Field, Int>>(
                    { it.second },
                    { -it.first.name.length },
                    { it.first.name },
                ),
            ) ?: return null
        val field = fieldMatch.first
        val resId = scanSubStep("ScanRules.ImageViewerShareIconResourceRule.${field.name}.ResourceId", logger, null) {
            field.isAccessible = true
            field.getInt(null)
        }?.takeIf { it != 0 } ?: return null
        return ScanMatch(
            className = cls.name,
            methodName = field.name,
            fieldName = resId.toString(),
            score = fieldMatch.second,
        )
    }

    private fun scoreShareIconResourceName(name: String): Int {
        val lower = name.lowercase()
        if (!lower.contains("share")) return 0

        var score = 50
        if (lower == "icon_pure_pb_bottom_share24_svg") score += 90
        if (lower == "icon_pb_bottom_share_n") score += 50
        if (lower.startsWith("icon_")) score += 12
        if (lower.contains("pb")) score += 36
        if (lower.contains("bottom")) score += 30
        if (lower.contains("pure")) score += 20
        if (lower.contains("24")) score += 10
        if (lower.contains("svg")) score += 10

        val unrelatedMarkers = listOf(
            "wechat",
            "weibo",
            "qq",
            "qzone",
            "yy",
            "friend",
            "card",
            "live",
            "play",
            "hot",
            "nav",
            "topbar",
            "recommend",
            "loading",
            "background",
            "dialog",
            "input",
            "sdk",
            "webview",
            "bubble",
            "topic",
            "tei",
        )
        for (marker in unrelatedMarkers) {
            if (lower.contains(marker)) score -= 80
        }
        return if (score >= 100) score else 0
    }
}

/**
 * 查找 FeedCardView 上的数据绑定方法。
 * 签名：void w(CardData)，CardData 也就是 y99，是一个数据类。
 * 它包含 List、String、boolean 字段，toString() 以 "CardData(" 开头。
 */
internal class FeedCardBindRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tieba.feed.card.FeedCardView") return null

        val candidates = scanRuleMethods("FeedCardBindRule", cls, logger)
            ?.filter { m ->
            !Modifier.isStatic(m.modifiers) &&
                m.returnType == Void.TYPE &&
                m.parameterTypes.size == 1 &&
                !m.parameterTypes[0].isPrimitive
            }
            ?: return null

        val bindCandidates = candidates.filter { m ->
            val paramType = m.parameterTypes[0]
            isCardDataLike(paramType, logger)
        }
        val bindMethod = bindCandidates.firstOrNull { it.name == "w" }
            ?: bindCandidates.singleOrNull()
            ?: return null

        var score = 100
        if (bindMethod.name == "w") score += 20
        if (bindMethod.name.length <= 2) score += 10
        score -= candidates.size / 2

        return ScanMatch(
            className = cls.name,
            methodName = bindMethod.name,
            fieldName = "",
            score = score,
        )
    }

    /**
     * 用这些特征识别 CardData 类，也就是 y99。
     * - 有 List 字段 dataList
     * - 有多个 String 字段，比如 schema、threadId、userId
     * - 有 boolean 字段，比如 isGreyMode、canMultiManage、supportLongClick
     * - 有 Map 字段 appendixMap
     */
    private fun isCardDataLike(cls: Class<*>, logger: ScanLogger?): Boolean {
        val fields = scanRuleFields("FeedCardBindRule.CardData", cls, logger)
            ?.filter { !Modifier.isStatic(it.modifiers) }
            ?: return false
        val hasListField = fields.any { List::class.java.isAssignableFrom(it.type) }
        val stringFieldCount = fields.count { it.type == String::class.java }
        val boolFieldCount = fields.count { it.type == Boolean::class.javaPrimitiveType }
        val hasMapField = fields.any { Map::class.java.isAssignableFrom(it.type) }
        return hasListField && stringFieldCount >= 2 && boolFieldCount >= 2 && hasMapField
    }
}

/**
 * 解析 CardHeadUiState.businessInfoMap。
 * 这里保存 feed_head 参数，比如 thread_id、thread_type 和 card_type。
 */
internal class FeedHeadParamsFieldRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tieba.feed.component.uistate.CardHeadUiState") return null

        val fields = scanRuleFields("FeedHeadParamsFieldRule", cls, logger)
            ?.filter { field ->
                !Modifier.isStatic(field.modifiers) &&
                    Map::class.java.isAssignableFrom(field.type)
            }
            ?: return null
        if (fields.size < 2) return null

        return ScanMatch(
            className = cls.name,
            methodName = "",
            fieldName = fields[0].name,
            score = 120 - fields.size,
        )
    }
}

internal class PbGestureScaleRule : ScanRule() {
    override fun match(cls: Class<*>, cl: ClassLoader): ScanMatch? = match(cls, cl, null)

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return null
        if (!cls.name.startsWith("com.baidu.tieba.")) return null

        val shape = scanRuleClassShape("PbGestureScaleRule", cls, logger) ?: return null
        val instanceFields = shape.fields.filter { !Modifier.isStatic(it.modifiers) }
        val hasScaleDetectorField = instanceFields.any { it.type == ScaleGestureDetector::class.java }
        if (!hasScaleDetectorField) return null

        val methods = shape.methods
        val dispatchMethod = methods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == MotionEvent::class.java
        } ?: return null

        val listenerSetter = methods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].isInterface &&
                hasBooleanOnlyCallback(method.parameterTypes[0], logger)
        } ?: return null

        val scaleListenerClass = scanRuleNestedClasses("PbGestureScaleRule", cls, logger)
            ?.firstOrNull { inner ->
                val parent = inner.superclass ?: return@firstOrNull false
                ScaleGestureDetector.SimpleOnScaleGestureListener::class.java.isAssignableFrom(parent)
            }
            ?: return null

        val onScaleMethod = scanRuleMethods("PbGestureScaleRule.ScaleListener", scaleListenerClass, logger)
            ?.firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.name == "onScale" &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == ScaleGestureDetector::class.java
            }
            ?: return null

        var score = 100
        if (dispatchMethod.name.length <= 2) score += 8
        if (listenerSetter.name.length <= 2) score += 8
        if (scaleListenerClass.name.contains("$")) score += 6
        if (cls.simpleName.length <= 4) score += 8
        score -= methods.size / 3
        score -= cls.simpleName.length

        val packedMethods = listOf(
            dispatchMethod.name,
            listenerSetter.name,
            onScaleMethod.name,
        ).joinToString(",")
        return ScanMatch(
            className = cls.name,
            methodName = packedMethods,
            fieldName = scaleListenerClass.name,
            score = score,
        )
    }

    private fun hasBooleanOnlyCallback(type: Class<*>, logger: ScanLogger?): Boolean {
        if (!type.isInterface) return false
        val abstractMethods = scanRuleMethods("PbGestureScaleRule.BooleanCallback", type, logger)
            ?.filter { Modifier.isAbstract(it.modifiers) }
            ?: return false
        if (abstractMethods.size != 1) return false
        val target = abstractMethods[0]
        if (target.returnType != Void.TYPE) return false
        if (target.parameterTypes.size != 1) return false
        val param = target.parameterTypes[0]
        return param == Boolean::class.javaPrimitiveType || param == Boolean::class.java
    }
}
