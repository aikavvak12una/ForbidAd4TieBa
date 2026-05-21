package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

object PbAdRequestBlockHook {
    private const val PB_PAGE_REQUEST_MESSAGE_CLASS = "com.baidu.tieba.pb.PbPageRequestMessage"
    private const val PAGE_BROWSER_REQUEST_MESSAGE_CLASS =
        "com.baidu.tieba.pb.pagebrowser.net.PageBrowserRequestMessage"
    private const val PB_LIST_DATA_REQ_BUILDER_CLASS = "tbclient.PbList.DataReq\$Builder"
    private const val COMMON_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
    private const val PAGE_BROWSER_BASE_REQUEST_MODEL_CLASS =
        "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
    private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"

    private data class FieldPatch(
        val field: Field,
        val value: Any?,
    )

    @Volatile private var hooked = false
    private val pbPageClearWarned = AtomicBoolean(false)
    private val commonNotifyWarned = AtomicBoolean(false)

    fun hook(cl: ClassLoader, symbols: HookSymbols? = HookSymbolResolver.getMemorySymbols()) {
        if (!ConfigManager.isAdBlockEnabled) {
            XposedCompat.log("[PbAdRequestBlockHook] skipped: config disabled")
            return
        }
        if (XposedCompat.module == null) return
        if (!tryMarkHooked()) return

        try {
            var installed = 0
            installed += installPbPageRequestMessageHook(cl)
            installed += installPageBrowserRequestMessageHook(cl)
            installed += installCommonAdBidShortCircuit(cl, symbols)
            installed += installPageBrowserAdBidShortCircuit(cl, symbols)

            if (installed == 0) {
                resetHooked()
                XposedCompat.log("[PbAdRequestBlockHook] no hooks installed")
                return
            }
            XposedCompat.log("[PbAdRequestBlockHook] hooks INSTALLED: count=$installed")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("[PbAdRequestBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun installPbPageRequestMessageHook(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val requestClass = XposedCompat.findClassOrNull(PB_PAGE_REQUEST_MESSAGE_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_PAGE_REQUEST_MESSAGE_CLASS")
            return 0
        }
        val encodeMethod = XposedCompat.findMethodOrNull(
            requestClass,
            "encode",
            Boolean::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] method NOT FOUND: $PB_PAGE_REQUEST_MESSAGE_CLASS.encode(boolean)")
            return 0
        }
        val patches = resolvePbPageRequestFieldPatches(requestClass)
        if (patches.isEmpty()) {
            XposedCompat.log("[PbAdRequestBlockHook] skipped PbPageRequestMessage: ad fields not resolved")
            return 0
        }

        mod.hook(encodeMethod).intercept { chain ->
            if (ConfigManager.isAdBlockEnabled) {
                clearPbPageAdRequestFields(chain.thisObject, patches)
            }
            chain.proceed()
        }
        return 1
    }

    private fun installPageBrowserRequestMessageHook(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val requestClass = XposedCompat.findClassOrNull(PAGE_BROWSER_REQUEST_MESSAGE_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PAGE_BROWSER_REQUEST_MESSAGE_CLASS")
            return 0
        }
        val builderClass = XposedCompat.findClassOrNull(PB_LIST_DATA_REQ_BUILDER_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_LIST_DATA_REQ_BUILDER_CLASS")
            return 0
        }
        val addAdMethod = XposedCompat.findMethodOrNull(
            requestClass,
            "addAdRequestMessage",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            builderClass,
        ) ?: run {
            XposedCompat.log(
                "[PbAdRequestBlockHook] method NOT FOUND: " +
                    "$PAGE_BROWSER_REQUEST_MESSAGE_CLASS.addAdRequestMessage(int,int,DataReq.Builder)",
            )
            return 0
        }

        mod.hook(addAdMethod).intercept { chain ->
            if (ConfigManager.isAdBlockEnabled) {
                return@intercept null
            }
            chain.proceed()
        }
        return 1
    }

    private fun installCommonAdBidShortCircuit(cl: ClassLoader, symbols: HookSymbols?): Int {
        val mod = XposedCompat.module ?: return 0
        val resolvedSymbols = symbols ?: return 0
        val modelClassName = resolvedSymbols.pbAdBidCommonRequestModelClass?.takeIf { it.isNotBlank() } ?: return 0
        val startMethodNames = resolvedSymbols.pbAdBidCommonRequestStartMethods.orEmpty().filter { it.isNotBlank() }
        val notifyMethodName = resolvedSymbols.pbAdBidCommonRequestNotifyMethod?.takeIf { it.isNotBlank() }
        if (startMethodNames.isEmpty() || notifyMethodName == null) {
            return 0
        }

        val commonBaseClass = XposedCompat.findClassOrNull(COMMON_REQUEST_MODEL_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $COMMON_REQUEST_MODEL_CLASS")
            return 0
        }
        val targetModelClass = XposedCompat.findClassOrNull(modelClassName, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $modelClassName")
            return 0
        }
        if (!commonBaseClass.isAssignableFrom(targetModelClass)) {
            XposedCompat.log("[PbAdRequestBlockHook] skipped common AdBid: target is not CommonRequestModel")
            return 0
        }

        val notifyMethod = findInstanceMethod(
            commonBaseClass,
            notifyMethodName,
            Void.TYPE,
            Int::class.javaPrimitiveType!!,
        ) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] method NOT FOUND: $COMMON_REQUEST_MODEL_CLASS.$notifyMethodName(int)")
            return 0
        }

        var installed = 0
        for (methodName in startMethodNames.distinct()) {
            val startMethod = findInstanceMethod(commonBaseClass, methodName, Void.TYPE)
            if (startMethod == null) {
                XposedCompat.logD("[PbAdRequestBlockHook] common AdBid start method not resolved: $methodName")
                continue
            }
            mod.hook(startMethod).intercept { chain ->
                val model = chain.thisObject
                if (!ConfigManager.isAdBlockEnabled || model == null || !targetModelClass.isInstance(model)) {
                    return@intercept chain.proceed()
                }
                notifyCommonAdBidFailure(model, notifyMethod)
                null
            }
            installed++
        }
        return installed
    }

    private fun installPageBrowserAdBidShortCircuit(cl: ClassLoader, symbols: HookSymbols?): Int {
        val mod = XposedCompat.module ?: return 0
        val resolvedSymbols = symbols ?: return 0
        val modelClassName = resolvedSymbols.pbAdBidPageBrowserRequestModelClass?.takeIf { it.isNotBlank() } ?: return 0
        val requestDataMethodName =
            resolvedSymbols.pbAdBidPageBrowserRequestDataMethod?.takeIf { it.isNotBlank() } ?: return 0
        val targetModelClass = XposedCompat.findClassOrNull(modelClassName, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $modelClassName")
            return 0
        }
        val continuationClass = XposedCompat.findClassOrNull(KOTLIN_CONTINUATION_CLASS, cl) ?: run {
            XposedCompat.log("[PbAdRequestBlockHook] class NOT FOUND: $KOTLIN_CONTINUATION_CLASS")
            return 0
        }
        XposedCompat.findClassOrNull(PAGE_BROWSER_BASE_REQUEST_MODEL_CLASS, cl)
            ?.takeIf { !it.isAssignableFrom(targetModelClass) }
            ?.let { XposedCompat.logD("[PbAdRequestBlockHook] pagebrowser AdBid target is outside legacy BaseRequestModel") }

        val requestDataMethod = findInstanceMethodInHierarchy(
            targetModelClass,
            requestDataMethodName,
            Any::class.java,
            continuationClass,
        ) ?: run {
            XposedCompat.log(
                "[PbAdRequestBlockHook] method NOT FOUND: " +
                    "$modelClassName.$requestDataMethodName(Continuation)",
            )
            return 0
        }

        mod.hook(requestDataMethod).intercept { chain ->
            val model = chain.thisObject
            if (!ConfigManager.isAdBlockEnabled || model == null || !targetModelClass.isInstance(model)) {
                return@intercept chain.proceed()
            }
            null
        }
        return 1
    }

    private fun resolvePbPageRequestFieldPatches(requestClass: Class<*>): List<FieldPatch> {
        val patches = ArrayList<FieldPatch>(5)
        fun add(name: String, value: Any?) {
            val field = try {
                XposedCompat.findField(requestClass, name)
            } catch (_: Throwable) {
                null
            } ?: return
            patches.add(FieldPatch(field, value))
        }
        add("adxBearBannerStr", "")
        add("adxBearCommentStr", "")
        add("adExternalBannerStr", "")
        add("adExternalCommentStr", "")
        add("isReqAd", 0)
        return patches
    }

    private fun clearPbPageAdRequestFields(message: Any?, patches: List<FieldPatch>) {
        if (message == null) return
        try {
            for (patch in patches) {
                patch.field.set(message, patch.value)
            }
        } catch (t: Throwable) {
            if (pbPageClearWarned.compareAndSet(false, true)) {
                XposedCompat.log("[PbAdRequestBlockHook] clear PbPageRequestMessage ad fields FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        }
    }

    private fun notifyCommonAdBidFailure(model: Any, notifyMethod: Method) {
        try {
            notifyMethod.invoke(model, -2)
        } catch (t: Throwable) {
            if (commonNotifyWarned.compareAndSet(false, true)) {
                XposedCompat.log("[PbAdRequestBlockHook] notify common AdBid failure FAILED: ${t.message}")
                XposedCompat.log(t)
            }
        }
    }

    private fun findInstanceMethod(
        clazz: Class<*>,
        name: String,
        returnType: Class<*>,
        vararg paramTypes: Class<*>,
    ): Method? {
        return try {
            clazz.getDeclaredMethod(name, *paramTypes).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == returnType
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private fun findInstanceMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        returnType: Class<*>,
        vararg paramTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            findInstanceMethod(current, name, returnType, *paramTypes)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }
}
