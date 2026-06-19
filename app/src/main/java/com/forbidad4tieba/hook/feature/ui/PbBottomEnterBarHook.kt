package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ViewExt
import com.forbidad4tieba.hook.core.Api102ModuleFacade
import com.forbidad4tieba.hook.symbol.model.PbBottomEnterBarHotTopicGuideSymbols
import com.forbidad4tieba.hook.symbol.model.PbBottomEnterBarStableSymbols
import java.lang.reflect.Method

object PbBottomEnterBarHook {
    private const val MAX_PREDRAW_SQUASH_CHECKS = 3

    internal fun hookStable(targets: PbBottomEnterBarStableSymbols) {
        val mod = XposedCompat.module ?: return
        val installed = installBottomEnterBarHook(mod, targets) +
            installPbEnterFrsAnimationTipHook(mod, targets)
        if (installed == 0) {
            XposedCompat.log("[PbBottomEnterBarHook] stable hook target NOT FOUND")
        } else {
            XposedCompat.log("[PbBottomEnterBarHook] stable hooks INSTALLED: hooks=$installed")
        }
    }

    internal fun hookHotTopicGuide(targets: PbBottomEnterBarHotTopicGuideSymbols) {
        val mod = XposedCompat.module ?: return
        val installed = installHotTopicGuideHook(mod, targets)
        if (installed == 0) {
            XposedCompat.log("[PbBottomEnterBarHook] hot topic guide hook target NOT FOUND")
        } else {
            XposedCompat.log(
                "[PbBottomEnterBarHook] hot topic guide hook INSTALLED: " +
                    "hooks=$installed refresh=${targets.refreshMethods.size}",
            )
        }
    }

    private fun installBottomEnterBarHook(
        mod: Api102ModuleFacade,
        targets: PbBottomEnterBarStableSymbols,
    ): Int {
        try {
            val clazz = targets.bottomEnterBarViewClass ?: return 0

            var installed = 0
            for (ctor in clazz.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    if (view != null) {
                        squashBottomEnterBar(view)
                    }
                    result
                }
                installed++
            }

            for (method in targets.bottomEnterBarRefreshMethods.distinct()) {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { view ->
                        squashBottomEnterBar(view)
                    }
                    result
                }
                installed++
            }

            return installed
        } catch (t: Throwable) {
            XposedCompat.log("[PbBottomEnterBarHook] bottom enter bar hook FAILED: ${t.message}")
            XposedCompat.log(t)
            return 0
        }
    }

    private fun installHotTopicGuideHook(
        mod: Api102ModuleFacade,
        targets: PbBottomEnterBarHotTopicGuideSymbols,
    ): Int {
        try {
            val clazz = targets.guideClass
            val totalViewMethod = targets.totalViewMethod.apply { isAccessible = true }
            var installed = 0
            for (ctor in clazz.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject?.let { guide ->
                        squashHotTopicGuide(guide, totalViewMethod)
                    }
                    result
                }
                installed++
            }

            for (method in targets.refreshMethods.distinct()) {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject?.let { guide ->
                        squashHotTopicGuide(guide, totalViewMethod)
                    }
                    result
                }
                installed++
            }
            return installed
        } catch (t: Throwable) {
            XposedCompat.log("[PbBottomEnterBarHook] hot topic guide hook FAILED: ${t.message}")
            XposedCompat.log(t)
            return 0
        }
    }

    private fun installPbEnterFrsAnimationTipHook(
        mod: Api102ModuleFacade,
        targets: PbBottomEnterBarStableSymbols,
    ): Int {
        try {
            val clazz = targets.enterFrsAnimationTipViewClass ?: return 0

            var installed = 0
            for (ctor in clazz.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    if (view != null && isPbEnterFrsAnimationTipConstruction()) {
                        squashPbEnterFrsAnimationTip(view)
                    }
                    result
                }
                installed++
            }
            return installed
        } catch (t: Throwable) {
            XposedCompat.log("[PbBottomEnterBarHook] pb enter frs animation tip hook FAILED: ${t.message}")
            XposedCompat.log(t)
            return 0
        }
    }

    private fun squashHotTopicGuide(guide: Any, totalViewMethod: Method) {
        val root = try {
            totalViewMethod.invoke(guide) as? View
        } catch (t: Throwable) {
            XposedCompat.logD("[PbBottomEnterBarHook] hot topic root resolve failed: ${t.message}")
            null
        } ?: return
        squashBannerView(root)
    }

    private fun squashBottomEnterBar(view: View) {
        squashBannerView(view)
    }

    private fun squashPbEnterFrsAnimationTip(view: View) {
        squashBannerView(view)
    }

    private fun isPbEnterFrsAnimationTipConstruction(): Boolean {
        var sawPbViewUtil = false
        var sawSpriteAnimationTipManager = false
        for (frame in Thread.currentThread().stackTrace) {
            when (frame.className) {
                StableTiebaHookPoints.PB_VIEW_UTIL_KT_CLASS -> sawPbViewUtil = true
                StableTiebaHookPoints.SPRITE_ANIMATION_TIP_MANAGER_CLASS -> sawSpriteAnimationTipManager = true
            }
            if (sawPbViewUtil && sawSpriteAnimationTipManager) return true
        }
        return false
    }

    private fun squashBannerView(view: View) {
        val wasSquashed = isSquashedZeroLayout(view)
        ViewExt.squashViewRemembering(view)
        val listenerInstalled = installPersistentSquash(view)
        if (!listenerInstalled && wasSquashed) return
        view.post {
            if (!isSquashedZeroLayout(view)) {
                ViewExt.squashViewRemembering(view)
            }
        }
        view.postOnAnimation {
            if (!isSquashedZeroLayout(view)) {
                ViewExt.squashViewRemembering(view)
            }
        }
    }

    private fun installPersistentSquash(view: View): Boolean {
        if (view.getTag(ViewExt.TAG_EMPTY_VIEW) == true) return false
        view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
        val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
            private var checks = 0

            override fun onPreDraw(): Boolean {
                if (!isSquashedZeroLayout(view)) {
                    ViewExt.squashViewRemembering(view)
                }
                checks++
                if (isSquashedZeroLayout(view) || checks >= MAX_PREDRAW_SQUASH_CHECKS) {
                    removePreDrawListener(view, this)
                }
                return true
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
                ViewExt.squashViewRemembering(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                removePreDrawListener(v, preDrawListener)
            }
        })
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (!isSquashedZeroLayout(v)) {
                ViewExt.squashViewRemembering(v)
            }
        }
        if (view.isAttachedToWindow) {
            runCatching {
                view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }
        return true
    }

    private fun removePreDrawListener(view: View, listener: ViewTreeObserver.OnPreDrawListener) {
        runCatching {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
    }

    private fun isSquashedZeroLayout(view: View): Boolean {
        if (view.visibility != View.GONE || view.minimumHeight != 0 || view.minimumWidth != 0) return false
        if (view.paddingLeft != 0 || view.paddingTop != 0 || view.paddingRight != 0 || view.paddingBottom != 0) {
            return false
        }
        val lp = view.layoutParams ?: return true
        if (lp.width != 0 || lp.height != 0) return false
        return lp !is ViewGroup.MarginLayoutParams ||
            (lp.leftMargin == 0 && lp.topMargin == 0 && lp.rightMargin == 0 && lp.bottomMargin == 0)
    }
}
