package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.model.HomeBottomEasterEggAdScanSymbols
import com.forbidad4tieba.hook.symbol.model.HomeBottomEasterEggAdSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.HomeBottomEasterEggAdSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier
import org.json.JSONObject

/** Owns the cached descriptors and host rules for this capability. */
object HomeBottomEasterEggContract : SymbolContract("HomeBottomEasterEgg") {
    internal override val candidateClasses = listOf(
        "com.baidu.tbadk.mainTab.MaintabAddResponedData",
    )

    val homeBottomEasterEggParserClass = text("homeBottomEasterEggParserClass")
    val homeBottomEasterEggParserMethod = text("homeBottomEasterEggParserMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val homeBottomEasterEggScan = runScanStep(
            "HomeBottomEasterEggAdHook",
            logger,
            scanErrors,
            HomeBottomEasterEggAdScanSymbols(),
        ) {
            HomeBottomEasterEggAdSymbolScanner.scan(context, cl, logger)
        }

        val homeBottomEasterEggParserClass: String? = homeBottomEasterEggScan.parserClass

        val homeBottomEasterEggParserMethod: String? = homeBottomEasterEggScan.parserMethod

        output[HomeBottomEasterEggContract.homeBottomEasterEggParserClass] = homeBottomEasterEggParserClass
        output[HomeBottomEasterEggContract.homeBottomEasterEggParserMethod] = homeBottomEasterEggParserMethod
    }

    fun resolveHomeBottomEasterEggAdSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): HomeBottomEasterEggAdSymbols? {
        val resolvedSymbols = symbols ?: run {
            Diagnostics.log("[HomeBottomEasterEggAdHook] skipped: scan symbols unavailable")
            return null
        }
        val className = resolvedSymbols[HomeBottomEasterEggContract.homeBottomEasterEggParserClass]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[HomeBottomEasterEggAdHook] skipped: parser class missing")
            return null
        }
        val methodName = resolvedSymbols[HomeBottomEasterEggContract.homeBottomEasterEggParserMethod]?.takeIf { it.isNotBlank() } ?: run {
            Diagnostics.log("[HomeBottomEasterEggAdHook] skipped: parser method missing")
            return null
        }
        return try {
            val clazz = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[HomeBottomEasterEggAdHook] parser class NOT FOUND: $className")
                return null
            }
            val matches = clazz.declaredMethods.filter { method ->
                method.name == methodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(JSONObject::class.java))
            }
            if (matches.size != 1) {
                Diagnostics.log(
                    "[HomeBottomEasterEggAdHook] parser restore rejected: " +
                        "$className#$methodName matches=${matches.size}",
                )
                null
            } else {
                matches.single().isAccessible = true
                HomeBottomEasterEggAdSymbols(matches.single())
            }
        } catch (t: Throwable) {
            Diagnostics.log("[HomeBottomEasterEggAdHook] parser restore FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        out[HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG] = statusFromMissing(
            listOfNotNull(
                "homeBottomEasterEggParserClass".takeIf {
                    symbols[HomeBottomEasterEggContract.homeBottomEasterEggParserClass].isNullOrBlank()
                },
                "homeBottomEasterEggParserMethod".takeIf {
                    symbols[HomeBottomEasterEggContract.homeBottomEasterEggParserMethod].isNullOrBlank()
                },
            ),
        )
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "HomeBottomEasterEggAdHook.Parser",
            "${symbols[HomeBottomEasterEggContract.homeBottomEasterEggParserClass]}.${symbols[HomeBottomEasterEggContract.homeBottomEasterEggParserMethod]}",
            listOf(
                HomeBottomEasterEggContract.homeBottomEasterEggParserClass.check(symbols),
                HomeBottomEasterEggContract.homeBottomEasterEggParserMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("HomeBottomEasterEggAdHook.Parser", false, listOf(HookFeatureKey.BLOCK_AD_HOME_BOTTOM_EASTER_EGG)),
    )

    // No persisted reflective targets outside the delegated contract.
    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean = true
}
