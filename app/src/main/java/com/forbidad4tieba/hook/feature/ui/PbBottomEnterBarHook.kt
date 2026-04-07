package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager

import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ViewExt

object PbBottomEnterBarHook {
    private const val TAG_LAYOUT_STORM = 0x7E000012

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        try {
            val targetClass = "com.baidu.tieba.pb.pb.main.underlayer.PbBottomEnterBarView"
            val clazz = XposedCompat.findClassOrNull(targetClass, cl)
            if (clazz == null) {
                XposedCompat.log("[PbBottomEnterBarHook] class NOT FOUND: $targetClass")
                return
            }

            for (ctor in clazz.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    if (ConfigManager.isHidePbBottomEnterBarEnabled) {
                        val view = chain.thisObject as? View
                        if (view != null) {
                            ViewExt.squashView(view)
                            if (view.getTag(ViewExt.TAG_EMPTY_VIEW) != true) {
                                view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
                                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) { ViewExt.squashView(v) }
                                    override fun onViewDetachedFromWindow(v: View) {}
                                })
                                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                    if (v.visibility != View.GONE || v.height > 0) {
                                        if (v.getTag(TAG_LAYOUT_STORM) != true) {
                                            v.setTag(TAG_LAYOUT_STORM, true)
                                            ViewExt.squashViewRemembering(v)
                                            v.post { v.setTag(TAG_LAYOUT_STORM, false) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    result
                }
            }

            XposedCompat.log("[PbBottomEnterBarHook] hook INSTALLED")
        } catch (t: Throwable) {
            XposedCompat.log("[PbBottomEnterBarHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }
}
