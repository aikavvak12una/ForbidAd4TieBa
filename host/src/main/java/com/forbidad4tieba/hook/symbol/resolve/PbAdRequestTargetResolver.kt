package com.forbidad4tieba.hook.symbol.resolve

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.PbAdRequestBlockSymbols
import com.forbidad4tieba.hook.symbol.model.PbAdRequestFieldPatchSymbols
import com.forbidad4tieba.hook.symbol.scan.ScanReflection.safeFindClass
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PbAdRequestTargetResolver {
    fun resolve(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PbAdRequestBlockSymbols? {
        return try {
            // Independent request paths must survive another path's reflection failure.
            val pbPage = recover("PbPageRequest") { resolvePbPageRequestMessageTargets(cl) }
            val pageBrowser = recover("PageBrowserRequest") { resolvePageBrowserRequestMessageTarget(cl) }
            val commonAdBid = recover("CommonAdBid") { resolveCommonAdBidTargets(cl, symbols) }
            val pageBrowserAdBid = recover("PageBrowserAdBid") { resolvePageBrowserAdBidTarget(cl, symbols) }
            if (
                pbPage == null &&
                pageBrowser == null &&
                commonAdBid == null &&
                pageBrowserAdBid == null
            ) {
                Diagnostics.log("[PbAdRequestBlockHook] skipped: no resolved install targets")
                return null
            }
            PbAdRequestBlockSymbols(
                pbPageEncodeMethod = pbPage?.first,
                pbPageFieldPatches = pbPage?.second.orEmpty(),
                pageBrowserAddAdMethod = pageBrowser,
                commonAdBidTargetClass = commonAdBid?.targetClass,
                commonAdBidStartMethods = commonAdBid?.startMethods.orEmpty(),
                commonAdBidNotifyMethod = commonAdBid?.notifyMethod,
                pageBrowserAdBidTargetClass = pageBrowserAdBid?.first,
                pageBrowserAdBidRequestDataMethod = pageBrowserAdBid?.second,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PbAdRequestBlockHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private inline fun <T> recover(role: String, resolve: () -> T?): T? {
        return try {
            resolve()
        } catch (failure: Throwable) {
            Diagnostics.log("[PbAdRequestBlockHook] $role symbol resolve FAILED: ${failure.message}")
            Diagnostics.log(failure)
            null
        }
    }

    private data class CommonAdBidTargets(
        val targetClass: Class<*>,
        val startMethods: List<Method>,
        val notifyMethod: Method,
    )

    private fun resolvePbPageRequestMessageTargets(cl: ClassLoader): Pair<Method, List<PbAdRequestFieldPatchSymbols>>? {
        val requestClass = safeFindClass(PB_PAGE_REQUEST_MESSAGE_CLASS, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_PAGE_REQUEST_MESSAGE_CLASS")
            return null
        }
        val encodeMethod = MemberAccess.findMethodOrNull(
            requestClass,
            "encode",
            Boolean::class.javaPrimitiveType!!,
        ) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] method NOT FOUND: $PB_PAGE_REQUEST_MESSAGE_CLASS.encode(boolean)")
            return null
        }
        val patches = resolvePbPageRequestFieldPatches(requestClass)
        if (patches.isEmpty()) {
            Diagnostics.log("[PbAdRequestBlockHook] skipped PbPageRequestMessage: ad fields not resolved")
            return null
        }
        encodeMethod.isAccessible = true
        return encodeMethod to patches
    }

    private fun resolvePageBrowserRequestMessageTarget(cl: ClassLoader): Method? {
        val requestClass = safeFindClass(PAGE_BROWSER_REQUEST_MESSAGE_CLASS, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $PAGE_BROWSER_REQUEST_MESSAGE_CLASS")
            return null
        }
        val builderClass = safeFindClass(PB_LIST_DATA_REQ_BUILDER_CLASS, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_LIST_DATA_REQ_BUILDER_CLASS")
            return null
        }
        return MemberAccess.findMethodOrNull(
            requestClass,
            "addAdRequestMessage",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            builderClass,
        )?.apply { isAccessible = true } ?: run {
            Diagnostics.log(
                "[PbAdRequestBlockHook] method NOT FOUND: " +
                    "$PAGE_BROWSER_REQUEST_MESSAGE_CLASS.addAdRequestMessage(int,int,DataReq.Builder)",
            )
            null
        }
    }

    private fun resolveCommonAdBidTargets(cl: ClassLoader, symbols: HookSymbols?): CommonAdBidTargets? {
        val resolvedSymbols = symbols ?: return null
        val modelClassName = resolvedSymbols[PbAdRequestContract.pbAdBidCommonRequestModelClass]?.takeIf { it.isNotBlank() } ?: return null
        val startMethodNames = resolvedSymbols[PbAdRequestContract.pbAdBidCommonRequestStartMethods].orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val notifyMethodName = resolvedSymbols[PbAdRequestContract.pbAdBidCommonRequestNotifyMethod]?.takeIf { it.isNotBlank() }
        if (startMethodNames.isEmpty() || notifyMethodName == null) return null

        val commonBaseClass = safeFindClass(PB_COMMON_REQUEST_MODEL_CLASS, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $PB_COMMON_REQUEST_MODEL_CLASS")
            return null
        }
        val targetModelClass = safeFindClass(modelClassName, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $modelClassName")
            return null
        }
        if (!commonBaseClass.isAssignableFrom(targetModelClass)) {
            Diagnostics.log("[PbAdRequestBlockHook] skipped common AdBid: target is not CommonRequestModel")
            return null
        }

        val notifyMethod = findPbAdRequestInstanceMethod(
            commonBaseClass,
            notifyMethodName,
            Void.TYPE,
            Int::class.javaPrimitiveType!!,
        ) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] method NOT FOUND: $PB_COMMON_REQUEST_MODEL_CLASS.$notifyMethodName(int)")
            return null
        }

        val startMethods = startMethodNames.mapNotNull { methodName ->
            findPbAdRequestInstanceMethod(commonBaseClass, methodName, Void.TYPE) ?: run {
                Diagnostics.logD("[PbAdRequestBlockHook] common AdBid start method not resolved: $methodName")
                null
            }
        }
        if (startMethods.isEmpty()) return null
        return CommonAdBidTargets(
            targetClass = targetModelClass,
            startMethods = startMethods,
            notifyMethod = notifyMethod,
        )
    }

    private fun resolvePageBrowserAdBidTarget(cl: ClassLoader, symbols: HookSymbols?): Pair<Class<*>, Method>? {
        val resolvedSymbols = symbols ?: return null
        val modelClassName = resolvedSymbols[PbAdRequestContract.pbAdBidPageBrowserRequestModelClass]?.takeIf { it.isNotBlank() }
            ?: return null
        val requestDataMethodName = resolvedSymbols[PbAdRequestContract.pbAdBidPageBrowserRequestDataMethod]?.takeIf { it.isNotBlank() }
            ?: return null
        val targetModelClass = safeFindClass(modelClassName, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $modelClassName")
            return null
        }
        val continuationClass = safeFindClass(KOTLIN_CONTINUATION_CLASS, cl) ?: run {
            Diagnostics.log("[PbAdRequestBlockHook] class NOT FOUND: $KOTLIN_CONTINUATION_CLASS")
            return null
        }
        val baseClass = safeFindClass(PB_PAGE_BROWSER_REQUEST_MODEL_CLASS, cl) ?: return null
        if (!baseClass.isAssignableFrom(targetModelClass)) {
            Diagnostics.log("[PbAdRequestBlockHook] skipped pagebrowser AdBid: target is not BaseRequestModel")
            return null
        }

        val requestDataMethod = findPbAdRequestInstanceMethodInHierarchy(
            targetModelClass,
            requestDataMethodName,
            Any::class.java,
            continuationClass,
        ) ?: run {
            Diagnostics.log(
                "[PbAdRequestBlockHook] method NOT FOUND: " +
                    "$modelClassName.$requestDataMethodName(Continuation)",
            )
            return null
        }
        if (requestDataMethod.declaringClass != baseClass || Modifier.isAbstract(requestDataMethod.modifiers)) return null
        return targetModelClass to requestDataMethod
    }

    private fun resolvePbPageRequestFieldPatches(requestClass: Class<*>): List<PbAdRequestFieldPatchSymbols> {
        val patches = ArrayList<PbAdRequestFieldPatchSymbols>(5)
        fun add(name: String, value: Any?) {
            val field = try {
                MemberAccess.findField(requestClass, name)
            } catch (_: Throwable) {
                null
            } ?: return
            field.isAccessible = true
            patches.add(PbAdRequestFieldPatchSymbols(field, value))
        }
        add("adxBearBannerStr", "")
        add("adxBearCommentStr", "")
        add("adExternalBannerStr", "")
        add("adExternalCommentStr", "")
        add("isReqAd", 0)
        return patches
    }

    private fun findPbAdRequestInstanceMethod(
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

    private fun findPbAdRequestInstanceMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        returnType: Class<*>,
        vararg paramTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            findPbAdRequestInstanceMethod(current, name, returnType, *paramTypes)?.let { return it }
            current = current.superclass
        }
        return null
    }
}

private const val PB_PAGE_REQUEST_MESSAGE_CLASS = "com.baidu.tieba.pb.PbPageRequestMessage"
private const val PAGE_BROWSER_REQUEST_MESSAGE_CLASS =
    "com.baidu.tieba.pb.pagebrowser.net.PageBrowserRequestMessage"
private const val PB_LIST_DATA_REQ_BUILDER_CLASS = "tbclient.PbList.DataReq\$Builder"
private const val PB_COMMON_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pb.main.newmodel.CommonRequestModel"
private const val PB_PAGE_BROWSER_REQUEST_MODEL_CLASS = "com.baidu.tieba.pb.pagebrowser.model.BaseRequestModel"
private const val KOTLIN_CONTINUATION_CLASS = "kotlin.coroutines.Continuation"
