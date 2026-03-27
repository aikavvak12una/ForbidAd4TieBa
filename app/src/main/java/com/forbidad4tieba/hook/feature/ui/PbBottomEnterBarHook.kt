package com.forbidad4tieba.hook.feature.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.Constants
import com.forbidad4tieba.hook.utils.ViewExt
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object PbBottomEnterBarHook {
    fun hook(cl: ClassLoader) {
        try {
            val targetClass = "com.baidu.tieba.pb.pb.main.underlayer.PbBottomEnterBarView"
            
            val squashCallback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!ConfigManager.isHidePbBottomEnterBarEnabled) return
                    val view = param.thisObject as? View ?: return
                    
                    // Squash immediately
                    ViewExt.squashView(view)
                    
                    // Hook onto its lifecycle and keep it hidden if the tieba code tries to unhide it
                    if (view.getTag(ViewExt.TAG_EMPTY_VIEW) != true) {
                        view.setTag(ViewExt.TAG_EMPTY_VIEW, true)
                        
                        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                ViewExt.squashView(v)
                            }
                            override fun onViewDetachedFromWindow(v: View) {}
                        })
                        
                        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                            if (v.visibility != View.GONE || v.height > 0) {
                                ViewExt.squashView(v)
                            }
                        }
                    }
                }
            }

            // Hook XML constructor
            XposedHelpers.findAndHookConstructor(
                targetClass, cl,
                Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType,
                squashCallback
            )
            
            // Hook direct constructor
            XposedHelpers.findAndHookConstructor(
                targetClass, cl,
                Context::class.java, AttributeSet::class.java,
                squashCallback
            )
            
            // Hook context-only constructor
            XposedHelpers.findAndHookConstructor(
                targetClass, cl,
                Context::class.java,
                squashCallback
            )

            XposedBridge.log("${Constants.TAG}: PbBottomEnterBar view interception installed")
        } catch (t: Throwable) {
            XposedBridge.log("${Constants.TAG}: Failed to hook PbBottomEnterBarView: ${t.message}")
        }
    }
}
