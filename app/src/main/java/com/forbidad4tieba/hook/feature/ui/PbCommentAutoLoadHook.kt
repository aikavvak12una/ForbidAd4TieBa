package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 复用目标应用的底部加载回调，预加载下一页 PB 评论。
 */
object PbCommentAutoLoadHook {
    private const val SMALL_LIST_PRELOAD_REMAINING_ITEMS = 5
    private const val MEDIUM_LIST_PRELOAD_REMAINING_ITEMS = 10
    private const val LARGE_LIST_PRELOAD_REMAINING_ITEMS = 20
    private const val MEDIUM_LIST_TOTAL_COUNT = 40
    private const val LARGE_LIST_TOTAL_COUNT = 80
    private const val MIN_TRIGGER_INTERVAL_MS = 1500L

    @Volatile private var hooked = false
    @Volatile private var lastFragmentRef: WeakReference<Any>? = null
    private var lastFragmentState = ScrollState()

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
        if (
            listenerClassName == null ||
            scrollMethodName == null ||
            fragmentFieldName == null ||
            bottomListenerFieldName == null
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
            val bottomListenerField = XposedCompat.findField(fragmentField.type, bottomListenerFieldName)
            val bottomListenerMethod = XposedCompat.findMethodOrNull(
                StableTiebaHookPoints.BD_LIST_VIEW_SCROLL_TO_BOTTOM_LISTENER_CLASS,
                cl,
                StableTiebaHookPoints.METHOD_ON_SCROLL_TO_BOTTOM,
            )
            if (bottomListenerMethod == null) {
                resetHooked()
                XposedCompat.log(
                    "[PbCommentAutoLoadHook] method NOT FOUND: " +
                        "${StableTiebaHookPoints.BD_LIST_VIEW_SCROLL_TO_BOTTOM_LISTENER_CLASS}." +
                        StableTiebaHookPoints.METHOD_ON_SCROLL_TO_BOTTOM,
                )
                return
            }

            mod.hook(scrollMethod).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.isAutoLoadMoreEnabled) {
                    maybeTrigger(
                        listener = chain.thisObject,
                        firstVisible = chain.args.getOrNull(1) as? Int,
                        visibleCount = chain.args.getOrNull(2) as? Int,
                        totalCount = chain.args.getOrNull(3) as? Int,
                        fragmentField = fragmentField,
                        bottomListenerField = bottomListenerField,
                        bottomListenerMethod = bottomListenerMethod,
                    )
                }
                result
            }
            XposedCompat.log(
                "[PbCommentAutoLoadHook] hook INSTALLED: " +
                    "$listenerClassName.${scrollMethod.name}, thresholds=" +
                    "$SMALL_LIST_PRELOAD_REMAINING_ITEMS/" +
                    "$MEDIUM_LIST_PRELOAD_REMAINING_ITEMS/" +
                    LARGE_LIST_PRELOAD_REMAINING_ITEMS,
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbCommentAutoLoadHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun maybeTrigger(
        listener: Any?,
        firstVisible: Int?,
        visibleCount: Int?,
        totalCount: Int?,
        fragmentField: Field,
        bottomListenerField: Field,
        bottomListenerMethod: Method,
    ) {
        if (listener == null || firstVisible == null || visibleCount == null || totalCount == null) return
        if (visibleCount <= 0 || totalCount <= visibleCount) return

        val remaining = totalCount - (firstVisible + visibleCount)
        if (remaining > preloadRemainingThreshold(totalCount)) return

        val fragment = runCatching { fragmentField.get(listener) }.getOrNull() ?: return
        if (!shouldTrigger(fragment, firstVisible, totalCount)) return

        val bottomListener = runCatching { bottomListenerField.get(fragment) }.getOrNull() ?: return
        runCatching {
            bottomListenerMethod.invoke(bottomListener)
        }.onFailure {
            XposedCompat.logD { "[PbCommentAutoLoadHook] trigger failed: ${it.message}" }
        }
    }

    private fun shouldTrigger(fragment: Any, firstVisible: Int, totalCount: Int): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        val state = stateFor(fragment)
        val scrollingDown = state.lastFirstVisible < 0 || firstVisible >= state.lastFirstVisible
        state.lastFirstVisible = firstVisible
        if (!scrollingDown) return false
        if (state.lastTriggeredTotalCount == totalCount) return false
        if (now - state.lastTriggerAt < MIN_TRIGGER_INTERVAL_MS) return false
        state.lastTriggeredTotalCount = totalCount
        state.lastTriggerAt = now
        return true
    }

    private fun stateFor(fragment: Any): ScrollState {
        if (lastFragmentRef?.get() === fragment) return lastFragmentState
        val state = ScrollState()
        lastFragmentState = state
        lastFragmentRef = WeakReference(fragment)
        return state
    }

    private fun preloadRemainingThreshold(totalCount: Int): Int {
        return when {
            totalCount < MEDIUM_LIST_TOTAL_COUNT -> SMALL_LIST_PRELOAD_REMAINING_ITEMS
            totalCount < LARGE_LIST_TOTAL_COUNT -> MEDIUM_LIST_PRELOAD_REMAINING_ITEMS
            else -> LARGE_LIST_PRELOAD_REMAINING_ITEMS
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

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private data class ScrollState(
        var lastFirstVisible: Int = -1,
        var lastTriggeredTotalCount: Int = -1,
        var lastTriggerAt: Long = 0L,
    )
}
