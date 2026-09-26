package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

internal object EnterForumWebSymbolScanner {
    private const val INIT_INFO_DATA_CLASS = "com.baidu.tbadk.coreExtra.data.InitInfoData"
    private const val INIT_INFO_GET_URL_METHOD = "getForumEnterUrl"
    private const val TB_WEB_VIEW = StableTiebaHookPoints.TB_WEB_VIEW_CLASS
    private const val AB_HELPER = "com.baidu.tbadk.abtest.UbsABTestHelper"
    private const val UNIQUE_ID = "com.baidu.adp.BdUniqueId"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"

    fun scan(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): EnterForumWebScanSymbols {
        val webController = scanSubStep(
            "EnterForumWebHook.WebController",
            logger,
            EnterForumWebScanSymbols(),
        ) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            checkNotNull(HookSymbolScanSession.withDexKitBridge(paths, logger) {
                scanWebController(it.bridge, cl, logger)
            }) { "DexKit bridge unavailable" }
        }
        val initInfo = scanSubStep(
            "EnterForumWebHook.InitInfoData",
            logger,
            EnterForumWebScanSymbols(),
        ) {
            scanInitInfoData(cl, logger)
        }
        return EnterForumWebScanSymbols(
            controllerClass = webController.controllerClass,
            webLoadMethod = webController.webLoadMethod,
            initInfoDataClass = initInfo.initInfoDataClass,
            initInfoGetUrlMethod = initInfo.initInfoGetUrlMethod,
            webViewFieldOwnerClass = webController.webViewFieldOwnerClass,
            webViewField = webController.webViewField,
            setForceCommonMethod = webController.setForceCommonMethod,
        )
    }

    private fun scanWebController(
        bridge: DexKitBridge,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): EnterForumWebScanSymbols {
        val webClass = checkNotNull(safeFindClass(TB_WEB_VIEW, cl)) { "TbWebView class missing" }
        val webData = checkNotNull(bridge.getClassData(TB_WEB_VIEW)) { "TbWebView dex missing" }
        val setForceCommon = unique("SetForceCommon", webData.methods.filter {
            it.methodName == "setForceCommon" && instance(it, "void", "boolean")
        })
        val loadUrl = unique("LoadUrl", webData.methods.filter {
            it.methodName == "loadUrl" && instance(it, "void", "java.lang.String")
        })
        val ab = checkNotNull(bridge.getClassData(AB_HELPER)) { "AB helper missing" }
        val waitSwitch = unique("WaitSwitch", ab.methods.filter {
            it.methodName == "needWaitH5InsertStatus" && Modifier.isStatic(it.modifiers) &&
                it.returnTypeName == "boolean" && it.paramTypeNames.isEmpty()
        })
        // The enter-forum controller opts its own WebView into common-H5 insertion.
        // Resolve that relationship rather than choosing a short (String) method name.
        val constructor = unique("Controller", waitSwitch.callers.filter {
            it.isConstructor && UNIQUE_ID in it.paramTypeNames && it.calls(setForceCommon)
        }.distinctBy { it.descriptor })
        val controller = checkNotNull(bridge.getClassData(constructor.declaredClassName))
        check(controller.methods.any { "forumSort" in it.usingStrings }) { "forumSort protocol missing" }
        val load = unique("WebLoad", controller.methods.filter { method ->
            instance(method, "void", "java.lang.String") && method.calls(loadUrl) &&
                method.invokes.any {
                    it.declaredClassName == TB_WEB_VIEW && it.methodName == "setWebViewSkinOverly" &&
                        instance(it, "void", "java.lang.String", "int")
                }
        })
        val getter = unique("WebViewGetter", load.invokes.filter {
            instance(it, TB_WEB_VIEW) && constructor.calls(it)
        }.distinctBy { it.descriptor })
        val loadCalls = load.invokes.filterNot { it.declaredClassName == INTERCEPTABLE }
        check(loadCalls.take(2).map { it.descriptor } == listOf(getter.descriptor, loadUrl.descriptor)) {
            "loadUrl must directly consume the controller WebView getter"
        }
        val field = getter.usingFields.filter {
            it.usingType.isRead() && !Modifier.isStatic(it.field.modifiers) && it.field.typeName == TB_WEB_VIEW
        }.map { it.field }.distinctBy { it.descriptor }.singleOrNull()
            ?: error("WebView getter must read exactly one TbWebView field")
        check(getter.usingFields.all { usage ->
            usage.usingType.isRead() && (usage.field.descriptor == field.descriptor ||
                Modifier.isStatic(usage.field.modifiers) && usage.field.typeName == INTERCEPTABLE ||
                usage.field.declaredClassName == INTERCEPT_RESULT)
        } && getter.invokes.all {
            it.declaredClassName == INTERCEPTABLE && it.methodName == "invokeV" ||
                it.declaredClassName == "kotlin.jvm.internal.Intrinsics" &&
                it.methodName == "throwUninitializedPropertyAccessException"
        }) { "WebView getter is not a read-only field accessor" }

        val controllerClass = checkNotNull(safeFindClass(controller.name, cl))
        check(!controllerClass.isInterface && !Modifier.isAbstract(controllerClass.modifiers))
        val webViewField = field.getFieldInstance(cl)
        check(ScanReflection.collectInstanceFields(controllerClass).filter { it.type == webClass }
            .singleOrNull() == webViewField) { "controller TbWebView field is ambiguous" }
        check(getter.getMethodInstance(cl).declaringClass.isAssignableFrom(controllerClass))
        load.getMethodInstance(cl)
        setForceCommon.getMethodInstance(cl)
        log(logger, "enterForumWeb matched: load=${load.descriptor}, webView=${field.descriptor}, " +
            "policy=${setForceCommon.descriptor}")
        return EnterForumWebScanSymbols(
            controllerClass = controller.name,
            webLoadMethod = load.methodName,
            webViewFieldOwnerClass = field.declaredClassName,
            webViewField = field.fieldName,
            setForceCommonMethod = setForceCommon.methodName,
        )
    }

    private fun unique(role: String, candidates: List<MethodData>): MethodData {
        check(candidates.size == 1) {
            "$role candidates=" + candidates.joinToString(",") { it.descriptor }.ifEmpty { "-" }
        }
        return candidates.single()
    }

    private fun instance(method: MethodData, returns: String, vararg params: String) =
        !Modifier.isStatic(method.modifiers) && !method.isConstructor &&
            method.returnTypeName == returns && method.paramTypeNames == params.toList()

    private fun MethodData.calls(target: MethodData) = invokes.any { it.descriptor == target.descriptor }

    private fun scanInitInfoData(cl: ClassLoader, logger: ScanLogger?): EnterForumWebScanSymbols {
        val cls = safeFindClass(INIT_INFO_DATA_CLASS, cl)
        if (cls == null) {
            log(logger, "enterForumWeb.initInfoData: class not found: $INIT_INFO_DATA_CLASS")
            return EnterForumWebScanSymbols()
        }
        val getter = cls.methods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == INIT_INFO_GET_URL_METHOD &&
                method.returnType == String::class.java &&
                method.parameterTypes.isEmpty()
        }
        if (getter == null) {
            log(
                logger,
                "enterForumWeb.initInfoData: method not found: " +
                    "$INIT_INFO_DATA_CLASS.$INIT_INFO_GET_URL_METHOD()",
            )
            return EnterForumWebScanSymbols()
        }
        log(logger, "enterForumWeb.initInfoData matched: ${cls.name}.${getter.name}")
        return EnterForumWebScanSymbols(
            initInfoDataClass = cls.name,
            initInfoGetUrlMethod = getter.name,
        )
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
