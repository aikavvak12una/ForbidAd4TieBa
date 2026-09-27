package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.EnterForumWebScanSymbols
import com.forbidad4tieba.hook.symbol.model.EnterForumWebSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.resolve.EnterForumWebTargetResolver
import com.forbidad4tieba.hook.symbol.scan.EnterForumWebSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object EnterForumContract : SymbolContract("EnterForum") {
    val enterForumWebControllerClass = text("enterForumWebControllerClass")
    val enterForumWebLoadMethod = text("enterForumWebLoadMethod")
    val enterForumInitInfoDataClass = text("enterForumInitInfoDataClass")
    val enterForumInitInfoGetUrlMethod = text("enterForumInitInfoGetUrlMethod")
    val enterForumWebViewFieldOwnerClass = text("enterForumWebViewFieldOwnerClass")
    val enterForumWebViewField = text("enterForumWebViewField")
    val enterForumWebSetForceCommonMethod = text("enterForumWebSetForceCommonMethod")

    internal val load = SymbolDependencies(enterForumWebControllerClass, enterForumWebLoadMethod)
    internal val policy = SymbolDependencies(
        enterForumWebViewFieldOwnerClass, enterForumWebViewField, enterForumWebSetForceCommonMethod,
    )
    internal val required = load + policy
    internal val optionalSource = SymbolDependencies(enterForumInitInfoDataClass, enterForumInitInfoGetUrlMethod)

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val enterForumWebScan = runScanStep(
            "EnterForumWebHook",
            logger,
            scanErrors,
            EnterForumWebScanSymbols(),
        ) {
            EnterForumWebSymbolScanner.scan(context, cl, logger)
        }

        val enterForumWebControllerClass: String? = enterForumWebScan.controllerClass

        val enterForumWebLoadMethod: String? = enterForumWebScan.webLoadMethod

        val enterForumInitInfoDataClass: String? = enterForumWebScan.initInfoDataClass

        val enterForumInitInfoGetUrlMethod: String? = enterForumWebScan.initInfoGetUrlMethod

        val enterForumWebViewFieldOwnerClass: String? = enterForumWebScan.webViewFieldOwnerClass

        val enterForumWebViewField: String? = enterForumWebScan.webViewField

        val enterForumWebSetForceCommonMethod: String? = enterForumWebScan.setForceCommonMethod

        output[EnterForumContract.enterForumWebControllerClass] = enterForumWebControllerClass
        output[EnterForumContract.enterForumWebLoadMethod] = enterForumWebLoadMethod
        output[EnterForumContract.enterForumInitInfoDataClass] = enterForumInitInfoDataClass
        output[EnterForumContract.enterForumInitInfoGetUrlMethod] = enterForumInitInfoGetUrlMethod
        output[EnterForumContract.enterForumWebViewFieldOwnerClass] = enterForumWebViewFieldOwnerClass
        output[EnterForumContract.enterForumWebViewField] = enterForumWebViewField
        output[EnterForumContract.enterForumWebSetForceCommonMethod] = enterForumWebSetForceCommonMethod
    }

    fun resolveEnterForumWebSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): EnterForumWebSymbols? {
        return EnterForumWebTargetResolver.resolve(cl, symbols)
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> =
        mapOf(HookFeatureKey.FILTER_ENTER_FORUM_WEB to required.requiredStatus(symbols))

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        val enterForumLoadChecks = load.checks(symbols)
        val enterForumPolicyChecks = policy.checks(symbols)
        add(
            "EnterForumWebHook",
            "source=${symbols[EnterForumContract.enterForumInitInfoDataClass]}.${symbols[EnterForumContract.enterForumInitInfoGetUrlMethod]} " +
                "webLoad=${symbols[EnterForumContract.enterForumWebControllerClass]}.${symbols[EnterForumContract.enterForumWebLoadMethod]}(String)",
            enterForumLoadChecks + enterForumPolicyChecks,
        )
        addOptional(
            "EnterForumWebHook.InitInfoData",
            "${symbols[EnterForumContract.enterForumInitInfoDataClass]}.${symbols[EnterForumContract.enterForumInitInfoGetUrlMethod]}()",
            optionalSource.checks(symbols),
        )
        add(
            "EnterForumWebHook.WebLoad",
            "${symbols[EnterForumContract.enterForumWebControllerClass]}.${symbols[EnterForumContract.enterForumWebLoadMethod]}(String)",
            enterForumLoadChecks,
        )
        add(
            "EnterForumWebHook.LoadPolicy",
            "${symbols[EnterForumContract.enterForumWebViewFieldOwnerClass]}.${symbols[EnterForumContract.enterForumWebViewField]} -> " +
                "TbWebView.${symbols[EnterForumContract.enterForumWebSetForceCommonMethod]}(boolean)",
            enterForumPolicyChecks,
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("EnterForumWebHook", true, listOf(HookFeatureKey.FILTER_ENTER_FORUM_WEB)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean =
        (!required.anyPresent(symbols) || EnterForumWebTargetResolver.resolveWebLoad(cl, symbols) != null) &&
            (!optionalSource.anyPresent(symbols) || isEnterForumInitInfoValid(symbols, cl))

    private fun isEnterForumInitInfoValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[EnterForumContract.enterForumInitInfoDataClass] ?: return false
        val methodName = symbols[EnterForumContract.enterForumInitInfoGetUrlMethod] ?: return false
        return try {
            val targetClass = ScanReflection.safeFindClass(className, cl) ?: return false
            targetClass.methods.any { method ->
                !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.returnType == String::class.java &&
                    method.parameterTypes.isEmpty()
            }
        } catch (_: Throwable) {
            false
        }
    }
}
