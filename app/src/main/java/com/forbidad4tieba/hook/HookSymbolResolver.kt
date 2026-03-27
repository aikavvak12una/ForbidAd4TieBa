package com.forbidad4tieba.hook

import android.content.Context
import android.view.View
import android.widget.Toast
import dalvik.system.DexFile
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

internal fun interface ScanLogger {
    fun log(line: String)
}

internal data class HookSymbols(
    val homeTabClass: String? = null,
    val homeTabRebuildMethod: String? = null,
    val homeTabListField: String? = null,
    val settingsClass: String? = null,
    val settingsInitMethod: String? = null,
    val settingsContainerField: String? = null,
    val source: String = "unsupported",
    val createdAt: Long = 0L,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("homeTabClass", homeTabClass)
            put("homeTabRebuildMethod", homeTabRebuildMethod)
            put("homeTabListField", homeTabListField)
            put("settingsClass", settingsClass)
            put("settingsInitMethod", settingsInitMethod)
            put("settingsContainerField", settingsContainerField)
            put("source", source)
            put("createdAt", createdAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String?): HookSymbols? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                HookSymbols(
                    homeTabClass = obj.optStringOrNull("homeTabClass"),
                    homeTabRebuildMethod = obj.optStringOrNull("homeTabRebuildMethod"),
                    homeTabListField = obj.optStringOrNull("homeTabListField"),
                    settingsClass = obj.optStringOrNull("settingsClass"),
                    settingsInitMethod = obj.optStringOrNull("settingsInitMethod"),
                    settingsContainerField = obj.optStringOrNull("settingsContainerField"),
                    source = obj.optString("source", "unsupported"),
                    createdAt = obj.optLong("createdAt", 0L),
                )
            } catch (_: Throwable) {
                null
            }
        }

        private fun JSONObject.optStringOrNull(name: String): String? {
            if (isNull(name)) return null
            val s = optString(name)
            return s.ifEmpty { null }
        }
    }
}

internal object HookSymbolResolver {
    private const val TAG = "TiebaHook.SymbolResolver"
    private const val PREFS_NAME = "tiebahook_settings"
    private const val KEY_SYMBOL_FP = "hook_symbol_fp_v2"
    private const val KEY_SYMBOL_JSON = "hook_symbol_json_v2"

    @Volatile
    private var sMemoryFingerprint: String? = null

    @Volatile
    private var sMemorySymbols: HookSymbols? = null

    fun getMemorySymbols(): HookSymbols? = sMemorySymbols

    fun resolve(
        context: Context,
        cl: ClassLoader,
        forceRescan: Boolean,
        showToast: Boolean,
        logger: ScanLogger? = null,
    ): HookSymbols {
        val startedAt = System.currentTimeMillis()
        val appCtx = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appCtx)
        log(logger, "resolve start, forceRescan=$forceRescan")
        log(logger, "fingerprint=$fingerprint")
        log(logger, "thread=${Thread.currentThread().name}")
        log(logger, "classLoader=${cl.javaClass.name}@${System.identityHashCode(cl)}")
        log(logger, "app=${describeAppMeta(appCtx)}")

        val memorySymbols = sMemorySymbols
        if (!forceRescan && memorySymbols != null && fingerprint == sMemoryFingerprint) {
            log(logger, "memory cache hit: source=${memorySymbols.source}")
            return memorySymbols
        }

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!forceRescan) {
            val cacheFp = prefs.getString(KEY_SYMBOL_FP, null)
            val cached = HookSymbols.fromJson(prefs.getString(KEY_SYMBOL_JSON, null))
            log(logger, "disk cache fp match=${cacheFp == fingerprint}, cached=${cached != null}")
            if (cacheFp == fingerprint && cached != null) {
                if (cached.source == "unsupported") {
                    log(logger, "disk cache unsupported, skipping scan")
                    sMemoryFingerprint = fingerprint
                    sMemorySymbols = cached
                    return cached
                }
                if (isUsable(cached, cl)) {
                    log(logger, "disk cache usable: source=${cached.source}")
                    log(logger, "cache symbols: ${describeSymbols(cached)}")
                    sMemoryFingerprint = fingerprint
                    sMemorySymbols = cached
                    return cached
                }
                log(logger, "disk cache unusable, rescan required")
            }
        }

        if (showToast) {
            toastOnMain(appCtx, "TBHook: 正在扫描符号...")
        }
        log(logger, "scan begin")

        val scanned = scan(appCtx, cl, logger)
        log(logger, "scan done: source=${scanned.source}")
        log(logger, "final symbols: ${describeSymbols(scanned)}")

        prefs.edit()
            .putString(KEY_SYMBOL_FP, fingerprint)
            .putString(KEY_SYMBOL_JSON, scanned.toJson())
            .apply()
        log(logger, "cache updated")

        sMemoryFingerprint = fingerprint
        sMemorySymbols = scanned

        if (showToast) {
            when (scanned.source) {
                "scan" -> toastOnMain(appCtx, "TBHook: 符号扫描完成")
                "partial" -> toastOnMain(appCtx, "TBHook: 部分扫描完成，部分功能已禁用")
                else -> toastOnMain(appCtx, "TBHook: 当前贴吧版本不支持，模块已休眠")
            }
        }
        log(logger, "durationMs=${System.currentTimeMillis() - startedAt}")
        return scanned
    }

    fun loadCachedIfUsable(context: Context, cl: ClassLoader, logger: ScanLogger? = null): HookSymbols? {
        val appCtx = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appCtx)
        log(logger, "loadCachedIfUsable fingerprint=$fingerprint")

        val memorySymbols = sMemorySymbols
        if (memorySymbols != null && fingerprint == sMemoryFingerprint) {
            log(logger, "memory cache candidate: source=${memorySymbols.source}")
            if (memorySymbols.source == "unsupported" || isUsable(memorySymbols, cl)) return memorySymbols
        }

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cacheFp = prefs.getString(KEY_SYMBOL_FP, null)
        val cached = HookSymbols.fromJson(prefs.getString(KEY_SYMBOL_JSON, null))
        log(logger, "disk cache candidate: fpMatch=${cacheFp == fingerprint}, exists=${cached != null}")
        if (cacheFp == fingerprint && cached != null) {
            if (cached.source == "unsupported" || isUsable(cached, cl)) {
                sMemoryFingerprint = fingerprint
                sMemorySymbols = cached
                log(logger, "disk cache usable")
                return cached
            }
        }
        log(logger, "no usable cache")
        return null
    }

    fun manualRescanAsync(context: Context, classLoader: ClassLoader?) {
        val appCtx = context.applicationContext ?: context
        val cl = classLoader ?: appCtx.classLoader
        if (cl == null) {
            toastOnMain(appCtx, "TBHook: 类加载器不可用")
            return
        }
        toastOnMain(appCtx, "TBHook: 手动扫描开始")

        thread(name = "tbhook-manual-rescan", isDaemon = true) {
            val symbols = resolve(
                context = appCtx,
                cl = cl,
                forceRescan = true,
                showToast = false,
            )
            if (symbols.source == "unsupported") {
                toastOnMain(appCtx, "TBHook: 手动扫描失败，当前版本不支持")
            } else {
                toastOnMain(appCtx, "TBHook: 手动扫描完成，重启贴吧生效")
            }
        }
    }

    private fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): HookSymbols {
        val candidates = listObfuscatedRootClasses(context, logger)
        log(logger, "candidates=${candidates.size}")
        if (candidates.isEmpty()) return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())

        val navClass = safeFindClass(NAV_CLASS, cl)
        if (navClass == null) {
            log(logger, "nav class not found, aborting")
            return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())
        }

        val settingsRules = listOf(SettingsLevel1Rule(navClass))
        val settingsMatch = runRules(candidates, cl, settingsRules, logger, "settings")
        
        if (settingsMatch == null) {
            log(logger, "settings match not found, life-line broken, aborting")
            return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())
        }

        val homeRules = listOf(HomeTabsLevel1Rule())
        val homeMatch = runRules(candidates, cl, homeRules, logger, "home")

        return HookSymbols(
            settingsClass = settingsMatch.className,
            settingsInitMethod = settingsMatch.methodName,
            settingsContainerField = settingsMatch.fieldName,
            homeTabClass = homeMatch?.className,
            homeTabRebuildMethod = homeMatch?.methodName,
            homeTabListField = homeMatch?.fieldName,
            source = if (homeMatch != null) "scan" else "partial",
            createdAt = System.currentTimeMillis(),
        )
    }

    @Suppress("DEPRECATION")
    private fun listObfuscatedRootClasses(context: Context, logger: ScanLogger?): List<String> {
        val out = ArrayList<String>(512)
        val sourceDir = context.applicationInfo?.sourceDir ?: return out
        var dexFile: DexFile? = null
        try {
            dexFile = DexFile(sourceDir)
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement()
                if (!name.startsWith("com.baidu.tieba.")) continue
                val shortName = name.substring("com.baidu.tieba.".length)
                if (shortName.contains('.')) continue
                if (!isLikelyObfuscatedShortName(shortName)) continue
                out.add(name)
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: list classes failed: ${t.message}")
            log(logger, "list classes failed: ${t.message}")
        } finally {
            try {
                dexFile?.close()
            } catch (_: Throwable) {
            }
        }
        log(logger, "obfuscated root classes listed: ${out.size}")
        return out
    }

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) return false
        }
        return true
    }

    private fun runRules(
        candidates: List<String>,
        cl: ClassLoader,
        rules: List<ScanRule>,
        logger: ScanLogger?,
        tag: String
    ): ScanMatch? {
        for (rule in rules) {
            var best: ScanMatch? = null
            var skippedByReflectError = 0
            for (className in candidates) {
                try {
                    val cls = safeFindClass(className, cl) ?: continue
                    val match = rule.match(cls, cl)
                    if (match != null) {
                        if (best == null || match.score > best.score) {
                            best = match
                        }
                    }
                } catch (_: Throwable) {
                    skippedByReflectError++
                }
            }
            if (skippedByReflectError > 0) {
                log(logger, "$tag scan rule ${rule.javaClass.simpleName} skipped classes due reflection errors=$skippedByReflectError")
            }
            if (best != null) {
                log(logger, "$tag matched by ${rule.javaClass.simpleName}: ${best.className}.${best.methodName} score=${best.score}")
                return best
            }
        }
        return null
    }

    private fun isUsable(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (!isSettingsValid(symbols, cl)) return false
        val hasHomeSymbols =
            symbols.homeTabClass != null ||
                symbols.homeTabRebuildMethod != null ||
                symbols.homeTabListField != null
        if (!hasHomeSymbols) return true
        return isHomeValid(symbols, cl)
    }

    private fun isHomeValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (symbols.homeTabClass == null || symbols.homeTabRebuildMethod == null || symbols.homeTabListField == null) return false
        return try {
            val homeClass = safeFindClass(symbols.homeTabClass, cl) ?: return false
            val methodOk = homeClass.declaredMethods.any {
                it.name == symbols.homeTabRebuildMethod &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == Void.TYPE
            }
            if (!methodOk) return false
            homeClass.declaredFields.any { it.name == symbols.homeTabListField }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isSettingsValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        if (symbols.settingsClass == null || symbols.settingsInitMethod == null || symbols.settingsContainerField == null) return false
        return try {
            val settingsClass = safeFindClass(symbols.settingsClass, cl) ?: return false
            val navClass = safeFindClass(NAV_CLASS, cl) ?: return false
            val methodOk = settingsClass.declaredMethods.any { method ->
                if (method.name != symbols.settingsInitMethod) return@any false
                if (method.returnType != Void.TYPE) return@any false
                val p = method.parameterTypes
                p.size == 2 &&
                    Context::class.java.isAssignableFrom(p[0]) &&
                    navClass.isAssignableFrom(p[1])
            }
            if (!methodOk) return false
            settingsClass.declaredFields.any { it.name == symbols.settingsContainerField }
        } catch (_: Throwable) {
            false
        }
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClassIfExists(name, cl)
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildFingerprint(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val sourceDir = pkgInfo.applicationInfo?.sourceDir
            val file = if (sourceDir.isNullOrBlank()) null else File(sourceDir)
            val size = file?.length() ?: -1L
            val modified = file?.lastModified() ?: -1L
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
            "$vCode:${pkgInfo.lastUpdateTime}:$size:$modified:${BuildConfig.VERSION_NAME}:${BuildConfig.VERSION_CODE}"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun toastOnMain(context: Context, text: String) {
        try {
            val appCtx = context.applicationContext ?: context
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                try {
                    Toast.makeText(appCtx, text, Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun log(logger: ScanLogger?, line: String) {
        try {
            XposedBridge.log("$TAG: $line")
        } catch (_: Throwable) {
        }
        try {
            logger?.log(line)
        } catch (_: Throwable) {
        }
    }

    private fun describeSymbols(symbols: HookSymbols): String {
        return "home=${symbols.homeTabClass}.${symbols.homeTabRebuildMethod}[${symbols.homeTabListField}], " +
            "settings=${symbols.settingsClass}.${symbols.settingsInitMethod}[${symbols.settingsContainerField}], " +
            "source=${symbols.source}"
    }

    private fun describeAppMeta(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "pkg=${context.packageName}, verCode=$vCode, update=${info.lastUpdateTime}"
        } catch (_: Throwable) {
            "pkg=${context.packageName}"
        }
    }
}

private const val NAV_CLASS = "com.baidu.tbadk.core.view.NavigationBar"
