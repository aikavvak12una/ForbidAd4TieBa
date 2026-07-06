package com.forbidad4tieba.hook.feature.perf

import android.view.View
import com.forbidad4tieba.hook.symbol.model.AiComponentSymbols
import com.forbidad4tieba.hook.symbol.model.AiImageViewerJumpButtonSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ViewExt
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object AiComponentDisableHook {
    private const val TAG_AI_EMOJI_CREATION_DISABLED = 0x7E000010.toInt()

    private val installed = AtomicBoolean(false)
    private val imageViewerInstalled = AtomicBoolean(false)

    internal fun hook(targets: AiComponentSymbols) {
        if (!ConfigManager.isAiComponentsDisabled) {
            XposedCompat.log("[AiComponentDisableHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[AiComponentDisableHook] already installed, skip")
            return
        }

        try {
            mod.hook(targets.spriteMemeEnableMethod).intercept { false }
            mod.hook(targets.pbInitAiWriteMethod).intercept { null }
            val pbAiEmojiCreationHooks = installPbAiEmojiCreationHooks(mod, targets)

            XposedCompat.log(
                "[AiComponentDisableHook] hooks INSTALLED: " +
                    "sprite=${targets.spriteMemeEnableMethod.declaringClass.name}.${targets.spriteMemeEnableMethod.name}, " +
                    "pbAiWrite=${targets.pbInitAiWriteMethod.declaringClass.name}.${targets.pbInitAiWriteMethod.name}, " +
                    "pbSpriteInitKept=${targets.pbInitSpriteMemeMethod.declaringClass.name}.${targets.pbInitSpriteMemeMethod.name}, " +
                    "pbAiEmoji=$pbAiEmojiCreationHooks",
            )
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[AiComponentDisableHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun hookImageViewerJumpButton(targets: AiImageViewerJumpButtonSymbols) {
        if (!ConfigManager.isAiComponentsDisabled) {
            XposedCompat.log("[AiComponentDisableHook] image viewer skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return

        try {
            if (!installImageViewerJumpButtonHook(mod, targets.initMethod)) {
                XposedCompat.logD("[AiComponentDisableHook] image viewer already installed, skip")
                return
            }
            XposedCompat.log(
                "[AiComponentDisableHook] image viewer hook INSTALLED: " +
                    "${targets.initMethod.declaringClass.name}.${targets.initMethod.name}",
            )
        } catch (t: Throwable) {
            imageViewerInstalled.set(false)
            XposedCompat.log("[AiComponentDisableHook] image viewer FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installImageViewerJumpButtonHook(
        mod: com.forbidad4tieba.hook.core.Api102ModuleFacade,
        method: Method?,
    ): Boolean {
        if (method == null) return false
        if (!imageViewerInstalled.compareAndSet(false, true)) return false
        try {
            mod.hook(method).intercept { null }
        } catch (t: Throwable) {
            imageViewerInstalled.set(false)
            throw t
        }
        return true
    }

    private fun installPbAiEmojiCreationHooks(
        mod: com.forbidad4tieba.hook.core.Api102ModuleFacade,
        targets: AiComponentSymbols,
    ): Int {
        var installed = 0
        val viewClass = targets.pbAiEmojiCreationViewBindMethod?.declaringClass
        if (viewClass != null) {
            for (ctor in viewClass.declaredConstructors) {
                ctor.isAccessible = true
                mod.hook(ctor).intercept { chain ->
                    val result = chain.proceed()
                    disableAiEmojiCreationView(chain.thisObject)
                    result
                }
                installed++
            }
        }
        targets.pbAiEmojiCreationViewBindMethod?.let { method ->
            mod.hook(method).intercept { chain ->
                disableAiEmojiCreationView(chain.thisObject)
                null
            }
            installed++
        }
        targets.pbPageBrowserAiEmojiCreationBindMethod?.let { method ->
            mod.hook(method).intercept { chain ->
                disableAiEmojiCreationView(chain.thisObject)
                null
            }
            installed++
        }
        return installed
    }

    private fun disableAiEmojiCreationView(target: Any?) {
        val view = target as? View ?: return
        view.setOnClickListener(null)
        view.isClickable = false
        view.isEnabled = false
        if (view.getTag(TAG_AI_EMOJI_CREATION_DISABLED) != true) {
            view.setTag(TAG_AI_EMOJI_CREATION_DISABLED, true)
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    disableAiEmojiCreationView(v)
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.visibility != View.GONE || v.width > 0 || v.height > 0) {
                    disableAiEmojiCreationView(v)
                }
            }
        }
        ViewExt.squashView(view)
    }
}
