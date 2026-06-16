package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import com.forbidad4tieba.hook.symbol.model.StrategyAdScanSymbols
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object StrategyAdSymbolScanner {
    private const val CLOSE_AD_DATA_CLASS = "com.baidu.tbadk.data.CloseAdData"

    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): StrategyAdScanSymbols {
        val splash = scanSubStep("StrategyAdHook.SplashAdHelper", logger, StrategyAdScanSymbols()) {
            scanSplashAdHelper(candidates, cl, logger)
        }
        val closeAd = scanSubStep("StrategyAdHook.CloseAdData", logger, StrategyAdScanSymbols()) {
            scanCloseAdData(cl, logger)
        }
        val zga = scanSubStep("StrategyAdHook.Zga", logger, StrategyAdScanSymbols()) {
            scanZga(candidates, cl, logger)
        }
        return StrategyAdScanSymbols(
            splashAdHelperClass = splash.splashAdHelperClass,
            splashAdHelperMethod = splash.splashAdHelperMethod,
            closeAdDataClass = closeAd.closeAdDataClass,
            closeAdDataMethodG1 = closeAd.closeAdDataMethodG1,
            closeAdDataMethodJ1 = closeAd.closeAdDataMethodJ1,
            zgaClass = zga.zgaClass,
            zgaMethods = zga.zgaMethods,
        )
    }

    private fun scanSplashAdHelper(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): StrategyAdScanSymbols {
        val match = ScanReflection.runRules(candidates, cl, listOf(SplashAdHelperRule()), logger, "splashAdHelper")
            ?: return StrategyAdScanSymbols()
        return StrategyAdScanSymbols(
            splashAdHelperClass = match.className,
            splashAdHelperMethod = match.methodName,
        )
    }

    private fun scanCloseAdData(
        cl: ClassLoader,
        logger: ScanLogger?,
    ): StrategyAdScanSymbols {
        val match = ScanReflection.runRules(
            listOf(CLOSE_AD_DATA_CLASS),
            cl,
            listOf(CloseAdDataRule()),
            logger,
            "closeAdData",
        ) ?: return StrategyAdScanSymbols()
        val parts = match.methodName
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size < 2) return StrategyAdScanSymbols(closeAdDataClass = match.className)
        return StrategyAdScanSymbols(
            closeAdDataClass = match.className,
            closeAdDataMethodG1 = parts[0],
            closeAdDataMethodJ1 = parts.drop(1).joinToString(","),
        )
    }

    private fun scanZga(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): StrategyAdScanSymbols {
        val match = ScanReflection.runRules(candidates, cl, listOf(ZgaRule()), logger, "zga")
            ?: return StrategyAdScanSymbols()
        val candidateMethods = match.methodName
            .split(",")
            .filter { it.isNotBlank() }
            .distinct()
        if (!isZgaScanResultValid(cl, match.className, candidateMethods, logger)) {
            HookSymbolScanDiagnostics.log(
                logger,
                "zga scan rejected: class=${match.className}, methods=${candidateMethods.joinToString(",")}",
            )
            return StrategyAdScanSymbols()
        }
        return StrategyAdScanSymbols(
            zgaClass = match.className,
            zgaMethods = candidateMethods,
        )
    }

    private fun isZgaScanResultValid(
        cl: ClassLoader,
        className: String,
        methodNames: List<String>,
        logger: ScanLogger?,
    ): Boolean {
        if (methodNames.isEmpty()) return false
        return try {
            val cls = ScanReflection.safeFindClass(className, cl) ?: return false
            if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return false
            val methods = declaredMethods("Zga", cls, logger) ?: return false
            val hasIoException = methodNames.any { methodName ->
                methods.any { method ->
                    method.name == methodName &&
                        Modifier.isStatic(method.modifiers) &&
                        !Modifier.isAbstract(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.exceptionTypes.any { ex -> ex == IOException::class.java }
                }
            }
            if (!hasIoException) return false
            methodNames.all { methodName ->
                methods.any { method ->
                    method.name == methodName &&
                        Modifier.isStatic(method.modifiers) &&
                        !Modifier.isAbstract(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == String::class.java
                }
            }
        } catch (t: Throwable) {
            HookSymbolScanDiagnostics.log(
                logger,
                "zga scan validate failed: ${HookSymbolScanDiagnostics.sanitizeScanStatusText(
                    HookSymbolScanDiagnostics.formatScanException(t),
                )}",
            )
            false
        }
    }

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("StrategyAdHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

}
