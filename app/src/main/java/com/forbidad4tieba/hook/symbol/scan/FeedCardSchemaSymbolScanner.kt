package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Follow the card click's schema dispatch instead of guessing a String field by name or order. */
internal object FeedCardSchemaSymbolScanner {
    private const val TAG = "CustomPostCardBlockHook.TopicSchema"

    fun scan(context: Context, cl: ClassLoader, bindSpec: String?, logger: ScanLogger?): String? =
        scanSubStep(TAG, logger, null) {
            val cardClass = cardClass(cl, bindSpec) ?: return@scanSubStep null
            val paths = listOfNotNull(context.applicationInfo?.sourceDir) +
                context.applicationInfo?.splitSourceDirs.orEmpty()
            val spec = HookSymbolScanSession.withDexKitBridge(paths, logger) {
                scan(it.bridge, cardClass.name, logger)
            } ?: return@scanSubStep null
            check(restore(cl, bindSpec, spec) != null) { "card schema getter failed reflection validation" }
            spec
        }

    internal fun scan(bridge: DexKitBridge, cardClass: String, logger: ScanLogger?): String? {
        val click = unique("Click", bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().addEqString("is_need_intercepted_deal_schema")
                    .addEqString("card_click_other")),
        ).filter {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("android.view.View", cardClass)
        }, logger) ?: return null

        // The getter result is immediately passed to the Context/String schema dispatcher.
        // Thread/user IDs read elsewhere in the click handler do not satisfy this call edge.
        val getters = click.invokes.zipWithNext().mapNotNull { (getter, dispatch) ->
            getter.takeIf {
                it.declaredClassName == cardClass && !Modifier.isStatic(it.modifiers) &&
                    it.paramCount == 0 && it.returnTypeName == "java.lang.String" &&
                    Modifier.isStatic(dispatch.modifiers) && dispatch.returnTypeName == "void" &&
                    dispatch.paramTypeNames == listOf("android.content.Context", "java.lang.String")
            }
        }.filter { getter ->
            val reads = getter.usingFields.filter {
                it.usingType.isRead() && it.field.declaredClassName == cardClass &&
                    !Modifier.isStatic(it.field.modifiers)
            }.map { it.field }.distinctBy { it.descriptor }
            reads.singleOrNull()?.let { it.typeName == "java.lang.String" && Modifier.isFinal(it.modifiers) } == true &&
                getter.usingFields.none { it.usingType.isWrite() } && getter.usingStrings.isEmpty() &&
                getter.invokes.all { it.declaredClassName.startsWith("com.baidu.titan.sdk.runtime.") }
        }
        val getter = unique("Getter", getters, logger) ?: return null
        return "${getter.declaredClassName}#${getter.methodName}"
    }

    fun restore(cl: ClassLoader, bindSpec: String?, getterSpec: String?): Method? {
        if (getterSpec == null) return null
        return scanSubStep("$TAG.Restore", null, null) {
            val owner = cardClass(cl, bindSpec) ?: return@scanSubStep null
            val parts = getterSpec.split('#')
            if (parts.size != 2 || parts[0] != owner.name || parts[1].isBlank()) return@scanSubStep null
            owner.getDeclaredMethod(parts[1]).takeIf {
                Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) &&
                    !Modifier.isAbstract(it.modifiers) && it.returnType == String::class.java
            }?.apply { isAccessible = true }
        }
    }

    fun isCacheValid(cl: ClassLoader, bindSpec: String?, getterSpec: String?): Boolean =
        getterSpec == null || restore(cl, bindSpec, getterSpec) != null

    private fun cardClass(cl: ClassLoader, bindSpec: String?): Class<*>? {
        val parts = bindSpec?.split('|') ?: return null
        if (parts.size != 3 || parts[0].isBlank() || parts[1] != "void" ||
            parts[2].isBlank() || ',' in parts[2]
        ) return null
        val cardClass = Class.forName(parts[2], false, cl)
        val viewClass = Class.forName(StableTiebaHookPoints.FEED_CARD_VIEW_CLASS, false, cl)
        val bind = viewClass.getDeclaredMethod(parts[0], cardClass)
        return cardClass.takeIf { !it.isPrimitive && !Modifier.isStatic(bind.modifiers) && bind.returnType == Void.TYPE }
    }

    private fun unique(label: String, methods: List<MethodData>, logger: ScanLogger?): MethodData? =
        selectUniqueScanCandidate("$TAG.$label", methods.distinctBy { it.descriptor }, logger) { it.descriptor }
}
