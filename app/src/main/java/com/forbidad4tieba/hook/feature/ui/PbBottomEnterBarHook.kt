package com.forbidad4tieba.hook.feature.ui

import android.view.View
import android.view.ViewTreeObserver
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ViewExt
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object PbBottomEnterBarHook {
    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val installed = installBottomEnterBarHook(mod, cl) +
            installHotTopicGuideHook(mod, cl) +
            installPbEnterFrsAnimationTipHook(mod, cl)
        if (installed == 0) {
            XposedCompat.log("[PbBottomEnterBarHook] hook target NOT FOUND")
        } else {
            XposedCompat.log("[PbBottomEnterBarHook] hook INSTALLED: hooks=$installed")
        }
    }

    private fun installBottomEnterBarHook(mod: XposedModule, cl: ClassLoader): Int {
        try {
            val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS, cl)
            if (clazz == null) {
                XposedCompat.log("[PbBottomEnterBarHook] class NOT FOUND: ${StableTiebaHookPoints.PB_BOTTOM_ENTER_BAR_VIEW_CLASS}")
                return 0
            }

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

            for (method in clazz.declaredMethods) {
                if (!isBottomEnterBarRefreshMethod(method)) continue
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

    private fun isBottomEnterBarRefreshMethod(method: Method): Boolean {
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return (method.name == "setData" && params.size == 1) ||
            (method.name == "onChangeSkinType" && params.isEmpty())
    }

    private fun installHotTopicGuideHook(mod: XposedModule, cl: ClassLoader): Int {
        try {
            val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS, cl)
            if (clazz == null) {
                XposedCompat.log("[PbBottomEnterBarHook] class NOT FOUND: ${StableTiebaHookPoints.PB_HOT_TOPIC_GUIDE_VIEW_CLASS}")
                return 0
            }

            val totalViewMethod = resolveHotTopicTotalViewMethod(clazz) ?: return 0
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

            for (method in clazz.declaredMethods) {
                if (!isHotTopicGuideRefreshMethod(method)) continue
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

    private fun installPbEnterFrsAnimationTipHook(mod: XposedModule, cl: ClassLoader): Int {
        try {
            val clazz = XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_ANIMATION_TIP_VIEW_CLASS, cl)
            if (clazz == null) {
                XposedCompat.logD("[PbBottomEnterBarHook] class NOT FOUND: ${StableTiebaHookPoints.TB_ANIMATION_TIP_VIEW_CLASS}")
                return 0
            }

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

    private fun resolveHotTopicTotalViewMethod(clazz: Class<*>): Method? {
        val candidates = clazz.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                method.returnType == View::class.java
        }
        if (candidates.size != 1) {
            XposedCompat.log(
                "[PbBottomEnterBarHook] hot topic guide root view method NOT FOUND: " +
                    "candidates=${candidates.size}"
            )
            return null
        }
        return candidates.first().apply { isAccessible = true }
    }

    private fun isHotTopicGuideRefreshMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        return params.isEmpty() ||
            (params.size == 1 && params[0] == Int::class.javaPrimitiveType)
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
        ViewExt.squashViewRemembering(view)
        installPersistentSquash(view)
        view.post { ViewExt.squashViewRemembering(view) }
        view.postOnAnimation { ViewExt.squashViewRemembering(view) }
    }

    private fun installPersistentSquash(view: View) {
        if (view.getTag(ViewExt.TAG_EMPTY_VIEW) == true) return
        view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (view.visibility != View.GONE || view.width > 0 || view.height > 0) {
                ViewExt.squashViewRemembering(view)
            }
            true
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching {
                    v.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                }
                ViewExt.squashViewRemembering(v)
            }

            override fun onViewDetachedFromWindow(v: View) {
                runCatching {
                    if (v.viewTreeObserver.isAlive) {
                        v.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                    }
                }
            }
        })
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.visibility != View.GONE || v.width > 0 || v.height > 0) {
                ViewExt.squashViewRemembering(v)
            }
        }
        if (view.isAttachedToWindow) {
            runCatching {
                view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }
    }
}
