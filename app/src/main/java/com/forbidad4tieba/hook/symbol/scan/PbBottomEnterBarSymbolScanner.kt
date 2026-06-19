package com.forbidad4tieba.hook.symbol.scan

import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.PbBottomEnterBarScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PbBottomEnterBarSymbolScanner {
    fun scan(cl: ClassLoader, logger: ScanLogger?): PbBottomEnterBarScanSymbols {
        val bottomEnterBar = scanBottomEnterBarView(cl, logger)
        val animationTip = scanEnterFrsAnimationTip(cl, logger)
        val hotTopicGuide = scanHotTopicGuide(cl, logger)
        return PbBottomEnterBarScanSymbols(
            bottomEnterBarViewClass = bottomEnterBar.bottomEnterBarViewClass,
            bottomEnterBarConstructorCount = bottomEnterBar.bottomEnterBarConstructorCount,
            bottomEnterBarRefreshMethodSpecs = bottomEnterBar.bottomEnterBarRefreshMethodSpecs,
            enterFrsAnimationTipViewClass = animationTip.enterFrsAnimationTipViewClass,
            enterFrsAnimationTipConstructorCount = animationTip.enterFrsAnimationTipConstructorCount,
            enterFrsAnimationTipCallerClasses = animationTip.enterFrsAnimationTipCallerClasses,
            hotTopicGuideTotalViewMethod = hotTopicGuide.hotTopicGuideTotalViewMethod,
            hotTopicGuideRefreshMethodSpecs = hotTopicGuide.hotTopicGuideRefreshMethodSpecs,
        )
    }

    private fun scanBottomEnterBarView(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbBottomEnterBarScanSymbols {
        val clazz = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS, cl) ?: run {
            log(
                logger,
                "pbBottomEnterBar.bottomEnterBarView: class not found: " +
                    StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS,
            )
            return PbBottomEnterBarScanSymbols()
        }
        val constructorCount = constructorCount("PbBottomEnterBarHook.BottomEnterBarView", clazz, logger)
        val methods = declaredMethods("PbBottomEnterBarHook.BottomEnterBarView", clazz, logger)
            ?: return PbBottomEnterBarScanSymbols(
                bottomEnterBarViewClass = clazz.name,
                bottomEnterBarConstructorCount = constructorCount,
            )
        val refreshSpecs = methods
            .asSequence()
            .filter(::isBottomEnterBarRefreshMethod)
            .map(::toMethodSpec)
            .distinct()
            .sorted()
            .toList()
        if (refreshSpecs.isEmpty()) {
            log(logger, "pbBottomEnterBar.bottomEnterBarView: refresh methods not found")
        } else {
            log(logger, "pbBottomEnterBar.bottomEnterBarView refresh methods: ${refreshSpecs.joinToString(";")}")
        }
        return PbBottomEnterBarScanSymbols(
            bottomEnterBarViewClass = clazz.name,
            bottomEnterBarConstructorCount = constructorCount,
            bottomEnterBarRefreshMethodSpecs = refreshSpecs,
        )
    }

    private fun scanEnterFrsAnimationTip(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbBottomEnterBarScanSymbols {
        val clazz = ScanReflection.safeFindClass(StableTiebaHookPoints.TB_ANIMATION_TIP_VIEW_CLASS, cl) ?: run {
            log(
                logger,
                "pbBottomEnterBar.animationTip: class not found: " +
                    StableTiebaHookPoints.TB_ANIMATION_TIP_VIEW_CLASS,
            )
            return PbBottomEnterBarScanSymbols()
        }
        val callerClasses = listOf(
            StableTiebaHookPoints.PB_VIEW_UTIL_KT_CLASS,
            StableTiebaHookPoints.SPRITE_ANIMATION_TIP_MANAGER_CLASS,
        ).filter { className ->
            ScanReflection.safeFindClass(className, cl) != null
        }
        if (callerClasses.size < 2) {
            log(
                logger,
                "pbBottomEnterBar.animationTip caller classes: " +
                    callerClasses.joinToString(";").ifBlank { "-" },
            )
        }
        return PbBottomEnterBarScanSymbols(
            enterFrsAnimationTipViewClass = clazz.name,
            enterFrsAnimationTipConstructorCount =
                constructorCount("PbBottomEnterBarHook.AnimationTip", clazz, logger),
            enterFrsAnimationTipCallerClasses = callerClasses,
        )
    }

    private fun scanHotTopicGuide(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbBottomEnterBarScanSymbols {
        val clazz = ScanReflection.safeFindClass(StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS, cl) ?: run {
            log(
                logger,
                "pbBottomEnterBar.hotTopicGuide: class not found: " +
                    StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS,
            )
            return PbBottomEnterBarScanSymbols()
        }
        val methods = declaredMethods("PbBottomEnterBarHook.HotTopicGuide", clazz, logger)
            ?: return PbBottomEnterBarScanSymbols()
        val totalViewMethod = scanHotTopicGuideTotalViewMethod(clazz, methods, logger)
        return PbBottomEnterBarScanSymbols(
            hotTopicGuideTotalViewMethod = totalViewMethod,
            hotTopicGuideRefreshMethodSpecs = scanHotTopicGuideRefreshMethodSpecs(methods, logger),
        )
    }

    private fun scanHotTopicGuideTotalViewMethod(
        clazz: Class<*>,
        methods: List<Method>,
        logger: ScanLogger?,
    ): String? {
        val candidates = methods.filter(::isHotTopicGuideTotalViewMethod)
        val method = candidates.singleOrNull() ?: run {
            log(
                logger,
                "pbBottomEnterBar.hotTopicGuide: total view method candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return null
        }
        log(logger, "pbBottomEnterBar.hotTopicGuide matched: ${clazz.name}.${method.name}")
        return method.name
    }

    private fun scanHotTopicGuideRefreshMethodSpecs(
        methods: List<Method>,
        logger: ScanLogger?,
    ): List<String> {
        val specs = methods
            .asSequence()
            .filter(::isHotTopicGuideRefreshMethod)
            .map(::toRefreshMethodSpec)
            .distinct()
            .sorted()
            .toList()
        if (specs.isEmpty()) {
            log(logger, "pbBottomEnterBar.hotTopicGuide: refresh methods not found")
        } else {
            log(logger, "pbBottomEnterBar.hotTopicGuide refresh methods: ${specs.joinToString(";")}")
        }
        return specs
    }

    private fun isHotTopicGuideTotalViewMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            method.returnType == View::class.java
    }

    private fun isHotTopicGuideRefreshMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return params.isEmpty() ||
            (params.size == 1 && params[0] == Int::class.javaPrimitiveType)
    }

    private fun isBottomEnterBarRefreshMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return (method.name == "setData" && params.size == 1) ||
            (method.name == "onChangeSkinType" && params.isEmpty())
    }

    private fun toRefreshMethodSpec(method: Method): String {
        return toMethodSpec(method)
    }

    private fun toMethodSpec(method: Method): String {
        return method.name + "|" + method.parameterTypes.joinToString(",") { it.name }
    }

    private fun declaredMethods(tag: String, clazz: Class<*>, logger: ScanLogger?): List<Method>? {
        return scanSubStep("$tag.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun constructorCount(tag: String, clazz: Class<*>, logger: ScanLogger?): Int? {
        return scanSubStep("$tag.Constructors", logger, null) {
            clazz.declaredConstructors.size
        }
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$ret"
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
