package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation.resolveCachedParameterClass
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassEnterForumCapsuleSymbols
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassHostDarkModeSwitchSymbols
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassHostDarkModeSwitchTargets
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassResourceIds
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassSortSwitchSymbols
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassSubPbNextPageSymbols
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassTopChromeSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.scan.HomeNativeGlassSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import com.forbidad4tieba.hook.symbol.status.collectHomeNativeGlassHookPointStatuses
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object NativeGlassContract : SymbolContract("NativeGlass") {
    val homeNativeGlassSubPbNextPageMoreViewId = number("homeNativeGlassSubPbNextPageMoreViewId")
    val homeNativeGlassPbReplyTitleDividerViewId = number("homeNativeGlassPbReplyTitleDividerViewId")
    val homeNativeGlassDynamicBackgroundColorIds = numbers("homeNativeGlassDynamicBackgroundColorIds")
    val homeNativeGlassTopChromeTabSelectedMethodSpecs = texts("homeNativeGlassTopChromeTabSelectedMethodSpecs", preserveEmpty = false)
    val homeNativeGlassSubPbSetNextPageMethod = text("homeNativeGlassSubPbSetNextPageMethod")
    val homeNativeGlassSubPbSetNextPageParamType = text("homeNativeGlassSubPbSetNextPageParamType")
    val homeNativeGlassSortSwitchBackgroundPaintField = text("homeNativeGlassSortSwitchBackgroundPaintField")
    val homeNativeGlassSortSwitchSlideDrawMethod = text("homeNativeGlassSortSwitchSlideDrawMethod")
    val homeNativeGlassSortSwitchSlidePathField = text("homeNativeGlassSortSwitchSlidePathField")
    val homeNativeGlassEnterForumCapsuleControllerClass = text("homeNativeGlassEnterForumCapsuleControllerClass")
    val homeNativeGlassEnterForumCapsuleInitMethod = text("homeNativeGlassEnterForumCapsuleInitMethod")
    val homeNativeGlassEnterForumCapsuleRefreshMethod = text("homeNativeGlassEnterForumCapsuleRefreshMethod")
    val homeNativeGlassEnterForumCapsuleViewField = text("homeNativeGlassEnterForumCapsuleViewField")
    val homeNativeGlassEnterForumCapsuleTitleField = text("homeNativeGlassEnterForumCapsuleTitleField")
    val homeNativeGlassHostDarkModeMoreActivityClass = text("homeNativeGlassHostDarkModeMoreActivityClass")
    val homeNativeGlassHostDarkModeControllerField = text("homeNativeGlassHostDarkModeControllerField")
    val homeNativeGlassHostDarkModeSwitchGetterMethod = text("homeNativeGlassHostDarkModeSwitchGetterMethod")
    val homeNativeGlassHostDarkModeSwitchStateField = text("homeNativeGlassHostDarkModeSwitchStateField")
    val homeNativeGlassHostDarkModeSwitchSetOnMethod = text("homeNativeGlassHostDarkModeSwitchSetOnMethod")
    val homeNativeGlassHostDarkModeSwitchSetOffMethod = text("homeNativeGlassHostDarkModeSwitchSetOffMethod")
    val homeNativeGlassHostDarkModeSwitchCallbackMethod = text("homeNativeGlassHostDarkModeSwitchCallbackMethod")
    val pbCommonLayoutPreloaderGetOrDefaultMethod = text("pbCommonLayoutPreloaderGetOrDefaultMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        var homeNativeGlassTopChromeTabSelectedMethodSpecs: List<String>? = null

        var homeNativeGlassSubPbSetNextPageMethod: String? = null

        var homeNativeGlassSubPbSetNextPageParamType: String? = null

        var homeNativeGlassSortSwitchBackgroundPaintField: String? = null

        var homeNativeGlassSortSwitchSlideDrawMethod: String? = null

        var homeNativeGlassSortSwitchSlidePathField: String? = null

        var homeNativeGlassEnterForumCapsuleControllerClass: String? = null

        var homeNativeGlassEnterForumCapsuleInitMethod: String? = null

        var homeNativeGlassEnterForumCapsuleRefreshMethod: String? = null

        var homeNativeGlassEnterForumCapsuleViewField: String? = null

        var homeNativeGlassEnterForumCapsuleTitleField: String? = null

        var homeNativeGlassHostDarkModeMoreActivityClass: String? = null

        var homeNativeGlassHostDarkModeControllerField: String? = null

        var homeNativeGlassHostDarkModeSwitchGetterMethod: String? = null

        var homeNativeGlassHostDarkModeSwitchStateField: String? = null

        var homeNativeGlassHostDarkModeSwitchSetOnMethod: String? = null

        var homeNativeGlassHostDarkModeSwitchSetOffMethod: String? = null

        var homeNativeGlassHostDarkModeSwitchCallbackMethod: String? = null

        val homeNativeGlassResourceIds = runScanStep(
            "HomeNativeGlassHook.Resources",
            logger,
            scanErrors,
            HomeNativeGlassResourceIds(),
        ) {
            HomeNativeGlassSymbolScanner.scanResourceIds(context, cl, logger)
        }

        val homeNativeGlassSubPbNextPageMoreViewId: Int? = homeNativeGlassResourceIds.subPbNextPageMoreViewId

        val homeNativeGlassPbReplyTitleDividerViewId: Int? = homeNativeGlassResourceIds.pbReplyTitleDividerViewId

        val homeNativeGlassDynamicBackgroundColorIds = homeNativeGlassResourceIds.dynamicBackgroundColorIds

        val homeNativeGlassTopChromeSymbols = runScanStep(
            "HomeNativeGlassHook.TopChrome",
            logger,
            scanErrors,
            HomeNativeGlassTopChromeSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanTopChrome(cl, logger)
        }

        homeNativeGlassTopChromeTabSelectedMethodSpecs =
            homeNativeGlassTopChromeSymbols.tabSelectedMethodSpecs.takeIf { it.isNotEmpty() }

        val homeNativeGlassSubPbNextPageSymbols = runScanStep(
            "HomeNativeGlassHook.SubPbNextPage",
            logger,
            scanErrors,
            HomeNativeGlassSubPbNextPageSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanSubPbNextPage(cl, logger)
        }

        homeNativeGlassSubPbSetNextPageMethod =
            homeNativeGlassSubPbNextPageSymbols.methodName

        homeNativeGlassSubPbSetNextPageParamType =
            homeNativeGlassSubPbNextPageSymbols.parameterTypeName

        val homeNativeGlassSortSwitchSymbols = runScanStep(
            "HomeNativeGlassHook.SortSwitch",
            logger,
            scanErrors,
            HomeNativeGlassSortSwitchSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanSortSwitch(context, cl, logger)
        }

        homeNativeGlassSortSwitchBackgroundPaintField =
            homeNativeGlassSortSwitchSymbols.backgroundPaintField

        homeNativeGlassSortSwitchSlideDrawMethod =
            homeNativeGlassSortSwitchSymbols.slideDrawMethod

        homeNativeGlassSortSwitchSlidePathField =
            homeNativeGlassSortSwitchSymbols.slidePathField

        val homeNativeGlassEnterForumCapsuleSymbols = runScanStep(
            "HomeNativeGlassHook.EnterForumCapsule",
            logger,
            scanErrors,
            HomeNativeGlassEnterForumCapsuleSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanEnterForumCapsule(context, candidatesWithWhitelist, cl, logger)
        }

        homeNativeGlassEnterForumCapsuleControllerClass =
            homeNativeGlassEnterForumCapsuleSymbols.controllerClass

        homeNativeGlassEnterForumCapsuleInitMethod =
            homeNativeGlassEnterForumCapsuleSymbols.initMethod

        homeNativeGlassEnterForumCapsuleRefreshMethod =
            homeNativeGlassEnterForumCapsuleSymbols.refreshMethod

        homeNativeGlassEnterForumCapsuleViewField =
            homeNativeGlassEnterForumCapsuleSymbols.viewField

        homeNativeGlassEnterForumCapsuleTitleField =
            homeNativeGlassEnterForumCapsuleSymbols.titleField

        val homeNativeGlassHostDarkModeSwitchSymbols = runScanStep(
            "HomeNativeGlassHook.HostDarkModeSwitch",
            logger,
            scanErrors,
            HomeNativeGlassHostDarkModeSwitchSymbols(),
        ) {
            HomeNativeGlassSymbolScanner.scanHostDarkModeSwitch(context, cl, logger)
        }

        homeNativeGlassHostDarkModeMoreActivityClass =
            homeNativeGlassHostDarkModeSwitchSymbols.moreActivityClass

        homeNativeGlassHostDarkModeControllerField =
            homeNativeGlassHostDarkModeSwitchSymbols.controllerField

        homeNativeGlassHostDarkModeSwitchGetterMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchGetterMethod

        homeNativeGlassHostDarkModeSwitchStateField =
            homeNativeGlassHostDarkModeSwitchSymbols.switchStateField

        homeNativeGlassHostDarkModeSwitchSetOnMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchSetOnMethod

        homeNativeGlassHostDarkModeSwitchSetOffMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchSetOffMethod

        homeNativeGlassHostDarkModeSwitchCallbackMethod =
            homeNativeGlassHostDarkModeSwitchSymbols.switchCallbackMethod

        val pbCommonLayoutPreloaderGetOrDefaultMethod: String? = runScanStep(
            "HomeNativeGlassHook.CommonLayoutPreloader",
            logger,
            scanErrors,
            null as String?,
        ) {
            HomeNativeGlassSymbolScanner.scanPbCommonLayoutPreloaderGetOrDefaultMethod(cl, logger)
        }

        output[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId] = homeNativeGlassSubPbNextPageMoreViewId
        output[NativeGlassContract.homeNativeGlassPbReplyTitleDividerViewId] = homeNativeGlassPbReplyTitleDividerViewId
        output[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds] = homeNativeGlassDynamicBackgroundColorIds
        output[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs] = homeNativeGlassTopChromeTabSelectedMethodSpecs
        output[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod] = homeNativeGlassSubPbSetNextPageMethod
        output[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType] = homeNativeGlassSubPbSetNextPageParamType
        output[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField] = homeNativeGlassSortSwitchBackgroundPaintField
        output[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod] = homeNativeGlassSortSwitchSlideDrawMethod
        output[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField] = homeNativeGlassSortSwitchSlidePathField
        output[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass] = homeNativeGlassEnterForumCapsuleControllerClass
        output[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod] = homeNativeGlassEnterForumCapsuleInitMethod
        output[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod] = homeNativeGlassEnterForumCapsuleRefreshMethod
        output[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField] = homeNativeGlassEnterForumCapsuleViewField
        output[NativeGlassContract.homeNativeGlassEnterForumCapsuleTitleField] = homeNativeGlassEnterForumCapsuleTitleField
        output[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass] = homeNativeGlassHostDarkModeMoreActivityClass
        output[NativeGlassContract.homeNativeGlassHostDarkModeControllerField] = homeNativeGlassHostDarkModeControllerField
        output[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod] = homeNativeGlassHostDarkModeSwitchGetterMethod
        output[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField] = homeNativeGlassHostDarkModeSwitchStateField
        output[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod] = homeNativeGlassHostDarkModeSwitchSetOnMethod
        output[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod] = homeNativeGlassHostDarkModeSwitchSetOffMethod
        output[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod] = homeNativeGlassHostDarkModeSwitchCallbackMethod
        output[NativeGlassContract.pbCommonLayoutPreloaderGetOrDefaultMethod] = pbCommonLayoutPreloaderGetOrDefaultMethod
    }

    private const val HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS =
        "com.baidu.adp.widget.BdSwitchView.BdSwitchView"

    fun resolveHomeNativeGlassHostDarkModeSwitchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): HomeNativeGlassHostDarkModeSwitchTargets? {
        class OptionalSymbolMissing(message: String) : IllegalStateException(message)

        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[HomeNativeGlassHook] host dark mode switch skipped: scan symbols unavailable")
                return null
            }
            fun required(name: String, value: String?): String {
                return value?.takeIf { it.isNotBlank() } ?: throw OptionalSymbolMissing("missing $name")
            }

            val moreActivityClassName = required(
                "homeNativeGlassHostDarkModeMoreActivityClass",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass],
            )
            val controllerFieldName = required(
                "homeNativeGlassHostDarkModeControllerField",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeControllerField],
            )
            val switchGetterMethodName = required(
                "homeNativeGlassHostDarkModeSwitchGetterMethod",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod],
            )
            val switchStateFieldName = required(
                "homeNativeGlassHostDarkModeSwitchStateField",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField],
            )
            val switchSetOnMethodName = required(
                "homeNativeGlassHostDarkModeSwitchSetOnMethod",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod],
            )
            val switchSetOffMethodName = required(
                "homeNativeGlassHostDarkModeSwitchSetOffMethod",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod],
            )
            val switchCallbackMethodName = required(
                "homeNativeGlassHostDarkModeSwitchCallbackMethod",
                resolvedSymbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod],
            )
            val moreActivityClass = ScanReflection.safeFindClass(moreActivityClassName, cl)
                ?: error("class not found: $moreActivityClassName")
            val switchClass = ScanReflection.safeFindClass(HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS, cl)
                ?: error("class not found: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS")
            val controllerField = ScanReflection.collectInstanceFields(moreActivityClass).singleOrNull { field ->
                field.name == controllerFieldName &&
                    !Modifier.isStatic(field.modifiers)
            } ?: error("controller field mismatch: $moreActivityClassName.$controllerFieldName")
            val switchGetterMethod = ScanReflection.collectInstanceMethods(controllerField.type).singleOrNull { method ->
                method.name == switchGetterMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    switchClass.isAssignableFrom(method.returnType)
            } ?: error("switch getter mismatch: ${controllerField.type.name}.$switchGetterMethodName()")
            val switchStateField = ScanReflection.collectInstanceFields(switchClass).singleOrNull { field ->
                field.name == switchStateFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type.isEnum
            } ?: error("switch state field mismatch: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS.$switchStateFieldName")
            val switchSetOnMethod = switchClass.declaredMethods.singleOrNull { method ->
                method.name == switchSetOnMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            } ?: error("switch set on mismatch: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS.$switchSetOnMethodName()")
            val switchSetOffMethod = switchClass.declaredMethods.singleOrNull { method ->
                method.name == switchSetOffMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            } ?: error("switch set off mismatch: $HOME_NATIVE_GLASS_BD_SWITCH_VIEW_CLASS.$switchSetOffMethodName()")
            val switchCallbackMethod = ScanReflection.collectInstanceMethods(moreActivityClass).singleOrNull { method ->
                val params = method.parameterTypes
                method.name == switchCallbackMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    params.size == 2 &&
                    params[0].isAssignableFrom(switchGetterMethod.returnType) &&
                    params[1] == switchStateField.type
            } ?: error("switch callback mismatch: $moreActivityClassName.$switchCallbackMethodName(View, SwitchState)")

            controllerField.isAccessible = true
            switchGetterMethod.isAccessible = true
            switchStateField.isAccessible = true
            switchSetOnMethod.isAccessible = true
            switchSetOffMethod.isAccessible = true
            switchCallbackMethod.isAccessible = true
            HomeNativeGlassHostDarkModeSwitchTargets(
                moreActivityClass = moreActivityClass,
                controllerField = controllerField,
                switchGetterMethod = switchGetterMethod,
                switchStateField = switchStateField,
                switchSetOnMethod = switchSetOnMethod,
                switchSetOffMethod = switchSetOffMethod,
                switchCallbackMethod = switchCallbackMethod,
            )
        } catch (t: Throwable) {
            if (t is OptionalSymbolMissing) {
                Diagnostics.log("[HomeNativeGlassHook] host dark mode switch skipped: ${t.message}")
            } else {
                Diagnostics.log("[HomeNativeGlassHook] host dark mode switch symbol resolve FAILED: ${t.message}")
            }
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val homeNativeGlassCritical = ArrayList<String>(1)
        val homeNativeGlassOptional = ArrayList<String>(12)
        if (symbols[FeedContract.feedCardBindMethodSpec].isNullOrBlank()) homeNativeGlassCritical.add("feedCardBindMethodSpec")
        if (!symbols[HomeAnchorsContract.homePersonalizeAnchorClasses].orEmpty().contains(StableTiebaHookPoints.HOME_PERSONALIZE_PAGE_VIEW_CLASS)) {
            homeNativeGlassOptional.add("homeNativeGlassPageClass")
        }
        if (symbols[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId] == null || symbols[NativeGlassContract.homeNativeGlassSubPbNextPageMoreViewId] == 0) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbNextPageMoreViewId")
        }
        if (symbols[NativeGlassContract.homeNativeGlassPbReplyTitleDividerViewId] == null || symbols[NativeGlassContract.homeNativeGlassPbReplyTitleDividerViewId] == 0) {
            homeNativeGlassOptional.add("homeNativeGlassPbReplyTitleDividerViewId")
        }
        if (symbols[NativeGlassContract.pbCommonLayoutPreloaderGetOrDefaultMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("pbCommonLayoutPreloaderGetOrDefaultMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassDynamicBackgroundColorIds].isEmpty()) {
            homeNativeGlassOptional.add("homeNativeGlassDynamicBackgroundColorIds")
        }
        val hasHomeNativeGlassTopChromeTabSelectedSpec = symbols[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs]
            .orEmpty()
            .any { spec ->
                val sep = spec.indexOf('#')
                sep > 0 && sep < spec.lastIndex
            }
        if (!hasHomeNativeGlassTopChromeTabSelectedSpec) {
            homeNativeGlassOptional.add("homeNativeGlassTopChromeTabSelectedMethodSpecs")
        }
        if (symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbSetNextPageMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSubPbSetNextPageParamType")
        }
        if (symbols[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchBackgroundPaintField")
        }
        if (symbols[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchSlideDrawMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassSortSwitchSlidePathField")
        }
        if (symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleControllerClass")
        }
        if (symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleInitMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleRefreshMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleViewField")
        }
        if (symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleTitleField].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassEnterForumCapsuleTitleField")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeMoreActivityClass")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeControllerField].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeControllerField")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchGetterMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchStateField")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchSetOnMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchSetOffMethod")
        }
        if (symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod].isNullOrBlank()) {
            homeNativeGlassOptional.add("homeNativeGlassHostDarkModeSwitchCallbackMethod")
        }
        out[HookFeatureKey.HOME_NATIVE_GLASS] = if (homeNativeGlassCritical.isEmpty()) {
            if (homeNativeGlassOptional.isEmpty()) {
                HookFeatureStatus(state = HookFeatureState.FULL)
            } else {
                HookFeatureStatus(
                    state = HookFeatureState.PARTIAL,
                    missingOptional = homeNativeGlassOptional,
                )
            }
        } else {
            HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = homeNativeGlassCritical,
                missingOptional = homeNativeGlassOptional,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        addAll(collectHomeNativeGlassHookPointStatuses(symbols))
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("HomeNativeGlassHook", true, listOf(HookFeatureKey.HOME_NATIVE_GLASS)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasHomeNativeGlassTopChromeSymbols =
            !symbols[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs].isNullOrEmpty()
        if (hasHomeNativeGlassTopChromeSymbols && !isHomeNativeGlassTopChromeValid(symbols, cl)) {
            return false
        }
        val hasHomeNativeGlassSubPbNextPageSymbols =
            symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType] != null
        if (hasHomeNativeGlassSubPbNextPageSymbols && !isHomeNativeGlassSubPbNextPageValid(symbols, cl)) {
            return false
        }
        val hasHomeNativeGlassSortSwitchSymbols =
            symbols[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField] != null ||
                symbols[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField] != null
        if (hasHomeNativeGlassSortSwitchSymbols && !isHomeNativeGlassSortSwitchValid(symbols, cl)) {
            return false
        }
        val hasHomeNativeGlassEnterForumCapsuleSymbols =
            symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass] != null ||
                symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField] != null ||
                symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleTitleField] != null
        if (
            hasHomeNativeGlassEnterForumCapsuleSymbols &&
            !isHomeNativeGlassEnterForumCapsuleValid(symbols, cl)
        ) {
            return false
        }
        val hasHomeNativeGlassHostDarkModeSwitchSymbols =
            symbols[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass] != null ||
                symbols[NativeGlassContract.homeNativeGlassHostDarkModeControllerField] != null ||
                symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField] != null ||
                symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod] != null ||
                symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod] != null
        if (
            hasHomeNativeGlassHostDarkModeSwitchSymbols &&
            !isHomeNativeGlassHostDarkModeSwitchValid(symbols, cl)
        ) {
            return false
        }
        return true
    }

    private const val BD_SWITCH_VIEW_CLASS = "com.baidu.adp.widget.BdSwitchView.BdSwitchView"

    private fun isHomeNativeGlassTopChromeValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val specs = symbols[NativeGlassContract.homeNativeGlassTopChromeTabSelectedMethodSpecs].orEmpty()
        if (specs.isEmpty()) return false
        return try {
            specs.all { spec ->
                val target = parseClassMethodSpec(spec) ?: return false
                val clazz = ScanReflection.safeFindClass(target.first, cl) ?: return false
                val method = clazz.getDeclaredMethod(
                    target.second,
                    Integer.TYPE,
                    Integer.TYPE,
                )
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeNativeGlassSubPbNextPageValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val methodName = symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageMethod] ?: return false
        val parameterTypeName = symbols[NativeGlassContract.homeNativeGlassSubPbSetNextPageParamType] ?: return false
        val listViewClass = ScanReflection.safeFindClass(StableTiebaHookPoints.BD_LIST_VIEW_CLASS, cl) ?: return false
        val parameterType = resolveCachedParameterClass(parameterTypeName, cl) ?: return false
        return try {
            val method = listViewClass.getDeclaredMethod(methodName, parameterType)
            !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeNativeGlassEnterForumCapsuleValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val controllerClassName = symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleControllerClass] ?: return false
        val initMethodName = symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleInitMethod] ?: return false
        val refreshMethodName = symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleRefreshMethod] ?: return false
        val viewFieldName = symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleViewField] ?: return false
        val titleFieldName = symbols[NativeGlassContract.homeNativeGlassEnterForumCapsuleTitleField] ?: return false
        return try {
            val controllerClass = ScanReflection.safeFindClass(controllerClassName, cl) ?: return false
            val fields = ScanReflection.collectInstanceFields(controllerClass)
            val viewFieldOk = fields.any { field ->
                field.name == viewFieldName &&
                    View::class.java.isAssignableFrom(field.type)
            }
            if (!viewFieldOk) return false
            val titleFieldOk = fields.any { field ->
                field.name == titleFieldName &&
                    field.type == String::class.java
            }
            if (!titleFieldOk) return false
            val methods = ScanReflection.collectInstanceMethods(controllerClass)
            val initMethodOk = methods.any { method ->
                method.name == initMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            }
            if (!initMethodOk) return false
            methods.any { method ->
                method.name == refreshMethodName &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun parseClassMethodSpec(spec: String): Pair<String, String>? {
        val sep = spec.indexOf('#')
        if (sep <= 0 || sep == spec.lastIndex) return null
        val className = spec.substring(0, sep).trim()
        val methodName = spec.substring(sep + 1).trim()
        if (className.isEmpty() || methodName.isEmpty()) return null
        return className to methodName
    }

    private fun isHomeNativeGlassSortSwitchValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val sortSwitchClass = ScanReflection.safeFindClass(StableTiebaHookPoints.SORT_SWITCH_BUTTON_CLASS, cl) ?: return false
        return try {
            val backgroundPaintFieldName = symbols[NativeGlassContract.homeNativeGlassSortSwitchBackgroundPaintField]
            if (!backgroundPaintFieldName.isNullOrBlank()) {
                val field = sortSwitchClass.getDeclaredField(backgroundPaintFieldName)
                if (field.type.name != "android.graphics.Paint" || Modifier.isStatic(field.modifiers)) {
                    return false
                }
            }
            val slidePathFieldName = symbols[NativeGlassContract.homeNativeGlassSortSwitchSlidePathField]
            if (!slidePathFieldName.isNullOrBlank()) {
                val field = sortSwitchClass.getDeclaredField(slidePathFieldName)
                if (field.type.name != "android.graphics.Path" || Modifier.isStatic(field.modifiers)) {
                    return false
                }
            }
            val slideDrawMethodName = symbols[NativeGlassContract.homeNativeGlassSortSwitchSlideDrawMethod]
            if (!slideDrawMethodName.isNullOrBlank()) {
                val method = sortSwitchClass.getDeclaredMethod(
                    slideDrawMethodName,
                    android.graphics.Canvas::class.java,
                )
                if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) {
                    return false
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isHomeNativeGlassHostDarkModeSwitchValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val moreActivityClassName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeMoreActivityClass] ?: return false
        val controllerFieldName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeControllerField] ?: return false
        val getterMethodName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchGetterMethod] ?: return false
        val stateFieldName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchStateField] ?: return false
        val setOnMethodName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOnMethod] ?: return false
        val setOffMethodName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchSetOffMethod] ?: return false
        val callbackMethodName = symbols[NativeGlassContract.homeNativeGlassHostDarkModeSwitchCallbackMethod] ?: return false
        return try {
            val moreActivityClass = ScanReflection.safeFindClass(moreActivityClassName, cl) ?: return false
            val switchClass = ScanReflection.safeFindClass(BD_SWITCH_VIEW_CLASS, cl) ?: return false
            val controllerField = ScanReflection.collectInstanceFields(moreActivityClass).singleOrNull { field ->
                field.name == controllerFieldName &&
                    !Modifier.isStatic(field.modifiers)
            } ?: return false
            val getterOk = ScanReflection.collectInstanceMethods(controllerField.type).any { method ->
                method.name == getterMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    switchClass.isAssignableFrom(method.returnType)
            }
            val getterMethod = ScanReflection.collectInstanceMethods(controllerField.type).singleOrNull { method ->
                method.name == getterMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    switchClass.isAssignableFrom(method.returnType)
            } ?: return false
            if (!getterOk) return false
            val stateField = ScanReflection.collectInstanceFields(switchClass).singleOrNull { field ->
                field.name == stateFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type.isEnum
            } ?: return false
            val switchMethods = switchClass.declaredMethods.toList()
            val setOnOk = switchMethods.any { method ->
                method.name == setOnMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            }
            if (!setOnOk) return false
            val setOffOk = switchMethods.any { method ->
                method.name == setOffMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Void.TYPE
            }
            if (!setOffOk) return false
            ScanReflection.collectInstanceMethods(moreActivityClass).any { method ->
                val params = method.parameterTypes
                method.name == callbackMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    params.size == 2 &&
                    View::class.java.isAssignableFrom(params[0]) &&
                    params[0].isAssignableFrom(getterMethod.returnType) &&
                    params[1] == stateField.type
            }
        } catch (_: Throwable) {
            false
        }
    }
}
