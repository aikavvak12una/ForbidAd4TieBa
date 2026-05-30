package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.SearchBoxTextAdSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.util.Collections
import java.util.WeakHashMap

/**
 * 灞忚斀棣栭〉涓€у寲鎼滅储妗嗛噷鐨勮疆鎾枃瀛楀箍鍛娿€? *
 * 澶勭悊鏂瑰紡锛? * 1. 鎷︽埅 TbSearchBoxView.setHintTextDataList(List, boolean)銆? * 2. 鍦ㄥご閮ㄥ垵濮嬪寲鏃舵爣璁伴椤典釜鎬у寲鎼滅储妗嗐€? * 3. 鎶?hint 鍒楄〃鏇挎崲涓虹┖鍒楄〃銆? */
object SearchBoxTextAdHook {
    @Volatile
    private var hooked = false
    @Volatile
    private var hooking = false
    private val homeSearchBoxes = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    internal fun hook(targets: SearchBoxTextAdSymbols) {
        if (!tryBeginHook()) return

        var success = false
        try {
            val mod = XposedCompat.module ?: return

            installHomeSearchBoxMarker(mod, targets)
            mod.hook(targets.setHintMethod).intercept { chain ->
                if (!ConfigManager.isAdBlockEnabled) return@intercept chain.proceed()

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
                    "${targets.setHintMethod.declaringClass.name}.${targets.setHintMethod.name} " +
                    "owner=${targets.ownerInitMethod.declaringClass.name}.${targets.ownerInitMethod.name}",
            )
        } catch (t: Throwable) {
            XposedCompat.log("[SearchBoxTextAdHook] hook FAILED: ${t.message}")
            XposedCompat.log(t)
        } finally {
            finishHook(success)
        }
    }

    private fun installHomeSearchBoxMarker(
        mod: io.github.libxposed.api.XposedModule,
        targets: SearchBoxTextAdSymbols,
    ) {
        mod.hook(targets.ownerInitMethod).intercept { chain ->
            val result = chain.proceed()
            val searchBox = runCatching { targets.ownerGetterMethod.invoke(chain.thisObject) }.getOrNull()
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
