package com.forbidad4tieba.hook.feature.ui

import android.os.SystemClock
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

object PbCommentAutoLoadHook {
    private const val PRELOAD_NOT_SEE_COMMENT_NUM = 20
    private const val MIN_TRIGGER_INTERVAL_MS = 1000L

    @Volatile private var hooked = false
    @Volatile private var runtimeDisabled = false
    private val triggerStates = Collections.synchronizedMap(WeakHashMap<Any, TriggerState>())

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        if (!ConfigManager.isAutoLoadMoreEnabled) {
            XposedCompat.log("[PbCommentAutoLoadHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        val listenerClassName = symbols.pbCommentScrollListenerClass?.takeIf { it.isNotBlank() }
        val scrollMethodName = symbols.pbCommentScrollMethod?.takeIf { it.isNotBlank() }
        val fragmentFieldName = symbols.pbCommentScrollFragmentField?.takeIf { it.isNotBlank() }
        val bottomListenerFieldName = symbols.pbCommentScrollBottomListenerField?.takeIf { it.isNotBlank() }
        val bottomMethodName = symbols.pbCommentScrollBottomMethod?.takeIf { it.isNotBlank() }
        if (
            listenerClassName == null ||
            scrollMethodName == null ||
            fragmentFieldName == null ||
            bottomListenerFieldName == null ||
            bottomMethodName == null
        ) {
            resetHooked()
            XposedCompat.log("[PbCommentAutoLoadHook] skipped: scan symbol missing")
            return
        }

        try {
            val listenerClass = XposedCompat.findClassOrNull(listenerClassName, cl)
            if (listenerClass == null) {
                resetHooked()
                XposedCompat.log("[PbCommentAutoLoadHook] class NOT FOUND: $listenerClassName")
                return
            }
            val scrollMethod = resolveScrollMethod(listenerClass, scrollMethodName)
            if (scrollMethod == null) {
                resetHooked()
                XposedCompat.log("[PbCommentAutoLoadHook] method NOT FOUND: $listenerClassName.$scrollMethodName")
                return
            }
            val fragmentField = XposedCompat.findField(listenerClass, fragmentFieldName)
            if (fragmentField.type.name != StableTiebaHookPoints.PB_FRAGMENT_CLASS) {
                resetHooked()
                XposedCompat.log(
                    "[PbCommentAutoLoadHook] field type mismatch: " +
                        "$listenerClassName.$fragmentFieldName=${fragmentField.type.name}",
                )
                return
            }
            val bottomListenerField = XposedCompat.findField(fragmentField.type, bottomListenerFieldName)
            val bottomMethod = resolveBottomMethod(bottomListenerField.type, bottomMethodName)
            if (bottomMethod == null) {
                resetHooked()
                XposedCompat.log(
                    "[PbCommentAutoLoadHook] bottom method NOT FOUND: " +
                        "${bottomListenerField.type.name}.$bottomMethodName",
                )
                return
            }

            mod.hook(scrollMethod).intercept { chain ->
                if (!ConfigManager.isAutoLoadMoreEnabled || runtimeDisabled) {
                    return@intercept chain.proceed()
                }
                val listener = chain.thisObject ?: return@intercept chain.proceed()
                val firstVisible = chain.args.getOrNull(1) as? Int ?: return@intercept chain.proceed()
                val visibleCount = chain.args.getOrNull(2) as? Int ?: return@intercept chain.proceed()
                val totalCount = chain.args.getOrNull(3) as? Int ?: return@intercept chain.proceed()

                val result = chain.proceed()
                maybeTriggerLoadMore(
                    listener = listener,
                    firstVisible = firstVisible,
                    visibleCount = visibleCount,
                    totalCount = totalCount,
                    fragmentField = fragmentField,
                    bottomListenerField = bottomListenerField,
                    bottomMethod = bottomMethod,
                )
                result
            }
            XposedCompat.log(
                "[PbCommentAutoLoadHook] hook INSTALLED: " +
                    "${ReflectionUtils.methodSignature(scrollMethod)}, threshold=$PRELOAD_NOT_SEE_COMMENT_NUM",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbCommentAutoLoadHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun maybeTriggerLoadMore(
        listener: Any,
        firstVisible: Int,
        visibleCount: Int,
        totalCount: Int,
        fragmentField: Field,
        bottomListenerField: Field,
        bottomMethod: Method,
    ) {
        if (firstVisible < 0 || visibleCount <= 0 || totalCount <= 0) return
        val remaining = totalCount - (firstVisible + visibleCount)
        if (remaining > PRELOAD_NOT_SEE_COMMENT_NUM) return

        try {
            val fragment = fragmentField.get(listener) ?: return
            val bottomListener = bottomListenerField.get(fragment) ?: return
            val now = SystemClock.uptimeMillis()
            val state = stateFor(listener)
            synchronized(state) {
                if (state.lastTriggeredTotalCount == totalCount) return
                if (now - state.lastTriggeredAt < MIN_TRIGGER_INTERVAL_MS) return
                state.lastTriggeredTotalCount = totalCount
                state.lastTriggeredAt = now
            }
            bottomMethod.invoke(bottomListener)
        } catch (t: Throwable) {
            runtimeDisabled = true
            XposedCompat.log("[PbCommentAutoLoadHook] trigger FAILED, disabled for this process: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun stateFor(listener: Any): TriggerState {
        synchronized(triggerStates) {
            return triggerStates.getOrPut(listener) { TriggerState() }
        }
    }

    private fun resolveScrollMethod(clazz: Class<*>, methodName: String): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true }
    }

    private fun resolveBottomMethod(clazz: Class<*>, methodName: String): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == methodName &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        }?.apply { isAccessible = true }
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            runtimeDisabled = false
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private class TriggerState(
        var lastTriggeredTotalCount: Int = -1,
        var lastTriggeredAt: Long = 0L,
    )
}
