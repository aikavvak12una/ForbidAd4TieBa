package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * 屏蔽首页个性化搜索框里的轮播文字广告。
 *
 * 处理方式：
 * 1. 拦截 TbSearchBoxView.setHintTextDataList(List, boolean)。
 * 2. 在头部初始化时标记首页个性化搜索框。
 * 3. 把 hint 列表替换为空列表。
 */
object SearchBoxTextAdHook {
    private const val HEADER_CONTAINER_CLASS = "androidx.coordinatorlayout.widget.CoordinatorLayout"

    private data class RuntimeTargets(
        val searchBoxClass: String,
        val setHintMethod: String,
        val ownerClass: String,
        val ownerInitMethod: String,
        val ownerGetterMethod: String,
    )

    private data class OwnerMethods(
        val initMethod: Method,
        val getterMethod: Method,
    )

    @Volatile
    private var hooked = false
    @Volatile
    private var hooking = false
    private val homeSearchBoxes = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        if (!tryBeginHook()) return

        var success = false
        try {
            val mod = XposedCompat.module ?: return
            val targets = resolveTargets(symbols)
            if (targets == null) {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] skipped: scan symbols missing " +
                        "(searchBoxViewClass/searchBoxSetHintMethod/homeSearchBoxOwnerClass/" +
                        "homeSearchBoxInitMethod/homeSearchBoxGetterMethod)"
                )
                return
            }
            val method = resolveSetHintMethod(cl, targets)
            if (method == null) {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] method NOT FOUND: " +
                        "${targets.searchBoxClass}.${targets.setHintMethod}(List, boolean)",
                )
                return
            }
            val ownerMethods = resolveOwnerMethods(cl, targets)
            if (ownerMethods == null) {
                XposedCompat.log(
                    "[SearchBoxTextAdHook] owner methods NOT FOUND: " +
                        "${targets.ownerClass}.{${targets.ownerInitMethod},${targets.ownerGetterMethod}}",
                )
                return
            }

            installHomeSearchBoxMarker(mod, ownerMethods)
            mod.hook(method).intercept { chain ->
                if (!ConfigManager.shouldStabilizeHomeChrome()) return@intercept chain.proceed()

                if (!isHomeSearchBox(chain.thisObject)) return@intercept chain.proceed()

                val originalSize = (chain.args.getOrNull(0) as? List<*>)?.size ?: 0
                val result = chain.proceed(arrayOf<Any?>(emptyList<String>(), false))

                if (originalSize > 0) {
                    XposedCompat.logD {
                        "[SearchBoxTextAdHook] blocked home search hint list: $originalSize -> 0"
                    }
                }
                result
            }
            success = true
            XposedCompat.log(
                "[SearchBoxTextAdHook] hook INSTALLED: " +
                    "${method.declaringClass.name}.${method.name} " +
                    "owner=${ownerMethods.initMethod.declaringClass.name}.${ownerMethods.initMethod.name}",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[SearchBoxTextAdHook] hook FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            finishHook(success)
        }
    }

    private fun resolveTargets(symbols: HookSymbols?): RuntimeTargets? {
        val scanSymbols = symbols ?: return null
        val symbolClass = scanSymbols.searchBoxViewClass?.takeIf { it.isNotBlank() } ?: return null
        val symbolMethod = scanSymbols.searchBoxSetHintMethod?.takeIf { it.isNotBlank() } ?: return null
        val ownerClass = scanSymbols.homeSearchBoxOwnerClass?.takeIf { it.isNotBlank() } ?: return null
        val ownerInitMethod = scanSymbols.homeSearchBoxInitMethod?.takeIf { it.isNotBlank() } ?: return null
        val ownerGetterMethod = scanSymbols.homeSearchBoxGetterMethod?.takeIf { it.isNotBlank() } ?: return null

        return RuntimeTargets(
            searchBoxClass = symbolClass,
            setHintMethod = symbolMethod,
            ownerClass = ownerClass,
            ownerInitMethod = ownerInitMethod,
            ownerGetterMethod = ownerGetterMethod,
        )
    }

    private fun resolveSetHintMethod(cl: ClassLoader, targets: RuntimeTargets): java.lang.reflect.Method? {
        return XposedCompat.findMethodOrNull(
            targets.searchBoxClass,
            cl,
            targets.setHintMethod,
            List::class.java,
            Boolean::class.javaPrimitiveType!!,
        )
    }

    private fun resolveOwnerMethods(cl: ClassLoader, targets: RuntimeTargets): OwnerMethods? {
        val ownerClass = XposedCompat.findClassOrNull(targets.ownerClass, cl) ?: return null
        val searchBoxClass = XposedCompat.findClassOrNull(targets.searchBoxClass, cl) ?: return null
        val recyclerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.BD_TYPE_RECYCLER_VIEW_CLASS, cl)
            ?: return null
        val containerClass = XposedCompat.findClassOrNull(HEADER_CONTAINER_CLASS, cl) ?: return null

        val initMethod = XposedCompat.findMethodOrNull(
            ownerClass,
            targets.ownerInitMethod,
            recyclerClass,
            containerClass,
        ) ?: return null
        if (Modifier.isStatic(initMethod.modifiers) || initMethod.returnType != Void.TYPE) return null

        val getterMethod = XposedCompat.findMethodOrNull(ownerClass, targets.ownerGetterMethod) ?: return null
        if (
            Modifier.isStatic(getterMethod.modifiers) ||
            getterMethod.parameterTypes.isNotEmpty() ||
            !searchBoxClass.isAssignableFrom(getterMethod.returnType)
        ) {
            return null
        }

        initMethod.isAccessible = true
        getterMethod.isAccessible = true
        return OwnerMethods(initMethod, getterMethod)
    }

    private fun installHomeSearchBoxMarker(
        mod: io.github.libxposed.api.XposedModule,
        ownerMethods: OwnerMethods,
    ) {
        mod.hook(ownerMethods.initMethod).intercept { chain ->
            val result = chain.proceed()
            val searchBox = runCatching { ownerMethods.getterMethod.invoke(chain.thisObject) }.getOrNull()
            rememberHomeSearchBox(searchBox)
            result
        }
    }

    private fun rememberHomeSearchBox(searchBox: Any?) {
        if (searchBox != null) homeSearchBoxes[searchBox] = true
    }

    private fun isHomeSearchBox(searchBox: Any?): Boolean {
        return searchBox != null && homeSearchBoxes.containsKey(searchBox)
    }

    private fun tryBeginHook(): Boolean {
        synchronized(this) {
            if (hooked || hooking) return false
            hooking = true
            return true
        }
    }

    private fun finishHook(success: Boolean) {
        synchronized(this) {
            hooking = false
            if (success) hooked = true
        }
    }
}
