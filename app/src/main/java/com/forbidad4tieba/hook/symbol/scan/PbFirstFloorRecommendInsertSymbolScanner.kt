package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.PbFirstFloorRecommendInsertScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PbFirstFloorRecommendInsertSymbolScanner {
    fun scan(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PbFirstFloorRecommendInsertScanSymbols {
        val targetClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_LEGACY_HEADER_BUSINESS_KT_CLASS,
            cl,
        ) ?: run {
            log(
                logger,
                "pbFirstFloorRecommendInsert: class not found: " +
                    StableTiebaHookPoints.PB_LEGACY_HEADER_BUSINESS_KT_CLASS,
            )
            return PbFirstFloorRecommendInsertScanSymbols()
        }
        val postDataClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_POST_DATA_CLASS,
            cl,
        ) ?: run {
            log(
                logger,
                "pbFirstFloorRecommendInsert: class not found: " +
                    StableTiebaHookPoints.PB_POST_DATA_CLASS,
            )
            return PbFirstFloorRecommendInsertScanSymbols()
        }
        val recommendDataClass = ScanReflection.safeFindClass(
            StableTiebaHookPoints.PB_FIRST_FLOOR_RECOMMEND_DATA_CLASS,
            cl,
        ) ?: run {
            log(
                logger,
                "pbFirstFloorRecommendInsert: class not found: " +
                    StableTiebaHookPoints.PB_FIRST_FLOOR_RECOMMEND_DATA_CLASS,
            )
            return PbFirstFloorRecommendInsertScanSymbols()
        }
        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "pbFirstFloorRecommendInsert: apk source path unavailable")
            return PbFirstFloorRecommendInsertScanSymbols()
        }
        val dexValidatedMethodNames = DexKitSemanticScanner.scanPbFirstFloorRecommendInsert(
            sourcePaths = sourcePaths,
            ownerClassName = targetClass.name,
            postDataClassName = postDataClass.name,
            recommendDataClassName = recommendDataClass.name,
            logger = logger,
        )
        if (dexValidatedMethodNames.isEmpty()) {
            return PbFirstFloorRecommendInsertScanSymbols()
        }
        return scanResolvedClass(
            targetClass = targetClass,
            postDataClass = postDataClass,
            dexValidatedMethodNames = dexValidatedMethodNames,
            logger = logger,
        )
    }

    internal fun scanResolvedClass(
        targetClass: Class<*>,
        postDataClass: Class<*>,
        dexValidatedMethodNames: Set<String>,
        logger: ScanLogger?,
    ): PbFirstFloorRecommendInsertScanSymbols {
        val methods = scanSubStep(
            "PbFirstFloorRecommendBlockHook.Methods",
            logger,
            null,
        ) {
            targetClass.declaredMethods.toList()
        } ?: return PbFirstFloorRecommendInsertScanSymbols()
        val candidates = methods.filter { method ->
            method.name in dexValidatedMethodNames &&
                isInsertMethod(method, postDataClass)
        }
        val method = candidates.singleOrNull()
        if (method == null) {
            log(
                logger,
                "pbFirstFloorRecommendInsert: expected=1 actual=${candidates.size} candidates=" +
                    candidates.joinToString(",") { describeMethodShape(it) }.ifBlank { "-" },
            )
            return PbFirstFloorRecommendInsertScanSymbols()
        }
        return PbFirstFloorRecommendInsertScanSymbols(
            className = targetClass.name,
            methodName = method.name,
        )
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    internal fun isInsertMethod(method: Method, postDataClass: Class<*>): Boolean {
        val params = method.parameterTypes
        return Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            params.size == 5 &&
            params[1] == postDataClass &&
            List::class.java.isAssignableFrom(params[2]) &&
            params[3] == Int::class.javaPrimitiveType &&
            params[4] == postDataClass
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val returnType = method.returnType.name.substringAfterLast('.')
        return "${method.name}($params):$returnType"
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
