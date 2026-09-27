package com.forbidad4tieba.hook

import android.content.Context
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.TitanRuntimeState
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.cache.HookSymbolCacheKeys
import com.forbidad4tieba.hook.symbol.cache.HookSymbolCachePolicy
import com.forbidad4tieba.hook.symbol.cache.HookSymbolMemoryCache
import com.forbidad4tieba.hook.symbol.contract.HomeTabsContract
import com.forbidad4tieba.hook.symbol.contract.SymbolContracts
import com.forbidad4tieba.hook.symbol.contract.SymbolScanContext
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.model.ScanSupportState
import com.forbidad4tieba.hook.symbol.model.TargetAppVersionInfo
import com.forbidad4tieba.hook.symbol.scan.HookSymbolScanContext
import com.forbidad4tieba.hook.symbol.scan.HookSymbolScanSession
import com.forbidad4tieba.hook.symbol.scan.ScanDexQueries
import com.forbidad4tieba.hook.symbol.scan.ScanCandidateCollector
import com.forbidad4tieba.hook.symbol.status.HookFeatureStatusDeriver
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.symbol.status.HookSymbolStatusFormatter
import com.forbidad4tieba.hook.symbol.validation.HookSymbolValidator
import java.io.File

object HookSymbolResolver {
    private const val TAG = "[HookSymbolResolver]"

    private const val KEY_SYMBOL_FP = HookSymbolCacheKeys.SYMBOL_FP

    private const val KEY_SYMBOL_JSON = HookSymbolCacheKeys.SYMBOL_JSON

    private const val KEY_CACHE_MODULE_VERSION = HookSymbolCacheKeys.MODULE_VERSION

    private const val KEY_SYMBOL_VERIFIED_FP = HookSymbolCacheKeys.VERIFIED_FP
    // Update these target version bounds when adapting a new Tieba release.

    const val TARGET_TIEBA_VERSION_NAME = "22.12.1.0"

    private const val MIN_TIEBA_VERSION_CODE = 369885440L

    private const val MAX_TIEBA_VERSION_CODE = 369885440L

    private const val VERSION_TYPE_META_NAME = "versionType"

    private const val OFFICIAL_VERSION_TYPE = "3"

    private val memoryCache = HookSymbolMemoryCache()

    fun getMemorySymbols(): HookSymbols? = memoryCache.currentSymbols()

    fun featureStatusMap(symbols: HookSymbols?): Map<String, HookFeatureStatus> {
        return HookFeatureStatusDeriver.derive(symbols ?: HookSymbols.unsupported())
    }

    fun isScanVersionCheckFailed(symbols: HookSymbols?): Boolean {
        return symbols != null && symbols.scanSupportState != ScanSupportState.SUPPORTED
    }

    fun hasScanErrors(symbols: HookSymbols?, failedLines: List<String>? = null): Boolean {
        if (symbols == null) return true
        if (symbols.source != "scan") return true
        if (symbols.scanErrors.isNotEmpty()) return true
        val missingLines = failedLines ?: formatUnavailableHookPointStatusLines(symbols)
        return missingLines.isNotEmpty()
    }

    fun formatUnavailableHookPointStatusLines(symbols: HookSymbols?): List<String> {
        return collectHookPointStatuses(symbols)
            .filter(HookPointStatus::isUnavailable)
            .map(HookPointStatus::formatLine)
    }

    fun formatScanIssueLines(symbols: HookSymbols?): List<String> {
        if (symbols == null) return listOf("HookPoint[SymbolCache] state=MISSING missing=symbols target=-")
        return formatUnavailableHookPointStatusLines(symbols).ifEmpty { symbols.scanErrors }
    }

    fun formatFeatureStatusLines(symbols: HookSymbols?): List<String> {
        return HookSymbolStatusFormatter.formatFeatureStatusLines(
            statusMap = featureStatusMap(symbols),
            featureKeys = HookFeatureStatusDeriver.featureKeys,
        )
    }

    fun formatHookPointStatusLines(symbols: HookSymbols?): List<String> {
        return collectHookPointStatuses(symbols).map(HookPointStatus::formatLine)
    }

    private fun collectHookPointStatuses(symbols: HookSymbols?): List<HookPointStatus> {
        return HookSymbolStatusFormatter.collectHookPointStatuses(
            symbols = symbols,
        )
    }

    fun resolve(
        context: Context,
        cl: ClassLoader,
        forceRescan: Boolean,
        logger: ScanLogger? = null,
    ): HookSymbols {
        val startedAt = System.currentTimeMillis()
        val appCtx = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appCtx)
        val prefs = appCtx.getSharedPreferences(HookSymbolCacheKeys.PREFS_NAME, Context.MODE_PRIVATE)
        HookSymbolScanDiagnostics.log(logger, "resolve start, forceRescan=$forceRescan")
        HookSymbolScanDiagnostics.log(logger, "fingerprint=$fingerprint")
        HookSymbolScanDiagnostics.log(logger, "thread=${Thread.currentThread().name}")
        HookSymbolScanDiagnostics.log(logger, "classLoader=${cl.javaClass.name}@${System.identityHashCode(cl)}")
        HookSymbolScanDiagnostics.log(logger, "app=${describeAppMeta(appCtx)}")
        ensureCacheForModuleVersion(appCtx, prefs, logger)

        val memorySymbols = memoryCache.getIfFingerprint(fingerprint)
        if (!forceRescan && memorySymbols != null) {
            HookSymbolScanDiagnostics.log(logger, "memory cache hit: source=${memorySymbols.source}")
            if (HookSymbolCachePolicy.isUsable(memorySymbols)) {
                return memorySymbols
            }
            HookSymbolScanDiagnostics.log(logger, "memory cache unusable, rescan required")
        }

        if (!forceRescan) {
            val cacheFp = prefs.getString(KEY_SYMBOL_FP, null)
            val cached = HookSymbolCachePolicy.decodeIfFingerprint(cacheFp, fingerprint) {
                prefs.getString(KEY_SYMBOL_JSON, null)
            }
            HookSymbolScanDiagnostics.log(logger, "disk cache fp match=${cacheFp == fingerprint}, cached=${cached != null}")
            if (cacheFp == fingerprint && cached != null) {
                val accepted = acceptCachedSymbolsIfUsable(cached, cl, fingerprint, prefs, logger, "disk cache")
                if (accepted != null) {
                    HookSymbolScanDiagnostics.log(logger, "disk cache usable: source=${accepted.source}")
                    if (logger != null || Diagnostics.detailed()) {
                        HookSymbolScanDiagnostics.log(logger, "cache symbols: \n${describeSymbols(appCtx, accepted)}")
                    }
                    memoryCache.put(fingerprint, accepted)
                    return accepted
                }
                HookSymbolScanDiagnostics.log(logger, "disk cache unusable, rescan required")
            }
        }

        HookSymbolScanDiagnostics.log(logger, "scan begin")

        val scanned = try {
            applyScanSupportCheck(appCtx, scan(appCtx, cl, logger), logger)
        } catch (t: Throwable) {
            val scanErrors = ArrayList<String>(1)
            logScanException(logger, "Scan", scanErrors, t)
            val unsupported = HookSymbols.unsupported(
                createdAt = System.currentTimeMillis(),
                scanErrors = scanErrors,
            )
            unsupported
        }
        HookSymbolScanDiagnostics.log(logger, "scan done: source=${scanned.source}")
        HookSymbolScanDiagnostics.log(
            logger,
            "scan support: state=${scanned.scanSupportState}, " +
                "version=${scanned.scanTargetVersionName}(${scanned.scanTargetVersionCode}), " +
                "versionType=${scanned.scanTargetVersionType ?: "-"}",
        )
        formatFeatureStatusLines(scanned).forEach { line -> HookSymbolScanDiagnostics.log(logger, line) }
        formatHookPointStatusLines(scanned).forEach { line -> HookSymbolScanDiagnostics.log(logger, line) }
        HookSymbolScanDiagnostics.log(logger, "final symbols: \n${describeSymbols(appCtx, scanned)}")

        val cacheEditor = prefs.edit()
            .putString(KEY_SYMBOL_FP, fingerprint)
            .putString(KEY_SYMBOL_JSON, scanned.toJson())
        if (scanned.source == "unsupported") {
            cacheEditor.remove(KEY_SYMBOL_VERIFIED_FP)
        } else {
            cacheEditor.putString(KEY_SYMBOL_VERIFIED_FP, fingerprint)
        }
        cacheEditor.apply()
        HookSymbolScanDiagnostics.log(logger, "cache updated")

        memoryCache.put(fingerprint, scanned)

        HookSymbolScanDiagnostics.log(logger, "durationMs=${System.currentTimeMillis() - startedAt}")
        return scanned
    }

    fun loadCachedIfUsable(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger? = null,
        verifyFull: Boolean = true,
    ): HookSymbols? {
        val appCtx = context.applicationContext ?: context
        val fingerprint = buildFingerprint(appCtx)
        val prefs = appCtx.getSharedPreferences(HookSymbolCacheKeys.PREFS_NAME, Context.MODE_PRIVATE)
        HookSymbolScanDiagnostics.log(logger, "loadCachedIfUsable fingerprint=$fingerprint")
        ensureCacheForModuleVersion(appCtx, prefs, logger)

        val memorySymbols = memoryCache.getIfFingerprint(fingerprint)
        if (memorySymbols != null) {
            HookSymbolScanDiagnostics.log(logger, "memory cache candidate: source=${memorySymbols.source}")
            if (HookSymbolCachePolicy.isUsable(memorySymbols)) return memorySymbols
        }

        val cacheFp = prefs.getString(KEY_SYMBOL_FP, null)
        val cached = HookSymbolCachePolicy.decodeIfFingerprint(cacheFp, fingerprint) {
            prefs.getString(KEY_SYMBOL_JSON, null)
        }
        HookSymbolScanDiagnostics.log(logger, "disk cache candidate: fpMatch=${cacheFp == fingerprint}, exists=${cached != null}")
        if (cacheFp == fingerprint && cached != null) {
            val accepted = acceptCachedSymbolsIfUsable(
                symbols = cached,
                cl = cl,
                fingerprint = fingerprint,
                prefs = prefs,
                logger = logger,
                source = "disk cache",
                verifyFull = verifyFull,
            )
            if (accepted != null) {
                memoryCache.put(fingerprint, accepted)
                HookSymbolScanDiagnostics.log(logger, "disk cache usable")
                return accepted
            }
        }
        HookSymbolScanDiagnostics.log(logger, "no usable cache")
        return null
    }

    private fun ensureCacheForModuleVersion(
        context: Context,
        prefs: android.content.SharedPreferences,
        logger: ScanLogger?,
    ) {
        val moduleVersion = runtimeModuleVersionCodeLabel()
        val cachedVersion = prefs.getString(KEY_CACHE_MODULE_VERSION, null)
        if (cachedVersion == moduleVersion) return
        prefs.edit()
            .remove(KEY_SYMBOL_FP)
            .remove(KEY_SYMBOL_JSON)
            .remove(KEY_SYMBOL_VERIFIED_FP)
            .putString(KEY_CACHE_MODULE_VERSION, moduleVersion)
            .apply()
        memoryCache.clear()
        HookSymbolScanDiagnostics.log(logger, "module version changed ($cachedVersion -> $moduleVersion), symbol cache cleared")
        HookSymbolScanDiagnostics.log(logger, "module cache owner=${context.packageName}")
    }

    private fun acceptCachedSymbolsIfUsable(
        symbols: HookSymbols,
        cl: ClassLoader,
        fingerprint: String,
        prefs: android.content.SharedPreferences,
        logger: ScanLogger?,
        source: String,
        verifyFull: Boolean = true,
    ): HookSymbols? {
        if (!HookSymbolCachePolicy.isUsable(symbols)) {
            HookSymbolScanDiagnostics.log(logger, "$source rejected: lightweight cache check failed")
            return null
        }
        if (symbols.source == "unsupported") {
            HookSymbolScanDiagnostics.log(logger, "$source unsupported, skipping full verification")
            return symbols
        }
        if (!verifyFull) {
            HookSymbolScanDiagnostics.log(logger, "$source lightweight usable")
            return symbols
        }
        if (prefs.getString(KEY_SYMBOL_VERIFIED_FP, null) == fingerprint) {
            HookSymbolScanDiagnostics.log(logger, "$source fast path: full verification already completed for fingerprint")
            return symbols
        }
        if (!HookSymbolValidator.isUsable(symbols, cl)) {
            HookSymbolScanDiagnostics.log(logger, "$source rejected: full verification failed")
            return null
        }
        prefs.edit()
            .putString(KEY_SYMBOL_VERIFIED_FP, fingerprint)
            .apply()
        HookSymbolScanDiagnostics.log(logger, "$source full verification completed")
        return symbols
    }

    private inline fun <T> withScanContext(cl: ClassLoader, sourcePaths: List<String>, block: () -> T): T {
        val previous = HookSymbolScanSession.get()
        val context = HookSymbolScanContext(cl, sourcePaths)
        HookSymbolScanSession.set(context)
        return try {
            block()
        } finally {
            context.close()
            HookSymbolScanSession.set(previous)
        }
    }

    private fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): HookSymbols =
        withScanContext(cl, ScanDexQueries.sourcePaths(context)) {
            scanInternal(context, cl, logger)
        }

    private inline fun <T> runScanStep(
        tag: String,
        logger: ScanLogger?,
        errors: MutableList<String>,
        fallback: T,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (t: Throwable) {
            logScanException(logger, tag, errors, t)
            fallback
        }
    }

    private fun scanInternal(context: Context, cl: ClassLoader, logger: ScanLogger?): HookSymbols {
        val scanErrors = ArrayList<String>()
        HookSymbolScanSession.get()?.scanErrors = scanErrors
        val scanCandidates = ScanCandidateCollector.collect(context, logger)
        val declaredCandidates = SymbolContracts.all.flatMap { it.candidateClasses }
        val candidatesWithWhitelist = (scanCandidates + declaredCandidates).distinct()
        HookSymbolScanDiagnostics.log(
            logger,
            "DexKit candidates=${scanCandidates.size}, including stable contracts=${candidatesWithWhitelist.size}",
        )
        if (candidatesWithWhitelist.isEmpty()) {
            val unsupported = HookSymbols.unsupported(createdAt = System.currentTimeMillis())
            return unsupported
        }

        val scan = SymbolScanContext(context, cl, logger, scanErrors, candidatesWithWhitelist)
        val output = HookSymbolsBuilder()
        SymbolContracts.all.forEach { contract -> contract.scan(scan, output) }
        output.scanErrors = scanErrors
        output.createdAt = System.currentTimeMillis()
        val symbols = output.build()
        output.source = if (symbols[HomeTabsContract.homeTabClass] != null) "scan" else "partial"
        return output.build()
    }

    private fun applyScanSupportCheck(
        context: Context,
        symbols: HookSymbols,
        logger: ScanLogger?,
    ): HookSymbols {
        val versionInfo = readTargetAppVersionInfo(context, logger)
            ?: return symbols.withScanSupport(ScanSupportState.UNKNOWN)

        if (
            versionInfo.versionCode > MAX_TIEBA_VERSION_CODE ||
            versionInfo.versionCode < MIN_TIEBA_VERSION_CODE
        ) {
            return symbols.withScanSupport(
                state = ScanSupportState.UNSUPPORTED_VERSION,
                targetVersionCode = versionInfo.versionCode,
                targetVersionName = versionInfo.versionName,
                targetVersionType = null,
            )
        }

        val versionType = readTargetVersionType(context, logger)
        return symbols.withScanSupport(
            state = if (isOfficialTiebaVersionType(versionType)) {
                ScanSupportState.SUPPORTED
            } else {
                ScanSupportState.NON_OFFICIAL
            },
            targetVersionCode = versionInfo.versionCode,
            targetVersionName = versionInfo.versionName,
            targetVersionType = versionType,
        )
    }

    private fun readTargetAppVersionInfo(context: Context, logger: ScanLogger?): TargetAppVersionInfo? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            TargetAppVersionInfo(versionCode = versionCode, versionName = info.versionName)
        } catch (t: Throwable) {
            HookSymbolScanDiagnostics.log(logger, "scan support version read failed: ${t.message}")
            null
        }
    }

    fun isOfficialTiebaVersionType(versionType: String?): Boolean {
        return versionType == OFFICIAL_VERSION_TYPE
    }

    @Suppress("DEPRECATION")

    fun readTargetVersionType(context: Context, logger: ScanLogger? = null): String? {
        return try {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA,
            )
            val value = info.metaData?.get(VERSION_TYPE_META_NAME)
            when (value) {
                is Number -> value.toLong().toString()
                is String -> value.trim().ifEmpty { null }
                null -> null
                else -> value.toString().trim().ifEmpty { null }
            }
        } catch (t: Throwable) {
            HookSymbolScanDiagnostics.log(logger, "scan support versionType read failed: ${t.message}")
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
            val titanFingerprint = TitanRuntimeState.buildFingerprint(context)
            "$vCode:${pkgInfo.lastUpdateTime}:$size:$modified:${runtimeModuleVersionCodeLabel()}:$titanFingerprint"
        } catch (t: Throwable) {
            Diagnostics.logD("$TAG fingerprint build failed: ${t.javaClass.simpleName}: ${t.message}")
            "unknown:${runtimeModuleVersionCodeLabel()}:${System.currentTimeMillis()}"
        }
    }

    private fun buildScanError(tag: String, t: Throwable): String {
        return "${tag.trim()} :: ${HookSymbolScanDiagnostics.sanitizeScanStatusText(HookSymbolScanDiagnostics.formatScanException(t))}"
    }

    private fun logScanException(
        logger: ScanLogger?,
        tag: String,
        errors: MutableList<String>,
        throwable: Throwable,
    ) {
        val error = buildScanError(tag, throwable)
        errors.add(error)
        HookSymbolScanDiagnostics.log(logger, "$tag scan exception: ${HookSymbolScanDiagnostics.splitScanError(error).second}")
        try {
            Diagnostics.log(throwable)
        } catch (t: Throwable) { Diagnostics.logD("HookSymbolResolver: ${t.message}") }
    }

    private fun describeSymbols(context: Context, symbols: HookSymbols): String =
        HookSymbolDiagnostics.describeSymbols(context, symbols)

    private fun describeAppMeta(context: Context): String =
        HookSymbolDiagnostics.describeAppMeta(context)

    private fun runtimeModuleVersionCodeLabel(): String =
        HookSymbolDiagnostics.runtimeModuleVersionCodeLabel()
}
