package com.forbidad4tieba.hook

import android.content.Context
import android.widget.Toast
import dalvik.system.DexFile
import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

internal fun interface ScanLogger {
    fun log(line: String)
}

data class HookSymbols(
    val homeTabClass: String? = null,
    val homeTabRebuildMethod: String? = null,
    val homeTabListField: String? = null,
    val settingsClass: String? = null,
    val settingsInitMethod: String? = null,
    val settingsContainerField: String? = null,
    val feedTemplateKeyMethod: String? = null,
    val feedTemplateLoadMoreMethod: String? = null,
    val splashAdHelperClass: String? = null,
    val splashAdHelperMethod: String? = null,
    val closeAdDataClass: String? = null,
    val closeAdDataMethodG1: String? = null,
    val closeAdDataMethodJ1: String? = null,
    val nd7Class: String? = null,
    val nd7MethodI0: String? = null,
    val nd7MethodL: String? = null,
    val zgaClass: String? = null,
    val zgaMethods: List<String>? = null,

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
            put("feedTemplateKeyMethod", feedTemplateKeyMethod)
            put("feedTemplateLoadMoreMethod", feedTemplateLoadMoreMethod)
            put("splashAdHelperClass", splashAdHelperClass)
            put("splashAdHelperMethod", splashAdHelperMethod)
            put("closeAdDataClass", closeAdDataClass)
            put("closeAdDataMethodG1", closeAdDataMethodG1)
            put("closeAdDataMethodJ1", closeAdDataMethodJ1)
            put("nd7Class", nd7Class)
            put("nd7MethodI0", nd7MethodI0)
            put("nd7MethodL", nd7MethodL)
            put("zgaClass", zgaClass)
            if (zgaMethods != null) {
                val array = org.json.JSONArray()
                zgaMethods.forEach { array.put(it) }
                put("zgaMethods", array)
            }

            put("source", source)
            put("createdAt", createdAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String?): HookSymbols? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val zgaMethodsArray = obj.optJSONArray("zgaMethods")
                val zgaMethodsList = if (zgaMethodsArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until zgaMethodsArray.length()) {
                        list.add(zgaMethodsArray.getString(i))
                    }
                    list
                } else null
                
                HookSymbols(
                    homeTabClass = obj.optStringOrNull("homeTabClass"),
                    homeTabRebuildMethod = obj.optStringOrNull("homeTabRebuildMethod"),
                    homeTabListField = obj.optStringOrNull("homeTabListField"),
                    settingsClass = obj.optStringOrNull("settingsClass"),
                    settingsInitMethod = obj.optStringOrNull("settingsInitMethod"),
                    settingsContainerField = obj.optStringOrNull("settingsContainerField"),
                    feedTemplateKeyMethod = obj.optStringOrNull("feedTemplateKeyMethod"),
                    feedTemplateLoadMoreMethod = obj.optStringOrNull("feedTemplateLoadMoreMethod"),
                    splashAdHelperClass = obj.optStringOrNull("splashAdHelperClass"),
                    splashAdHelperMethod = obj.optStringOrNull("splashAdHelperMethod"),
                    closeAdDataClass = obj.optStringOrNull("closeAdDataClass"),
                    closeAdDataMethodG1 = obj.optStringOrNull("closeAdDataMethodG1"),
                    closeAdDataMethodJ1 = obj.optStringOrNull("closeAdDataMethodJ1"),
                    nd7Class = obj.optStringOrNull("nd7Class"),
                    nd7MethodI0 = obj.optStringOrNull("nd7MethodI0"),
                    nd7MethodL = obj.optStringOrNull("nd7MethodL"),
                    zgaClass = obj.optStringOrNull("zgaClass"),
                    zgaMethods = zgaMethodsList,

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
    private const val TAG = "[HookSymbolResolver]"
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
                    log(logger, "cache symbols: \n${describeSymbols(appCtx, cached)}")
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
        log(logger, "final symbols: \n${describeSymbols(appCtx, scanned)}")

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
        val candidates = listObfuscatedRootClasses(context, cl, logger)
        log(logger, "candidates=${candidates.size}")
        if (candidates.isEmpty()) return HookSymbols(source = "unsupported", createdAt = System.currentTimeMillis())

        // Whitelist for un-obfuscated class names that shouldn't be searched through candidates
        val whitelistClasses = mutableListOf<String>()
        whitelistClasses.add("com.baidu.tieba.feed.list.FeedTemplateAdapter")
        whitelistClasses.add("com.baidu.tieba.ad.under.utils.SplashForbidAdHelperKt")
        whitelistClasses.add("com.baidu.tbadk.data.CloseAdData")
        whitelistClasses.add("com.baidu.tieba.nd7")

        val candidatesWithWhitelist = candidates.toMutableList()
        candidatesWithWhitelist.addAll(whitelistClasses)

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

        // Dynamic extraction of ad/ui related obfuscated names
        var feedLoadMoreMethod: String? = null
        var splashAdHelperClass: String? = null
        var splashAdHelperMethod: String? = null
        var closeAdDataClass: String? = null
        var closeAdDataMethodG1: String? = null
        var closeAdDataMethodJ1: String? = null
        var nd7Class: String? = null
        var nd7MethodI0: String? = null
        var nd7MethodL: String? = null
        var zgaClass: String? = null
        var zgaMethodsList: List<String>? = null


        val feedLoadMoreMatch = runRules(candidatesWithWhitelist, cl, listOf(FeedTemplateLoadMoreRule()), logger, "feedLoadMore")
        if (feedLoadMoreMatch != null) {
            feedLoadMoreMethod = feedLoadMoreMatch.methodName
        }
        
        val splashAdMatch = runRules(candidatesWithWhitelist, cl, listOf(SplashAdHelperRule()), logger, "splashAdHelper")
        if (splashAdMatch != null) {
            splashAdHelperClass = splashAdMatch.className
            splashAdHelperMethod = splashAdMatch.methodName
        }
        
        val closeAdMatch = runRules(candidatesWithWhitelist, cl, listOf(CloseAdDataRule()), logger, "closeAdData")
        if (closeAdMatch != null) {
            closeAdDataClass = closeAdMatch.className
            val parts = closeAdMatch.methodName.split(",")
            if (parts.size >= 2) {
                closeAdDataMethodG1 = parts[0]
                closeAdDataMethodJ1 = parts[1]
            }
        }
        
        val nd7Match = runRules(candidatesWithWhitelist, cl, listOf(Nd7Rule()), logger, "nd7")
        if (nd7Match != null) {
            nd7Class = nd7Match.className
            val parts = nd7Match.methodName.split(",")
            if (parts.size >= 2) {
                nd7MethodI0 = parts[0]
                nd7MethodL = parts[1]
            }
        }
        
        val zgaMatch = runRules(candidatesWithWhitelist, cl, listOf(ZgaRule()), logger, "zga")
        if (zgaMatch != null) {
            zgaClass = zgaMatch.className
            zgaMethodsList = zgaMatch.methodName.split(",").filter { it.isNotBlank() }
        }



        return HookSymbols(
            settingsClass = settingsMatch.className,
            settingsInitMethod = settingsMatch.methodName,
            settingsContainerField = settingsMatch.fieldName,
            homeTabClass = homeMatch?.className,
            homeTabRebuildMethod = homeMatch?.methodName,
            homeTabListField = homeMatch?.fieldName,
            feedTemplateLoadMoreMethod = feedLoadMoreMethod,
            splashAdHelperClass = splashAdHelperClass,
            splashAdHelperMethod = splashAdHelperMethod,
            closeAdDataClass = closeAdDataClass,
            closeAdDataMethodG1 = closeAdDataMethodG1,
            closeAdDataMethodJ1 = closeAdDataMethodJ1,
            nd7Class = nd7Class,
            nd7MethodI0 = nd7MethodI0,
            nd7MethodL = nd7MethodL,
            zgaClass = zgaClass,
            zgaMethods = zgaMethodsList,

            source = if (homeMatch != null) "scan" else "partial",
            createdAt = System.currentTimeMillis(),
        )
    }

    @Suppress("DEPRECATION")
    private fun listObfuscatedRootClasses(context: Context, cl: ClassLoader, logger: ScanLogger?): List<String> {
        val out = ArrayList<String>(512)
        try {
            var currentCl: ClassLoader? = cl
            while (currentCl != null) {
                if (currentCl is dalvik.system.BaseDexClassLoader) {
                    val pathListField = XposedCompat.findField(currentCl::class.java, "pathList")
                    val pathList = pathListField.get(currentCl)!!
                    val dexElementsField = XposedCompat.findField(pathList::class.java, "dexElements")
                    val dexElements = dexElementsField.get(pathList) as Array<*>
                    
                    for (element in dexElements) {
                        if (element == null) continue
                        val dexFileField = try { XposedCompat.findField(element::class.java, "dexFile") } catch (_: Throwable) { null } ?: continue
                        val dexFile = dexFileField.get(element) as? DexFile ?: continue
                        val entries = dexFile.entries()
                        while (entries.hasMoreElements()) {
                            val name = entries.nextElement()
                            if (name.startsWith("com.baidu.tieba.") || name.startsWith("com.baidu.tbadk.")) {
                                val shortName = name.substringAfterLast('.')
                                if (isLikelyObfuscatedShortName(shortName)) {
                                    out.add(name)
                                }
                            }
                        }
                    }
                }
                currentCl = currentCl.parent
            }
        } catch (t: Throwable) {
            log(logger, "list classes from dex path list failed: ${t.message}")
            XposedCompat.log(t)
        }
        
        if (out.isNotEmpty()) {
            log(logger, "obfuscated root classes listed: ${out.size}")
            return out.distinct()
        }
        
        val sourceDir = context.applicationInfo?.sourceDir ?: return out
        var dexFile: DexFile? = null
        try {
            dexFile = DexFile(sourceDir)
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement()
                if (name.startsWith("com.baidu.tieba.") || name.startsWith("com.baidu.tbadk.")) {
                    val shortName = name.substringAfterLast('.')
                    if (isLikelyObfuscatedShortName(shortName)) {
                        out.add(name)
                    }
                }
            }
        } catch (t: Throwable) {
            XposedCompat.log("$TAG: list classes fallback failed: ${t.message}")
            log(logger, "list classes fallback failed: ${t.message}")
            XposedCompat.log(t)
        } finally {
            try {
                dexFile?.close()
            } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
        }
        log(logger, "obfuscated root classes listed: ${out.size}")
        return out.distinct()
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
            XposedCompat.findClassOrNull(name, cl)
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
                } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
            }
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
    }

    private fun log(logger: ScanLogger?, line: String) {
        try {
            XposedCompat.log("$TAG: $line")
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
        try {
            logger?.log(line)
        } catch (t: Throwable) { XposedCompat.logD("HookSymbolResolver: ${t.message}") }
    }

    private fun describeSymbols(context: Context, symbols: HookSymbols): String {
        val appMeta = describeAppMetaFull(context)
        val modVersion = com.forbidad4tieba.hook.BuildConfig.VERSION_NAME

        fun fmt(name: String, value: String?): String {
            val isNull = value == null || 
                         value == "null" || 
                         value.contains("null.") || 
                         value.contains(".null") || 
                         value.contains("[null]")
                         
            return if (!isNull) {
                "✅ [$name: $value]"
            } else {
                "❌ [$name: NOT FOUND]"
            }
        }

        return """
            ========== TBHook Match Status ==========
            App Version   : $appMeta
            Module Version: $modVersion
            Match Result  :
            ${fmt("Home", "${symbols.homeTabClass}.${symbols.homeTabRebuildMethod}[${symbols.homeTabListField}]")}
            ${fmt("Settings", "${symbols.settingsClass}.${symbols.settingsInitMethod}[${symbols.settingsContainerField}]")}
            ${fmt("Zga", symbols.zgaClass)}
            ${fmt("Splash", "${symbols.splashAdHelperClass}.${symbols.splashAdHelperMethod}")}
            ${fmt("FeedLoadMore", symbols.feedTemplateLoadMoreMethod)}

            Source        : ${symbols.source}
            =========================================
        """.trimIndent()
    }

    private fun describeAppMetaFull(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            "${info.versionName} ($vCode)"
        } catch (_: Throwable) {
            "Unknown"
        }
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
