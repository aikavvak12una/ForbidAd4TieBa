package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.listTarget
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PlainUrlBrowserHelperScanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlBrowserHelperSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlClickableSpanScanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlClickableSpanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDataSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDispatchScanSymbols
import com.forbidad4tieba.hook.symbol.model.PlainUrlMessageDispatchSymbols
import com.forbidad4tieba.hook.symbol.scan.PlainUrlBrowserHelperSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PlainUrlClickableSpanSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.PlainUrlMessageSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Owns the cached descriptors and host rules for this capability. */
object PlainUrlContract : SymbolContract("PlainUrl") {
    val plainUrlClickableSpanClass = text("plainUrlClickableSpanClass")
    val plainUrlClickableSpanOnClickMethod = text("plainUrlClickableSpanOnClickMethod")
    val plainUrlClickableSpanOnClickOwnerClasses = texts("plainUrlClickableSpanOnClickOwnerClasses", preserveEmpty = false)
    val plainUrlClickableSpanTypeField = text("plainUrlClickableSpanTypeField")
    val plainUrlClickableSpanUrlField = text("plainUrlClickableSpanUrlField")
    val plainUrlClickableSpanTextField = text("plainUrlClickableSpanTextField")
    val plainUrlMessageManagerClass = text("plainUrlMessageManagerClass")
    val plainUrlMessageDispatchMethod = text("plainUrlMessageDispatchMethod")
    val plainUrlResponsedMessageClass = text("plainUrlResponsedMessageClass")
    val plainUrlResponsedMessageGetCmdMethod = text("plainUrlResponsedMessageGetCmdMethod")
    val plainUrlCustomResponsedMessageClass = text("plainUrlCustomResponsedMessageClass")
    val plainUrlCustomResponsedMessageGetDataMethod = text("plainUrlCustomResponsedMessageGetDataMethod")
    val plainUrlApplicationClass = text("plainUrlApplicationClass")
    val plainUrlApplicationGetInstMethod = text("plainUrlApplicationGetInstMethod")
    val plainUrlBrowserHelperClass = text("plainUrlBrowserHelperClass")
    val plainUrlBrowserHelperStartWebActivityMethod = text("plainUrlBrowserHelperStartWebActivityMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        val plainUrlSpanScan = runScanStep(
            "PlainUrlDirectBrowserHook.Direct",
            logger,
            scanErrors,
            PlainUrlClickableSpanScanSymbols(),
        ) {
            PlainUrlClickableSpanSymbolScanner.scan(context, candidatesWithWhitelist, cl, logger)
        }

        val plainUrlClickableSpanClass: String? = plainUrlSpanScan.className

        val plainUrlClickableSpanOnClickMethod: String? = plainUrlSpanScan.onClickMethod

        val plainUrlClickableSpanOnClickOwnerClasses: List<String>? = plainUrlSpanScan.onClickOwnerClasses

        val plainUrlClickableSpanTypeField: String? = plainUrlSpanScan.typeField

        val plainUrlClickableSpanUrlField: String? = plainUrlSpanScan.urlField

        val plainUrlClickableSpanTextField: String? = plainUrlSpanScan.textField

        val plainUrlMessageScan = runScanStep(
            "PlainUrlDirectBrowserHook.Message",
            logger,
            scanErrors,
            PlainUrlMessageDispatchScanSymbols(),
        ) {
            PlainUrlMessageSymbolScanner.scan(cl, logger)
        }

        val plainUrlMessageManagerClass: String? = plainUrlMessageScan.messageManagerClass

        val plainUrlMessageDispatchMethod: String? = plainUrlMessageScan.dispatchMethod

        val plainUrlResponsedMessageClass: String? = plainUrlMessageScan.responsedMessageClass

        val plainUrlResponsedMessageGetCmdMethod: String? = plainUrlMessageScan.getCmdMethod

        val plainUrlCustomResponsedMessageClass: String? = plainUrlMessageScan.customResponsedMessageClass

        val plainUrlCustomResponsedMessageGetDataMethod: String? = plainUrlMessageScan.getDataMethod

        val plainUrlApplicationClass: String? = plainUrlMessageScan.applicationClass

        val plainUrlApplicationGetInstMethod: String? = plainUrlMessageScan.getInstMethod

        val plainUrlBrowserHelperScan = runScanStep(
            "PlainUrlDirectBrowserHook.BrowserHelper",
            logger,
            scanErrors,
            PlainUrlBrowserHelperScanSymbols(),
        ) {
            PlainUrlBrowserHelperSymbolScanner.scan(cl, logger)
        }

        val plainUrlBrowserHelperClass: String? = plainUrlBrowserHelperScan.browserHelperClass

        val plainUrlBrowserHelperStartWebActivityMethod: String? = plainUrlBrowserHelperScan.startWebActivityMethod

        output[PlainUrlContract.plainUrlClickableSpanClass] = plainUrlClickableSpanClass
        output[PlainUrlContract.plainUrlClickableSpanOnClickMethod] = plainUrlClickableSpanOnClickMethod
        output[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses] = plainUrlClickableSpanOnClickOwnerClasses
        output[PlainUrlContract.plainUrlClickableSpanTypeField] = plainUrlClickableSpanTypeField
        output[PlainUrlContract.plainUrlClickableSpanUrlField] = plainUrlClickableSpanUrlField
        output[PlainUrlContract.plainUrlClickableSpanTextField] = plainUrlClickableSpanTextField
        output[PlainUrlContract.plainUrlMessageManagerClass] = plainUrlMessageManagerClass
        output[PlainUrlContract.plainUrlMessageDispatchMethod] = plainUrlMessageDispatchMethod
        output[PlainUrlContract.plainUrlResponsedMessageClass] = plainUrlResponsedMessageClass
        output[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod] = plainUrlResponsedMessageGetCmdMethod
        output[PlainUrlContract.plainUrlCustomResponsedMessageClass] = plainUrlCustomResponsedMessageClass
        output[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod] = plainUrlCustomResponsedMessageGetDataMethod
        output[PlainUrlContract.plainUrlApplicationClass] = plainUrlApplicationClass
        output[PlainUrlContract.plainUrlApplicationGetInstMethod] = plainUrlApplicationGetInstMethod
        output[PlainUrlContract.plainUrlBrowserHelperClass] = plainUrlBrowserHelperClass
        output[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod] = plainUrlBrowserHelperStartWebActivityMethod
    }

    private const val PLAIN_URL_CLICK_MESSAGE_CMD = 2001332

    private val plainUrlMessageDataSymbolsCache = ConcurrentHashMap<Class<*>, PlainUrlMessageDataSymbols>()

    fun resolvePlainUrlClickableSpanSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PlainUrlClickableSpanSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: scan symbols unavailable")
                return null
            }
            val className = resolvedSymbols[PlainUrlContract.plainUrlClickableSpanClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanClass")
                return null
            }
            val onClickMethodName =
                resolvedSymbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanOnClickMethod")
                    return null
                }
            val ownerClassNames = resolvedSymbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses]
                .orEmpty()
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf(className) }
            val typeFieldName = resolvedSymbols[PlainUrlContract.plainUrlClickableSpanTypeField]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanTypeField")
                return null
            }
            val urlFieldName = resolvedSymbols[PlainUrlContract.plainUrlClickableSpanUrlField]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanUrlField")
                return null
            }
            val textFieldName = resolvedSymbols[PlainUrlContract.plainUrlClickableSpanTextField]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: missing plainUrlClickableSpanTextField")
                return null
            }

            val spanClass = ScanReflection.safeFindClass(className, cl) ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: class not found: $className")
                return null
            }
            val onClickMethods = ownerClassNames.mapNotNull { ownerClassName ->
                val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: return@mapNotNull null
                if (!spanClass.isAssignableFrom(ownerClass)) return@mapNotNull null
                ownerClass.declaredMethods.singleOrNull { method ->
                    PlainUrlClickableSpanSymbolScanner.isOnClickMethod(method, onClickMethodName)
                }
            }.distinctBy { "${it.declaringClass.name}#${it.name}" }
            if (onClickMethods.isEmpty()) {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: onClick methods mismatch: $onClickMethodName owners=$ownerClassNames")
                return null
            }
            val fields = ScanReflection.collectInstanceFields(spanClass)
            val typeField = fields.singleOrNull { field ->
                field.name == typeFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    ScanReflection.isIntType(field.type)
            } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: type field mismatch: $className.$typeFieldName")
                return null
            }
            val urlField = fields.singleOrNull { field ->
                field.name == urlFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: url field mismatch: $className.$urlFieldName")
                return null
            }
            val textField = fields.singleOrNull { field ->
                field.name == textFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: text field mismatch: $className.$textFieldName")
                return null
            }

            if (!PlainUrlClickableSpanSymbolScanner.isStructureValid(spanClass, onClickMethods.first(), typeField, urlField, textField)) {
                Diagnostics.log("[PlainUrlDirectBrowserHook] skipped: span structure mismatch: $className")
                return null
            }

            onClickMethods.forEach { it.isAccessible = true }
            typeField.isAccessible = true
            urlField.isAccessible = true
            textField.isAccessible = true
            PlainUrlClickableSpanSymbols(
                spanClass = spanClass,
                onClickMethods = onClickMethods,
                typeField = typeField,
                urlField = urlField,
                textField = textField,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PlainUrlDirectBrowserHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolvePlainUrlMessageDispatchSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PlainUrlMessageDispatchSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: scan symbols unavailable")
                return null
            }
            val messageManagerClassName = resolvedSymbols[PlainUrlContract.plainUrlMessageManagerClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlMessageManagerClass")
                return null
            }
            val dispatchMethodName = resolvedSymbols[PlainUrlContract.plainUrlMessageDispatchMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlMessageDispatchMethod")
                return null
            }
            val responsedMessageClassName =
                resolvedSymbols[PlainUrlContract.plainUrlResponsedMessageClass]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlResponsedMessageClass")
                    return null
                }
            val getCmdMethodName =
                resolvedSymbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlResponsedMessageGetCmdMethod")
                    return null
                }
            val customResponsedMessageClassName =
                resolvedSymbols[PlainUrlContract.plainUrlCustomResponsedMessageClass]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlCustomResponsedMessageClass")
                    return null
                }
            val getDataMethodName =
                resolvedSymbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlCustomResponsedMessageGetDataMethod")
                    return null
                }
            val applicationClassName = resolvedSymbols[PlainUrlContract.plainUrlApplicationClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlApplicationClass")
                return null
            }
            val getInstMethodName = resolvedSymbols[PlainUrlContract.plainUrlApplicationGetInstMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] message skipped: missing plainUrlApplicationGetInstMethod")
                return null
            }

            val messageManagerClass = ScanReflection.safeFindClass(messageManagerClassName, cl) ?: return null
            val responsedMessageClass = ScanReflection.safeFindClass(responsedMessageClassName, cl) ?: return null
            val customResponsedMessageClass = ScanReflection.safeFindClass(customResponsedMessageClassName, cl) ?: return null
            val applicationClass = ScanReflection.safeFindClass(applicationClassName, cl) ?: return null
            val dispatchMethod = messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == dispatchMethodName &&
                    ScanReflection.isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
            } ?: return null
            val getCmdMethod = responsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getCmdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isIntType(method.returnType)
            } ?: return null
            val getDataMethod = customResponsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Any::class.java
            } ?: return null
            val getInstMethod = applicationClass.declaredMethods.singleOrNull { method ->
                method.name == getInstMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    applicationClass.isAssignableFrom(method.returnType)
            } ?: return null

            listOf(dispatchMethod, getCmdMethod, getDataMethod, getInstMethod).forEach { it.isAccessible = true }
            PlainUrlMessageDispatchSymbols(
                messageManagerClass = messageManagerClass,
                dispatchMethod = dispatchMethod,
                responsedMessageClass = responsedMessageClass,
                getCmdMethod = getCmdMethod,
                customResponsedMessageClass = customResponsedMessageClass,
                getDataMethod = getDataMethod,
                applicationClass = applicationClass,
                getInstMethod = getInstMethod,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PlainUrlDirectBrowserHook] message symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolvePlainUrlBrowserHelperSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PlainUrlBrowserHelperSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] browser helper skipped: scan symbols unavailable")
                return null
            }
            val browserHelperClassName =
                resolvedSymbols[PlainUrlContract.plainUrlBrowserHelperClass]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] browser helper skipped: missing class")
                    return null
                }
            val startWebActivityMethodName =
                resolvedSymbols[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod]?.takeIf { it.isNotBlank() } ?: run {
                    Diagnostics.log("[PlainUrlDirectBrowserHook] browser helper skipped: missing startWebActivity")
                    return null
                }
            val browserHelperClass = ScanReflection.safeFindClass(browserHelperClassName, cl) ?: run {
                Diagnostics.log("[PlainUrlDirectBrowserHook] browser helper skipped: class not found: $browserHelperClassName")
                return null
            }
            val startWebActivityMethods = browserHelperClass.declaredMethods
                .filter { method ->
                    PlainUrlBrowserHelperSymbolScanner.isStartWebActivityMethod(
                        method,
                        startWebActivityMethodName,
                    )
                }
                .distinctBy { method ->
                    method.name + "#" + method.parameterTypes.joinToString(",") { it.name }
                }
            if (startWebActivityMethods.isEmpty()) {
                Diagnostics.log(
                    "[PlainUrlDirectBrowserHook] browser helper skipped: startWebActivity overloads mismatch",
                )
                return null
            }
            startWebActivityMethods.forEach { it.isAccessible = true }
            PlainUrlBrowserHelperSymbols(
                browserHelperClass = browserHelperClass,
                startWebActivityMethods = startWebActivityMethods,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PlainUrlDirectBrowserHook] browser helper symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    fun resolvePlainUrlMessageDataSymbols(data: Any): PlainUrlMessageDataSymbols? {
        val dataClass = data.javaClass
        plainUrlMessageDataSymbolsCache[dataClass]?.let { return it }
        val fields = ScanReflection.collectInstanceFields(dataClass)
        val intFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && ScanReflection.isIntType(field.type)
        }
        val stringFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == String::class.java
        }
        val typeField = intFields.firstOrNull() ?: return null
        val urlField = stringFields.firstOrNull() ?: return null
        val textField = stringFields.getOrNull(1) ?: urlField
        listOf(typeField, urlField, textField).forEach { it.isAccessible = true }
        val resolved = PlainUrlMessageDataSymbols(typeField, urlField, textField)
        plainUrlMessageDataSymbolsCache[dataClass] = resolved
        return resolved
    }

    fun isPlainUrlClickMessageCmd(cmd: Int): Boolean {
        return cmd == PLAIN_URL_CLICK_MESSAGE_CMD
    }

    fun resolvePlainUrlClickSpanMarkerField(cl: ClassLoader): Field? {
        return try {
            val tbSingletonClass = ScanReflection.safeFindClass(StableTiebaHookPoints.TB_SINGLETON_CLASS, cl) ?: return null
            tbSingletonClass.declaredFields.singleOrNull { field ->
                field.name == "isClickSpan" &&
                    Modifier.isStatic(field.modifiers) &&
                    field.type == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
        } catch (t: Throwable) {
            Diagnostics.log("[PlainUrlDirectBrowserHook] click span marker resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> {
        val out = linkedMapOf<String, HookFeatureStatus>()
        val plainUrlDirectMissing = ArrayList<String>(6)
        if (symbols[PlainUrlContract.plainUrlClickableSpanClass].isNullOrBlank()) plainUrlDirectMissing.add("plainUrlClickableSpanClass")
        if (symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickMethod")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses].isNullOrEmpty()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanOnClickOwnerClasses")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanTypeField].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTypeField")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanUrlField].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanUrlField")
        }
        if (symbols[PlainUrlContract.plainUrlClickableSpanTextField].isNullOrBlank()) {
            plainUrlDirectMissing.add("plainUrlClickableSpanTextField")
        }
        val plainUrlMessageMissing = ArrayList<String>(8)
        if (symbols[PlainUrlContract.plainUrlMessageManagerClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageManagerClass")
        }
        if (symbols[PlainUrlContract.plainUrlMessageDispatchMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlMessageDispatchMethod")
        }
        if (symbols[PlainUrlContract.plainUrlResponsedMessageClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageClass")
        }
        if (symbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlResponsedMessageGetCmdMethod")
        }
        if (symbols[PlainUrlContract.plainUrlCustomResponsedMessageClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageClass")
        }
        if (symbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlCustomResponsedMessageGetDataMethod")
        }
        if (symbols[PlainUrlContract.plainUrlApplicationClass].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationClass")
        }
        if (symbols[PlainUrlContract.plainUrlApplicationGetInstMethod].isNullOrBlank()) {
            plainUrlMessageMissing.add("plainUrlApplicationGetInstMethod")
        }
        val mountCardMissing = ArrayList<String>(5)
        if (symbols[MountCardContract.mountCardLinkLayoutClass].isNullOrBlank()) mountCardMissing.add("mountCardLinkLayoutClass")
        if (symbols[MountCardContract.mountCardLinkLayoutOnClickMethod].isNullOrBlank()) {
            mountCardMissing.add("mountCardLinkLayoutOnClickMethod")
        }
        if (symbols[MountCardContract.mountCardLinkLayoutDataField].isNullOrBlank()) mountCardMissing.add("mountCardLinkLayoutDataField")
        if (symbols[MountCardContract.mountCardLinkInfoDataClass].isNullOrBlank()) mountCardMissing.add("mountCardLinkInfoDataClass")
        if (symbols[MountCardContract.mountCardLinkInfoGetUrlMethod].isNullOrBlank()) {
            mountCardMissing.add("mountCardLinkInfoGetUrlMethod")
        }
        val browserHelperMissing = ArrayList<String>(2)
        if (symbols[PlainUrlContract.plainUrlBrowserHelperClass].isNullOrBlank()) {
            browserHelperMissing.add("plainUrlBrowserHelperClass")
        }
        if (symbols[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod].isNullOrBlank()) {
            browserHelperMissing.add("plainUrlBrowserHelperStartWebActivityMethod")
        }
        val plainUrlDirectReady = plainUrlDirectMissing.isEmpty()
        val plainUrlMessageReady = plainUrlMessageMissing.isEmpty()
        val generalPlainUrlReady = plainUrlMessageReady || plainUrlDirectReady
        val mountCardReady = mountCardMissing.isEmpty()
        val browserHelperReady = browserHelperMissing.isEmpty()
        val plainUrlOptionalMissing = buildList {
            if (!plainUrlMessageReady) addAll(plainUrlMessageMissing)
            if (!mountCardReady) addAll(mountCardMissing)
            if (!browserHelperReady) addAll(browserHelperMissing)
        }.distinct()
        out[HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER] = when {
            browserHelperReady && plainUrlMessageReady && mountCardReady -> {
                HookFeatureStatus(state = HookFeatureState.FULL)
            }
            browserHelperReady || generalPlainUrlReady || mountCardReady -> HookFeatureStatus(
                state = HookFeatureState.PARTIAL,
                missingOptional = plainUrlOptionalMissing,
            )
            else -> HookFeatureStatus(
                state = HookFeatureState.DISABLED,
                missingCritical = (
                    browserHelperMissing +
                        plainUrlMessageMissing +
                        mountCardMissing
                    ).distinct(),
                missingOptional = plainUrlDirectMissing,
            )
        }
        return out
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        addOptional(
            "PlainUrlDirectBrowserHook.Direct",
            "${listTarget(symbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses])}.${symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod]}(View)[${symbols[PlainUrlContract.plainUrlClickableSpanTypeField]},${symbols[PlainUrlContract.plainUrlClickableSpanUrlField]},${symbols[PlainUrlContract.plainUrlClickableSpanTextField]}]",
            listOf(
                PlainUrlContract.plainUrlClickableSpanClass.check(symbols),
                PlainUrlContract.plainUrlClickableSpanOnClickMethod.check(symbols),
                PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses.check(symbols),
                PlainUrlContract.plainUrlClickableSpanTypeField.check(symbols),
                PlainUrlContract.plainUrlClickableSpanUrlField.check(symbols),
                PlainUrlContract.plainUrlClickableSpanTextField.check(symbols),
            ),
        )
        addOptional(
            "PlainUrlDirectBrowserHook.Message",
            "${symbols[PlainUrlContract.plainUrlMessageManagerClass]}.${symbols[PlainUrlContract.plainUrlMessageDispatchMethod]}(${symbols[PlainUrlContract.plainUrlResponsedMessageClass]})",
            listOf(
                PlainUrlContract.plainUrlMessageManagerClass.check(symbols),
                PlainUrlContract.plainUrlMessageDispatchMethod.check(symbols),
                PlainUrlContract.plainUrlResponsedMessageClass.check(symbols),
                PlainUrlContract.plainUrlResponsedMessageGetCmdMethod.check(symbols),
                PlainUrlContract.plainUrlCustomResponsedMessageClass.check(symbols),
                PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod.check(symbols),
                PlainUrlContract.plainUrlApplicationClass.check(symbols),
                PlainUrlContract.plainUrlApplicationGetInstMethod.check(symbols),
            ),
        )
        add(
            "PlainUrlDirectBrowserHook.BrowserHelper",
            "${symbols[PlainUrlContract.plainUrlBrowserHelperClass]}.${symbols[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod]}(Context,String/Uri...)",
            listOf(
                PlainUrlContract.plainUrlBrowserHelperClass.check(symbols),
                "plainUrlBrowserHelperStartWebActivityMethod" to
                    has(symbols[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod]),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PlainUrlDirectBrowserHook.Direct", false, listOf(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER, HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE)),
        PointOwner("PlainUrlDirectBrowserHook.Message", false, listOf(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER, HookFeatureKey.ENABLE_COMMENT_AVATAR_DIRECT_PROFILE)),
        PointOwner("PlainUrlDirectBrowserHook.", true, listOf(HookFeatureKey.OPEN_WEB_LINK_IN_SYSTEM_BROWSER)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasPlainUrlSymbols =
                symbols[PlainUrlContract.plainUrlClickableSpanClass] != null ||
                symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod] != null ||
                !symbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses].isNullOrEmpty() ||
                symbols[PlainUrlContract.plainUrlClickableSpanTypeField] != null ||
                symbols[PlainUrlContract.plainUrlClickableSpanUrlField] != null ||
                symbols[PlainUrlContract.plainUrlClickableSpanTextField] != null ||
                symbols[PlainUrlContract.plainUrlMessageManagerClass] != null ||
                symbols[PlainUrlContract.plainUrlMessageDispatchMethod] != null ||
                symbols[PlainUrlContract.plainUrlResponsedMessageClass] != null ||
                symbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod] != null ||
                symbols[PlainUrlContract.plainUrlCustomResponsedMessageClass] != null ||
                symbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod] != null ||
                symbols[PlainUrlContract.plainUrlApplicationClass] != null ||
                symbols[PlainUrlContract.plainUrlApplicationGetInstMethod] != null ||
                symbols[PlainUrlContract.plainUrlBrowserHelperClass] != null ||
                symbols[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod] != null
        if (hasPlainUrlSymbols && !isPlainUrlValid(symbols, cl)) return false
        return true
    }

    private fun isPlainUrlValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        return isPlainUrlClickableSpanDirectValid(symbols, cl) ||
            isPlainUrlMessageDispatchValid(symbols, cl) ||
            isPlainUrlBrowserHelperValid(symbols, cl)
    }

    private fun isPlainUrlClickableSpanDirectValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[PlainUrlContract.plainUrlClickableSpanClass] ?: return false
        val methodName = symbols[PlainUrlContract.plainUrlClickableSpanOnClickMethod] ?: return false
        val ownerClassNames = symbols[PlainUrlContract.plainUrlClickableSpanOnClickOwnerClasses].orEmpty()
        if (ownerClassNames.isEmpty()) return false
        val typeFieldName = symbols[PlainUrlContract.plainUrlClickableSpanTypeField] ?: return false
        val urlFieldName = symbols[PlainUrlContract.plainUrlClickableSpanUrlField] ?: return false
        val textFieldName = symbols[PlainUrlContract.plainUrlClickableSpanTextField] ?: return false
        return try {
            val spanClass = ScanReflection.safeFindClass(className, cl) ?: return false
            val onClickMethods = ownerClassNames.mapNotNull { ownerClassName ->
                val ownerClass = ScanReflection.safeFindClass(ownerClassName, cl) ?: return@mapNotNull null
                if (!spanClass.isAssignableFrom(ownerClass)) return@mapNotNull null
                ownerClass.declaredMethods.singleOrNull { method ->
                    PlainUrlClickableSpanSymbolScanner.isOnClickMethod(method, methodName)
                }
            }
            if (onClickMethods.size != ownerClassNames.size || onClickMethods.isEmpty()) return false
            val fields = ScanReflection.collectInstanceFields(spanClass)
            val typeField = fields.singleOrNull { field ->
                field.name == typeFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    ScanReflection.isIntType(field.type)
            } ?: return false
            val urlField = fields.singleOrNull { field ->
                field.name == urlFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: return false
            val textField = fields.singleOrNull { field ->
                field.name == textFieldName &&
                    !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java
            } ?: return false
            PlainUrlClickableSpanSymbolScanner.isStructureValid(spanClass, onClickMethods.first(), typeField, urlField, textField)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPlainUrlMessageDispatchValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val messageManagerClassName = symbols[PlainUrlContract.plainUrlMessageManagerClass] ?: return false
        val dispatchMethodName = symbols[PlainUrlContract.plainUrlMessageDispatchMethod] ?: return false
        val responsedMessageClassName = symbols[PlainUrlContract.plainUrlResponsedMessageClass] ?: return false
        val getCmdMethodName = symbols[PlainUrlContract.plainUrlResponsedMessageGetCmdMethod] ?: return false
        val customResponsedMessageClassName = symbols[PlainUrlContract.plainUrlCustomResponsedMessageClass] ?: return false
        val getDataMethodName = symbols[PlainUrlContract.plainUrlCustomResponsedMessageGetDataMethod] ?: return false
        val applicationClassName = symbols[PlainUrlContract.plainUrlApplicationClass] ?: return false
        val getInstMethodName = symbols[PlainUrlContract.plainUrlApplicationGetInstMethod] ?: return false
        return try {
            val messageManagerClass = ScanReflection.safeFindClass(messageManagerClassName, cl) ?: return false
            val responsedMessageClass = ScanReflection.safeFindClass(responsedMessageClassName, cl) ?: return false
            val customResponsedMessageClass = ScanReflection.safeFindClass(customResponsedMessageClassName, cl) ?: return false
            val applicationClass = ScanReflection.safeFindClass(applicationClassName, cl) ?: return false
            if (!responsedMessageClass.isAssignableFrom(customResponsedMessageClass)) return false
            messageManagerClass.declaredMethods.singleOrNull { method ->
                method.name == dispatchMethodName &&
                    ScanReflection.isPlainUrlMessageDispatchMethod(method, responsedMessageClass)
            } ?: return false
            responsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getCmdMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    ScanReflection.isIntType(method.returnType)
            } ?: return false
            customResponsedMessageClass.declaredMethods.singleOrNull { method ->
                method.name == getDataMethodName &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Any::class.java
            } ?: return false
            applicationClass.declaredMethods.singleOrNull { method ->
                method.name == getInstMethodName &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    applicationClass.isAssignableFrom(method.returnType)
            } != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun isPlainUrlBrowserHelperValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val className = symbols[PlainUrlContract.plainUrlBrowserHelperClass] ?: return false
        val methodName = symbols[PlainUrlContract.plainUrlBrowserHelperStartWebActivityMethod] ?: return false
        return try {
            val browserHelperClass = ScanReflection.safeFindClass(className, cl) ?: return false
            browserHelperClass.declaredMethods.any { method ->
                PlainUrlBrowserHelperSymbolScanner.isStartWebActivityMethod(method, methodName)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
