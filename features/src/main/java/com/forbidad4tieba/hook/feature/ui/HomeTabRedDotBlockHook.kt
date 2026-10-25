package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.contract.RedDotContract
import com.forbidad4tieba.hook.symbol.contract.RedDotLayer
import com.forbidad4tieba.hook.symbol.contract.RedDotTargets
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

object HomeTabRedDotBlockHook {
    fun hook(cl: ClassLoader): InstallOutcome? {
        val api = XposedCompat.module ?: return null
        if (!isRuntimeEnabled()) return null
        val context = ConfigManager.getAppContext()
        return install(
            RedDotContract.resolve(cl, context?.resources, context?.packageName), api,
        )
    }

    internal fun install(
        targets: RedDotTargets,
        api: XposedInterface,
        enabled: () -> Boolean = ::isRuntimeEnabled,
        mask: (View, RedDotLayer, () -> Boolean) -> Unit = ::maskPlainRedDot,
    ): InstallOutcome? {
        val issues = ArrayList(targets.issues)
        val callbackError = AtomicBoolean(false)
        fun failure(message: String, error: Throwable) {
            if (callbackError.compareAndSet(false, true)) {
                XposedCompat.logW("[HomeTabRedDotBlockHook] " + message + ": " + error.javaClass.simpleName)
            }
        }
        fun point(name: String, method: Method, callback: (XposedInterface.Chain) -> Any?): XposedInterface.HookHandle? =
            try {
                RuntimeHooks.builder(api, method, "HomeTabRedDotBlockHook", name).intercept { callback(it) }
            } catch (error: Throwable) {
                issues.add(name + ": " + error.javaClass.simpleName + ": " + error.message)
                null
            }

        targets.visibility.forEach { method ->
            point("visibility", method) { chain ->
                if (enabled() && chain.args.firstOrNull() == true) chain.proceed(arrayOf(false))
                else chain.proceed()
            }
        }
        targets.message?.let { message ->
            point("message.draw", message.draw) { chain ->
                if (!enabled() || chain.thisObject == null) return@point chain.proceed()
                val plain = try {
                    isPlainRedDot(message.text.invoke(chain.thisObject) as? String)
                } catch (error: Throwable) {
                    failure("badge label unavailable", error)
                    false
                }
                // Read the content that will actually be painted, after every host update.
                if (plain) null else chain.proceed()
            }
        }
        if (targets.drawableIds.isNotEmpty()) targets.resources.forEach { resource ->
            point("resource." + resource.layer.name, resource.method) { chain ->
                val result = chain.proceed()
                val resourceId = chain.args.getOrNull(1) as? Int
                if (enabled() && resourceId != null && resourceId in targets.drawableIds) {
                    (chain.args.firstOrNull() as? View)?.let { view ->
                        try {
                            mask(view, resource.layer, enabled)
                        } catch (error: Throwable) {
                            failure("resource mask unavailable", error)
                        }
                    }
                }
                result
            }
        }

        targets.navigation?.let { navigation ->
            val dots = Collections.synchronizedMap(WeakHashMap<Any, NavigationMask?>())
            val create = point("navigation.create", navigation.create) { chain ->
                val holder = chain.proceed()
                if (holder != null && enabled()) {
                    try {
                        val found = navigation.findDot(holder)?.let(::NavigationMask)
                        dots[holder] = found
                        found?.apply(true)
                    } catch (error: Throwable) {
                        failure("navigation dot unavailable", error)
                    }
                }
                holder
            }
            if (create != null) {
                val bind = point("navigation.bind", navigation.bind) { chain ->
                    val result = chain.proceed()
                    chain.args.firstOrNull()?.let { holder -> dots[holder]?.apply(enabled()) }
                    result
                }
                if (bind == null) {
                    try {
                        create.unhook()
                    } catch (error: Throwable) {
                        issues.add("navigation rollback: " + error.javaClass.simpleName)
                    }
                }
            }
        }

        return if (issues.isEmpty()) null else {
            val reason = issues.joinToString("; ")
            XposedCompat.logW("[HomeTabRedDotBlockHook] partial targets: " + reason)
            InstallOutcome(InstallState.PARTIAL, reason = reason)
        }
    }

    /** A confirmed, separate circle view; the numeric unread TextView is never changed. */
    private class NavigationMask(view: View) {
        private val target = WeakReference(view)
        private val originalAlpha = view.alpha

        fun apply(hidden: Boolean) {
            target.get()?.let { view ->
                if (hidden) {
                    if (view.alpha != 0f) view.alpha = 0f
                } else if (view.alpha == 0f) {
                    view.alpha = originalAlpha
                }
            }
        }
    }

    private fun isRuntimeEnabled() = ConfigManager.snapshot().isHomeTabRedDotHidden
}
