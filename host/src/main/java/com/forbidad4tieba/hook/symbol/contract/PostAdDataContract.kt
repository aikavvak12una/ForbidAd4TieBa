package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.contract.CapabilityPolicy.statusFromMissing
import com.forbidad4tieba.hook.symbol.contract.SymbolPointCollector.Companion.has
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbolsBuilder
import com.forbidad4tieba.hook.symbol.model.PostAdDataFilterSymbols
import com.forbidad4tieba.hook.symbol.model.TypeAdapterDataFilterScanSymbols
import com.forbidad4tieba.hook.symbol.scan.PostAdDataFilterSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import com.forbidad4tieba.hook.symbol.status.HookPointStatus
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Owns the cached descriptors and host rules for this capability. */
object PostAdDataContract : SymbolContract("PostAdData") {
    val typeAdapterSetDataMethod = text("typeAdapterSetDataMethod")
    val recyclerViewTypeAdapterSetDataMethod = text("recyclerViewTypeAdapterSetDataMethod")
    val typeAdapterDataItemClass = text("typeAdapterDataItemClass")
    val typeAdapterDataGetTypeMethod = text("typeAdapterDataGetTypeMethod")

    internal override fun scan(scan: SymbolScanContext, output: HookSymbolsBuilder) = with(scan) {

        var recyclerViewTypeAdapterSetDataMethod: String? = null

        val typeAdapterDataFilterScan = runScanStep(
            "PostAdHook.DataFilter",
            logger,
            scanErrors,
            TypeAdapterDataFilterScanSymbols(),
        ) {
            PostAdDataFilterSymbolScanner.scan(cl, logger)
        }

        val typeAdapterSetDataMethod: String? = typeAdapterDataFilterScan.typeAdapterSetDataMethod

        recyclerViewTypeAdapterSetDataMethod =
            typeAdapterDataFilterScan.recyclerViewTypeAdapterSetDataMethod

        val typeAdapterDataItemClass: String? = typeAdapterDataFilterScan.dataItemClass

        val typeAdapterDataGetTypeMethod: String? = typeAdapterDataFilterScan.dataGetTypeMethod

        output[PostAdDataContract.typeAdapterSetDataMethod] = typeAdapterSetDataMethod
        output[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod] = recyclerViewTypeAdapterSetDataMethod
        output[PostAdDataContract.typeAdapterDataItemClass] = typeAdapterDataItemClass
        output[PostAdDataContract.typeAdapterDataGetTypeMethod] = typeAdapterDataGetTypeMethod
    }

    fun resolvePostAdDataFilterSymbols(
        cl: ClassLoader,
        symbols: HookSymbols?,
    ): PostAdDataFilterSymbols? {
        return try {
            val resolvedSymbols = symbols ?: run {
                Diagnostics.log("[PostAdHook] skipped: scan symbols unavailable")
                return null
            }
            val itemClassName = resolvedSymbols[PostAdDataContract.typeAdapterDataItemClass]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PostAdHook] skipped: missing typeAdapterDataItemClass")
                return null
            }
            val getTypeMethodName = resolvedSymbols[PostAdDataContract.typeAdapterDataGetTypeMethod]?.takeIf { it.isNotBlank() } ?: run {
                Diagnostics.log("[PostAdHook] skipped: missing typeAdapterDataGetTypeMethod")
                return null
            }

            val setDataMethods = listOfNotNull(
                resolvePostAdSetDataMethod(
                    cl = cl,
                    adapterClassName = StableTiebaHookPoints.TYPE_ADAPTER_CLASS,
                    methodName = resolvedSymbols[PostAdDataContract.typeAdapterSetDataMethod],
                ),
                resolvePostAdSetDataMethod(
                    cl = cl,
                    adapterClassName = StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS,
                    methodName = resolvedSymbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod],
                ),
            ).distinct()
            if (setDataMethods.isEmpty()) {
                Diagnostics.log("[PostAdHook] skipped: no adapter setData methods resolved")
                return null
            }

            val itemClass = ScanReflection.safeFindClass(itemClassName, cl) ?: run {
                Diagnostics.log("[PostAdHook] class NOT FOUND: $itemClassName")
                return null
            }
            val getTypeMethod = try {
                itemClass.getMethod(getTypeMethodName).takeIf { method ->
                    method.parameterTypes.isEmpty()
                }
            } catch (_: NoSuchMethodException) {
                null
            } ?: run {
                Diagnostics.log("[PostAdHook] method NOT FOUND: $itemClassName.$getTypeMethodName()")
                return null
            }

            val blockedTypes = resolvePostAdBlockedTypes(cl)
            val blockedItemClasses = resolvePostAdBlockedItemClasses(cl)
            if (blockedTypes.isEmpty() && blockedItemClasses.isEmpty()) {
                Diagnostics.log("[PostAdHook] skipped: no blocked post-ad types resolved")
                return null
            }

            setDataMethods.forEach { it.isAccessible = true }
            getTypeMethod.isAccessible = true
            PostAdDataFilterSymbols(
                setDataMethods = setDataMethods,
                itemClass = itemClass,
                getTypeMethod = getTypeMethod,
                blockedTypes = blockedTypes,
                blockedItemClasses = blockedItemClasses,
            )
        } catch (t: Throwable) {
            Diagnostics.log("[PostAdHook] symbol resolve FAILED: ${t.message}")
            Diagnostics.log(t)
            null
        }
    }

    private fun resolvePostAdSetDataMethod(
        cl: ClassLoader,
        adapterClassName: String,
        methodName: String?,
    ): Method? {
        val resolvedMethodName = methodName?.takeIf { it.isNotBlank() } ?: return null
        val adapterClass = ScanReflection.safeFindClass(adapterClassName, cl) ?: run {
            Diagnostics.log("[PostAdHook] class NOT FOUND: $adapterClassName")
            return null
        }
        return try {
            adapterClass.getDeclaredMethod(resolvedMethodName, List::class.java).takeIf { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
            }?.apply { isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        } ?: run {
            Diagnostics.log("[PostAdHook] method NOT FOUND: $adapterClassName.$resolvedMethodName(List)")
            null
        }
    }

    private fun resolvePostAdBlockedItemClasses(cl: ClassLoader): Array<Class<*>> {
        return arrayOf(
            ScanReflection.safeFindClass(StableTiebaHookPoints.PB_FIRST_FLOOR_RECOMMEND_DATA_CLASS, cl),
        ).filterNotNull().toTypedArray()
    }

    private fun resolvePostAdBlockedTypes(cl: ClassLoader): Set<Any> {
        val out = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val advertAppInfoClass = ScanReflection.safeFindClass(POST_AD_ADVERT_APP_INFO_CLASS, cl) ?: return out
        for (fieldName in POST_AD_ADVERT_APP_TYPE_FIELDS) {
            val value = try {
                MemberAccess.findField(advertAppInfoClass, fieldName).get(null)
            } catch (_: Throwable) {
                null
            } ?: continue
            out.add(value)
        }
        return out
    }

    private const val POST_AD_ADVERT_APP_INFO_CLASS = "com.baidu.tbadk.core.data.AdvertAppInfo"

    private val POST_AD_ADVERT_APP_TYPE_FIELDS = arrayOf(
        "TYPE_FRS_ADVERT_APP_EMPTY",
        "TYPE_FRS_ADVERT_APP_SINGLE_PIC",
        "TYPE_FRS_ADVERT_APP_MULTI_PIC",
        "TYPE_FRS_ADVERT_APP_VIDEO",
        "TYPE_FRS_ADVERT_APP_VR_VIDEO",
        "TYPE_PB_ADVERT_APP_EMPTY",
        "TYPE_RECOMMEND_ADVERT_APP_EMPTY",
        "TYPE_RECOMMEND_ADVERT_APP_SINGLE_PIC",
        "TYPE_RECOMMEND_ADVERT_APP_VIDEO",
        "TYPE_ADVERT_LEGO_APP",
        "TYPE_ADVERT_LEGO_APP_SINGLE",
        "TYPE_ADVERT_LEGO_APP_MULTI",
        "TYPE_ADVERT_LEGO_APP_VIDEO",
        "TYPE_ADVERT_LEGO_APP_SMALL_PIC",
        "TYPE_ADVERT_LEGO_APP_SMALL_VIDEO_PIC",
        "TYPE_ADVERT_FUN_AD_TEMPLETE",
        "TYPE_ADVERT_FUN_AD_EMPTY",
        "TYPE_ADVERT_FUN_AD_PLACEHOLDER",
        "TYPE_ADVERT_FUN_AD_COMMENT_PLACEHOLDER",
    )

    fun pathStatus(symbols: HookSymbols): HookFeatureStatus {
        val postDataCritical = ArrayList<String>(4)
        val postDataOptional = ArrayList<String>(2)
        val hasTypeAdapterSetDataMethod = !symbols[PostAdDataContract.typeAdapterSetDataMethod].isNullOrBlank()
        val hasRecyclerViewTypeAdapterSetDataMethod =
            !symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod].isNullOrBlank()
        if (!hasTypeAdapterSetDataMethod && !hasRecyclerViewTypeAdapterSetDataMethod) {
            postDataCritical.add("typeAdapterSetDataMethod")
            postDataCritical.add("recyclerViewTypeAdapterSetDataMethod")
        } else {
            if (!hasTypeAdapterSetDataMethod) {
                postDataOptional.add("typeAdapterSetDataMethod")
            }
            if (!hasRecyclerViewTypeAdapterSetDataMethod) {
                postDataOptional.add("recyclerViewTypeAdapterSetDataMethod")
            }
        }
        if (symbols[PostAdDataContract.typeAdapterDataItemClass].isNullOrBlank()) postDataCritical.add("typeAdapterDataItemClass")
        if (symbols[PostAdDataContract.typeAdapterDataGetTypeMethod].isNullOrBlank()) {
            postDataCritical.add("typeAdapterDataGetTypeMethod")
        }
        val postDataStatus = statusFromMissing(postDataCritical, postDataOptional)
        return postDataStatus
    }

    internal override fun points(symbols: HookSymbols): List<HookPointStatus> = SymbolPointCollector().apply {
        add(
            "PostAdHook.DataFilter",
            "type=${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols[PostAdDataContract.typeAdapterSetDataMethod]}(List) " +
                "recycler=${StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS}." +
                "${symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod]}(List) " +
                "item=${symbols[PostAdDataContract.typeAdapterDataItemClass]}.${symbols[PostAdDataContract.typeAdapterDataGetTypeMethod]}()",
            listOf(
                "adapterSetDataMethod" to (
                    has(symbols[PostAdDataContract.typeAdapterSetDataMethod]) ||
                        has(symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod])
                    ),
                PostAdDataContract.typeAdapterDataItemClass.check(symbols),
                PostAdDataContract.typeAdapterDataGetTypeMethod.check(symbols),
            ),
        )
        add(
            "PostAdHook.DataFilter.TypeAdapter",
            "${StableTiebaHookPoints.TYPE_ADAPTER_CLASS}.${symbols[PostAdDataContract.typeAdapterSetDataMethod]}(List)",
            listOf(
                PostAdDataContract.typeAdapterSetDataMethod.check(symbols),
                PostAdDataContract.typeAdapterDataItemClass.check(symbols),
                PostAdDataContract.typeAdapterDataGetTypeMethod.check(symbols),
            ),
        )
        add(
            "PostAdHook.DataFilter.RecyclerViewTypeAdapter",
            "${StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS}." +
                "${symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod]}(List)",
            listOf(
                "recyclerViewTypeAdapterSetDataMethod" to
                    has(symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod]),
                PostAdDataContract.typeAdapterDataItemClass.check(symbols),
                PostAdDataContract.typeAdapterDataGetTypeMethod.check(symbols),
            ),
        )
    }.build()

    internal override val pointOwners = listOf(
        PointOwner("PostAdHook.DataFilter", true, listOf(HookFeatureKey.BLOCK_AD_POST_PAGE)),
    )

    internal override fun isCacheValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val hasTypeAdapterDataFilterSymbols =
            symbols[PostAdDataContract.typeAdapterSetDataMethod] != null ||
                symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod] != null ||
                symbols[PostAdDataContract.typeAdapterDataItemClass] != null ||
                symbols[PostAdDataContract.typeAdapterDataGetTypeMethod] != null
        if (hasTypeAdapterDataFilterSymbols && !isTypeAdapterDataFilterValid(symbols, cl)) return false
        return true
    }

    private const val BD_UNIQUE_ID_CLASS = "com.baidu.adp.BdUniqueId"

    private fun isTypeAdapterDataFilterValid(symbols: HookSymbols, cl: ClassLoader): Boolean {
        val typeAdapterSetDataMethodName = symbols[PostAdDataContract.typeAdapterSetDataMethod]
        val recyclerViewTypeAdapterSetDataMethodName = symbols[PostAdDataContract.recyclerViewTypeAdapterSetDataMethod]
        if (typeAdapterSetDataMethodName == null && recyclerViewTypeAdapterSetDataMethodName == null) {
            return false
        }
        val dataItemClassName = symbols[PostAdDataContract.typeAdapterDataItemClass] ?: return false
        val dataGetTypeMethodName = symbols[PostAdDataContract.typeAdapterDataGetTypeMethod] ?: return false
        return try {
            val dataItemClass = ScanReflection.safeFindClass(dataItemClassName, cl) ?: return false
            val bdUniqueIdClass = ScanReflection.safeFindClass(BD_UNIQUE_ID_CLASS, cl) ?: return false

            fun hasSetDataMethod(className: String, methodName: String): Boolean {
                val adapterClass = ScanReflection.safeFindClass(className, cl) ?: return false
                return adapterClass.declaredMethods.any { method ->
                    method.name == methodName &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        ScanReflection.isListType(method.parameterTypes[0])
                }
            }
            if (
                typeAdapterSetDataMethodName != null &&
                !hasSetDataMethod(
                    StableTiebaHookPoints.TYPE_ADAPTER_CLASS,
                    typeAdapterSetDataMethodName,
                )
            ) {
                return false
            }
            if (
                recyclerViewTypeAdapterSetDataMethodName != null &&
                !hasSetDataMethod(
                    StableTiebaHookPoints.RECYCLER_VIEW_TYPE_ADAPTER_CLASS,
                    recyclerViewTypeAdapterSetDataMethodName,
                )
            ) {
                return false
            }

            dataItemClass.methods.any { method ->
                method.name == dataGetTypeMethodName &&
                    method.parameterTypes.isEmpty() &&
                    bdUniqueIdClass.isAssignableFrom(method.returnType)
            }
        } catch (_: Throwable) {
            false
        }
    }

    // This owner supplies targets to a composing capability.
    internal override fun capabilities(symbols: HookSymbols): Map<String, HookFeatureStatus> = emptyMap()
}
