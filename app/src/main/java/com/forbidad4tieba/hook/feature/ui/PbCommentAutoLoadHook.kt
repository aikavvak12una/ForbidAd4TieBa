package com.forbidad4tieba.hook.feature.ui

import android.os.SystemClock
import com.forbidad4tieba.hook.symbol.model.PbCommentAutoLoadSymbols
import com.forbidad4tieba.hook.symbol.model.PbCommentBottomListSymbols
import com.forbidad4tieba.hook.symbol.model.PbCommentBottomRecyclerSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object PbCommentAutoLoadHook {
    private const val PRELOAD_NOT_SEE_COMMENT_NUM = 20
    private const val MIN_TRIGGER_INTERVAL_MS = 1000L
    private const val GET_ITEM_COUNT = "getItemCount"
    private const val TAG = "[PbCommentAutoLoadHook]"

    @Volatile private var hooked = false
    @Volatile private var runtimeDisabled = false
    private val triggerStates = Collections.synchronizedMap(WeakHashMap<Any, TriggerState>())
    private val adapterItemCountMethods = ConcurrentHashMap<Class<*>, Method>()

    internal fun hook(targets: PbCommentAutoLoadSymbols) {
        if (!ConfigManager.isAutoLoadMoreEnabled) {
            XposedCompat.log("$TAG skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        var installedCount = 0
        targets.listTargets?.let { installedCount += installBdListViewHook(mod, it) }
        targets.recyclerTargets?.let { installedCount += installBdRecyclerViewHook(mod, it) }

        if (installedCount > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$installedCount, threshold=$PRELOAD_NOT_SEE_COMMENT_NUM")
        } else {
            resetHooked()
            XposedCompat.log("$TAG skipped: bottom mechanism symbol missing")
        }
    }

    private fun installBdListViewHook(
        mod: io.github.libxposed.api.XposedModule,
        targets: PbCommentBottomListSymbols,
    ): Int {
        return try {
            mod.hook(targets.scrollMethod).intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isAutoLoadMoreEnabled || runtimeDisabled) {
                    return@intercept result
                }
                val argList = chain.args.getOrNull(0)
                val list = if (targets.listClass.isInstance(argList)) {
                    argList
                } else {
                    targets.ownerField.get(chain.thisObject)
                } ?: return@intercept result
                val firstVisible = chain.args.getOrNull(1) as? Int ?: return@intercept result
                val visibleCount = chain.args.getOrNull(2) as? Int ?: return@intercept result
                val totalCount = chain.args.getOrNull(3) as? Int ?: return@intercept result
                maybeTriggerLoadMore(
                    list = list,
                    firstVisible = firstVisible,
                    visibleCount = visibleCount,
                    totalCount = totalCount,
                    bottomListenerField = targets.bottomListenerField,
                    bottomMethod = targets.bottomMethod,
                )
                result
            }
            XposedCompat.logD { "$TAG BdListView hook INSTALLED: ${targets.scrollMethod.declaringClass.name}.${targets.scrollMethod.name}" }
            1
        } catch (t: Throwable) {
            XposedCompat.log("$TAG BdListView install FAILED: ${t.message}")
            XposedCompat.log(t)
            0
        }
    }

    private fun installBdRecyclerViewHook(
        mod: io.github.libxposed.api.XposedModule,
        targets: PbCommentBottomRecyclerSymbols,
    ): Int {
        return try {
            mod.hook(targets.scrollMethod).intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isAutoLoadMoreEnabled || runtimeDisabled) {
                    return@intercept result
                }
                val dy = chain.args.getOrNull(2) as? Int ?: return@intercept result
                if (dy <= 0) return@intercept result
                val argRecycler = chain.args.getOrNull(0)
                val recycler = if (targets.recyclerClass.isInstance(argRecycler)) {
                    argRecycler
                } else {
                    targets.ownerField.get(chain.thisObject)
                } ?: return@intercept result
                val firstVisible = targets.firstVisibleMethod.invoke(recycler) as? Int ?: return@intercept result
                val lastVisible = targets.lastVisibleMethod.invoke(recycler) as? Int ?: return@intercept result
                val totalCount = getRecyclerAdapterItemCount(recycler, targets.getAdapterMethod) ?: return@intercept result
                val visibleCount = lastVisible - firstVisible + 1
                maybeTriggerLoadMore(
                    list = recycler,
                    firstVisible = firstVisible,
                    visibleCount = visibleCount,
                    totalCount = totalCount,
                    bottomListenerField = targets.bottomListenerField,
                    bottomMethod = targets.bottomMethod,
                )
                result
            }
            XposedCompat.logD { "$TAG BdRecyclerView hook INSTALLED: ${targets.scrollMethod.declaringClass.name}.${targets.scrollMethod.name}" }
            1
        } catch (t: Throwable) {
            XposedCompat.log("$TAG BdRecyclerView install FAILED: ${t.message}")
            XposedCompat.log(t)
            0
        }
    }

    private fun maybeTriggerLoadMore(
        list: Any,
        firstVisible: Int,
        visibleCount: Int,
        totalCount: Int,
        bottomListenerField: Field,
        bottomMethod: Method,
    ) {
        if (firstVisible < 0 || visibleCount <= 0 || totalCount <= 0) return
        val remaining = totalCount - (firstVisible + visibleCount)
        if (remaining <= 0 || remaining > PRELOAD_NOT_SEE_COMMENT_NUM) return

        try {
            val bottomListener = bottomListenerField.get(list) ?: return
            if (!isPbCommentBottomListener(bottomListener)) return
            val now = SystemClock.uptimeMillis()
            val state = stateFor(list)
            synchronized(state) {
                if (state.lastTriggeredTotalCount == totalCount) return
                if (now - state.lastTriggeredAt < MIN_TRIGGER_INTERVAL_MS) return
                state.lastTriggeredTotalCount = totalCount
                state.lastTriggeredAt = now
            }
            bottomMethod.invoke(bottomListener)
        } catch (t: Throwable) {
            runtimeDisabled = true
            XposedCompat.log("$TAG trigger FAILED, disabled for this process: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun isPbCommentBottomListener(listener: Any): Boolean {
        val clazz = listener.javaClass
        val pbFragmentInnerPrefix = StableTiebaHookPoints.PB_FRAGMENT_CLASS + "$"
        return clazz.name.startsWith(pbFragmentInnerPrefix) ||
            clazz.enclosingClass?.name == StableTiebaHookPoints.PB_FRAGMENT_CLASS
    }

    private fun stateFor(list: Any): TriggerState {
        synchronized(triggerStates) {
            return triggerStates.getOrPut(list) { TriggerState() }
        }
    }

    private fun getRecyclerAdapterItemCount(recycler: Any, getAdapterMethod: Method): Int? {
        val adapter = getAdapterMethod.invoke(recycler) ?: return null
        val itemCountMethod = adapterItemCountMethods.getOrPut(adapter.javaClass) {
            resolveNoArgIntMethod(adapter.javaClass, GET_ITEM_COUNT) ?: return null
        }
        return itemCountMethod.invoke(adapter) as? Int
    }

    private fun resolveNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == methodName &&
                    it.parameterTypes.isEmpty()
            }
            if (method != null) return method.apply { isAccessible = true }
            current = current.superclass
        }
        return null
    }

    private fun resolveNoArgIntMethod(clazz: Class<*>, methodName: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == methodName &&
                    it.returnType == Int::class.javaPrimitiveType &&
                    it.parameterTypes.isEmpty()
            }
            if (method != null) return method.apply { isAccessible = true }
            current = current.superclass
        }
        return null
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
