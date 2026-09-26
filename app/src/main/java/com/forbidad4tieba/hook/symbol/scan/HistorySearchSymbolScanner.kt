package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.widget.BaseAdapter
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.HistorySearchScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** The history list follows cache delivery; its adapter setter must also notify observers. */
internal object HistorySearchSymbolScanner {
    private const val TAG = "HistorySearchHook"
    private const val ACTIVITY = StableTiebaHookPoints.PB_HISTORY_ACTIVITY_CLASS
    private const val DATA = "com.baidu.tieba.myCollection.baseHistory.PbHistoryData"
    private const val CACHE = "com.baidu.tbadk.mvc.model.CacheModel"
    private const val CALLBACK = "$CACHE\$CacheModelCallback"
    private const val NAVIGATION = "com.baidu.tbadk.core.view.NavigationBar"
    private const val LIST_VIEW = "com.baidu.adp.widget.ListView.BdListView"
    private const val LIST = "java.util.List"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): HistorySearchScanSymbols =
        scanSubStep(TAG, logger, HistorySearchScanSymbols()) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) { scan(it.bridge, cl, logger) }
                ?: HistorySearchScanSymbols().also { issue("Bridge", "DexKit bridge unavailable", logger) }
        }

    private data class CacheDelivery(val list: FieldData, val update: MethodData)
    private data class Adapter(val field: FieldData, val setter: MethodData)

    private fun scan(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): HistorySearchScanSymbols {
        val create = role("Create", logger) {
            named(bridge, ACTIVITY, "onCreate", "void", logger, "android.os.Bundle")
        }
        val delivery = create?.let { role("CacheDelivery", logger) { cacheDelivery(bridge, it, cl, logger) } }
        val adapter = delivery?.let { role("Adapter", logger) { adapter(bridge, create!!, it.update, cl, logger) } }
        val navigation = create?.let { role("Navigation", logger) { navigation(it, logger) } }
        fun getter(name: String) = role(name, logger) { named(bridge, DATA, name, "java.lang.String", logger)?.methodName }
        return HistorySearchScanSymbols(
            adapterField = adapter?.field?.fieldName,
            adapterSetListMethod = adapter?.setter?.methodName,
            adapterSetListMethodSpec = adapter?.setter?.spec(),
            listField = delivery?.list?.fieldName,
            activityListUpdateMethod = delivery?.update?.methodName,
            activityListUpdateMethodSpec = delivery?.update?.spec(),
            activityNavBarField = navigation?.fieldName,
            threadNameMethod = getter("getThreadName"),
            forumNameMethod = getter("getForumName"),
            userNameMethod = getter("getUserName"),
            descriptionMethod = getter("getDescription"),
            threadIdMethod = getter("getThreadId"),
            postIdMethod = getter("getPostID"),
            liveIdMethod = getter("getLiveId"),
        ).also { HookSymbolScanDiagnostics.log(logger, "$TAG $it") }
    }

    private fun cacheDelivery(bridge: DexKitBridge, create: MethodData, cl: ClassLoader, logger: ScanLogger?): CacheDelivery? {
        if (!create.calls(CACHE, "setCallback", "void", CALLBACK)) {
            issue("CacheDelivery", "cache callback registration missing", logger)
            return null
        }
        val callbackFields = create.readFields(ACTIVITY, CALLBACK)
        val constructors = bridge.getClassData(ACTIVITY)?.methods.orEmpty().filter { it.isConstructor }
        val callbackType = ScanReflection.safeFindClass(CALLBACK, cl) ?: run {
            issue("CacheCallback", "cache callback interface missing", logger)
            return null
        }
        val candidates = constructors.filter { constructor ->
            callbackFields.any { field -> constructor.usingFields.any { it.usingType.isWrite() && it.field.descriptor == field.descriptor } }
        }.flatMap { it.invokes }.filter { it.isConstructor && it.paramTypeNames == listOf(ACTIVITY) }
            .mapNotNull { constructor ->
                val type = ScanReflection.safeFindClass(constructor.declaredClassName, cl) ?: return@mapNotNull null
                if (!callbackType.isAssignableFrom(type)) return@mapNotNull null
                bridge.getClassData(type.name)?.methods.orEmpty().singleOrNull {
                    it.methodName == "onCacheDataGet" && instance(it, "void",
                        "com.baidu.tbadk.mvc.message.ReadCacheRespMsg", "com.baidu.tbadk.mvc.message.ReadCacheMessage") &&
                        it.calls("com.baidu.adp.framework.message.CustomResponsedMessage", "getData", "java.lang.Object")
                }
            }.distinctBy { it.descriptor }
        val callback = unique("CacheCallback", candidates, logger) { it.descriptor } ?: return null
        val writes = callback.invokes.mapNotNull { accessorField(it, write = true) }.distinctBy { it.descriptor }
        val reads = callback.invokes.mapNotNull { accessorField(it, write = false) }.map { it.descriptor }.toSet()
        val list = unique("HistoryList", writes.filter { it.descriptor in reads && it.typeName == LIST }, logger) { it.descriptor }
        val wrappers = callback.invokes.filter {
            Modifier.isStatic(it.modifiers) && it.declaredClassName == ACTIVITY &&
                it.paramTypeNames == listOf(ACTIVITY, LIST) && it.returnTypeName == "void" &&
                it.opNames.map { op -> op.substringBefore('/') } == listOf("invoke-virtual", "return-void") &&
                it.usingFields.isEmpty()
        }.filter { wrapper ->
            wrapper.invokes.singleOrNull()?.let { it.declaredClassName == ACTIVITY && instance(it, "void", LIST) } == true
        }.distinctBy { it.descriptor }
        val updates = wrappers.map { it.invokes.single() }.distinctBy { it.descriptor }
        val update = unique("ListUpdate", updates, logger) { it.descriptor }
        if (list == null || update == null) return null
        if (!directHistoryUpdates(callback, list, wrappers)) {
            issue("ListUpdate", "history getter-result-forwarder sequence missing", logger)
            return null
        }
        return CacheDelivery(list, update)
    }

    /** A direct getter/result/forwarder window; register data flow is not inferred. */
    private fun directHistoryUpdates(callback: MethodData, list: FieldData, wrappers: List<MethodData>): Boolean {
        val ops = callback.opNames.map { it.substringBefore('/') }
        val calls = callback.invokes
        val positions = ops.indices.filter { ops[it].startsWith("invoke-") }
        if (positions.size != calls.size) return false
        val targets = wrappers.map { it.descriptor }.toSet()
        return calls.indices.filter { calls[it].descriptor in targets }.all { index ->
            if (index == 0) return@all false
            val start = positions[index - 1]
            val end = positions[index]
            accessorField(calls[index - 1], write = false)?.descriptor == list.descriptor &&
                end == start + 2 && ops[start] == "invoke-static" &&
                ops[start + 1] == "move-result-object" && ops[end] == "invoke-static"
        }
    }

    /** Static field accessors may be renamed; only a direct field read/write is accepted. */
    private fun accessorField(method: MethodData, write: Boolean): FieldData? {
        if (!Modifier.isStatic(method.modifiers) || method.declaredClassName != ACTIVITY ||
            method.returnTypeName != LIST || method.paramTypeNames != (if (write) listOf(ACTIVITY, LIST) else listOf(ACTIVITY))) return null
        val expected = listOf(if (write) "iput-object" else "iget-object", "return-object")
        if (method.opNames.map { it.substringBefore('/') } != expected || method.invokes.isNotEmpty()) return null
        val usage = method.usingFields.singleOrNull() ?: return null
        return usage.field.takeIf { !Modifier.isStatic(it.modifiers) && it.declaredClassName == ACTIVITY && it.typeName == LIST &&
            (if (write) usage.usingType.isWrite() else usage.usingType.isRead()) }
    }

    private fun adapter(bridge: DexKitBridge, create: MethodData, update: MethodData, cl: ClassLoader, logger: ScanLogger?): Adapter? {
        if (!create.calls(LIST_VIEW, "setAdapter", "void", "android.widget.ListAdapter")) {
            issue("Adapter", "list adapter binding missing", logger)
            return null
        }
        val candidates = update.readFields(ACTIVITY).mapNotNull { field ->
            val type = ScanReflection.safeFindClass(field.typeName, cl) ?: return@mapNotNull null
            if (!BaseAdapter::class.java.isAssignableFrom(type) || !create.reads(field) ||
                create.usingFields.none { it.usingType.isWrite() && it.field.descriptor == field.descriptor }) return@mapNotNull null
            val setter = update.businessCalls().getOrNull(0) ?: return@mapNotNull null
            if (!instance(setter, "void", LIST)) return@mapNotNull null
            val effectiveSetter = type.getMethod(setter.methodName, List::class.java)
            if (effectiveSetter.declaringClass.name != setter.declaredClassName) return@mapNotNull null
            val ops = withoutTitanPrefix(update) ?: return@mapNotNull null
            val firstInvoke = ops.indexOfFirst { it.startsWith("invoke-") }
            val prefix = ops.take(firstInvoke + 1)
            val direct = listOf("iget-object", "if-eqz", "invoke-virtual")
            val reload = listOf("iget-object", "if-eqz", "iget-object", "invoke-virtual")
            val count = if (prefix == reload) 2 else 1
            if (prefix != direct && prefix != reload || update.businessFields().take(count).let { fields ->
                    fields.size != count || fields.any { !it.usingType.isRead() || it.field.descriptor != field.descriptor }
                }) return@mapNotNull null
            val list = adapterList(setter) ?: return@mapNotNull null
            val consumers = listOf("getCount" to emptyArray<Class<*>>(), "getItem" to arrayOf<Class<*>>(Int::class.javaPrimitiveType!!))
            if (consumers.any { (name, params) ->
                    val method = type.getMethod(name, *params)
                    bridge.getClassData(method.declaringClass.name)?.methods.orEmpty().none {
                        it.methodName == name && it.paramTypeNames == params.map { param -> param.name } && it.reads(list)
                    }
                }) return@mapNotNull null
            Adapter(field, setter)
        }
        return unique("Adapter", candidates, logger) { "${it.field.descriptor}->${it.setter.descriptor}" }
    }

    private fun adapterList(setter: MethodData): FieldData? {
        if (mainOps(setter) != listOf("invoke-virtual", "invoke-virtual", "return-void") || setter.businessFields().isNotEmpty()) return null
        val calls = setter.businessCalls()
        if (calls.size != 2 || calls[0].declaredClassName != setter.declaredClassName || !instance(calls[0], "void", LIST) ||
            calls[1].declaredClassName != "android.widget.BaseAdapter" || calls[1].methodName != "notifyDataSetChanged" ||
            !instance(calls[1], "void")) return null
        val replace = calls[0]
        val ops = mainOps(replace) ?: return null
        val allowed = setOf("if-eqz", "if-nez", "iget-object", "iput-object", "new-instance", "invoke-direct", "invoke-interface", "invoke-virtual", "return-void")
        if (ops.any { it !in allowed }) return null
        val list = replace.businessFields().map { it.field }.filter { it.typeName == LIST }.distinctBy { it.descriptor }.singleOrNull() ?: return null
        if (list.declaredClassName != replace.declaredClassName || Modifier.isStatic(list.modifiers)) return null
        val expectedCalls = listOf(
            "Ljava/util/ArrayList;-><init>()V", "Ljava/util/List;->clear()V",
            "Ljava/util/List;->addAll(Ljava/util/Collection;)Z", "Landroid/util/SparseArray;->clear()V",
        )
        if (replace.businessCalls().map { it.descriptor } != expectedCalls) return null
        if (replace.businessFields().any { Modifier.isStatic(it.field.modifiers) ||
                it.field.descriptor != list.descriptor && (it.field.typeName != "android.util.SparseArray" || !it.usingType.isRead()) }) return null
        return list
    }

    private fun navigation(create: MethodData, logger: ScanLogger?): FieldData? {
        val valid = create.calls(NAVIGATION, "addSystemImageButton", "android.view.View", "$NAVIGATION\$ControlAlign", "$NAVIGATION\$ControlType") &&
            create.calls(NAVIGATION, "setCenterTextTitle", "android.widget.TextView", "java.lang.String") &&
            create.calls(NAVIGATION, "addTextButton", "android.widget.TextView", "$NAVIGATION\$ControlAlign", "java.lang.String")
        val candidates = if (valid) create.readFields(ACTIVITY, NAVIGATION).filter { field ->
            create.usingFields.any { it.usingType.isWrite() && it.field.descriptor == field.descriptor }
        } else emptyList()
        return unique("Navigation", candidates, logger) { it.descriptor }
    }

    /** Restricted direct bodies plus the verified Titan void dispatch suffix, not arbitrary data flow. */
    private fun mainOps(method: MethodData): List<String>? {
        val ops = withoutTitanPrefix(method) ?: return null
        val end = ops.indexOf("return-void")
        if (end < 0) return null
        if (method.opNames.take(2) != listOf("sget-object", "if-nez")) return ops.takeIf { end == ops.lastIndex }
        if (ops.drop(end + 1) != listOf("move-object", "const", "invoke-interface", "move-result-object", "if-eqz", "return-void")) return null
        val dispatch = method.invokes.filter { it.declaredClassName == INTERCEPTABLE }.singleOrNull() ?: return null
        if (dispatch.methodName != "invokeL" || dispatch.returnTypeName != INTERCEPT_RESULT ||
            dispatch.paramTypeNames != listOf("int", "java.lang.Object", "java.lang.Object")) return null
        return ops.take(end + 1)
    }

    private fun withoutTitanPrefix(method: MethodData): List<String>? {
        val ops = method.opNames.map { it.substringBefore('/') }
        if (ops.take(2) != listOf("sget-object", "if-nez")) return ops
        val guard = method.usingFields.getOrNull(0) ?: return null
        if (!guard.usingType.isRead() || guard.field.typeName != INTERCEPTABLE ||
            guard.field.declaredClassName != method.declaredClassName || !Modifier.isStatic(guard.field.modifiers)) return null
        return ops.drop(2)
    }

    private fun MethodData.businessFields() = usingFields.filterNot {
        Modifier.isStatic(it.field.modifiers) && it.field.typeName == INTERCEPTABLE || it.field.declaredClassName == INTERCEPT_RESULT
    }
    private fun MethodData.businessCalls() = invokes.filterNot { it.declaredClassName == INTERCEPTABLE }
    private fun MethodData.readFields(owner: String, type: String? = null): List<FieldData> = usingFields.filter {
        it.usingType.isRead() && it.field.declaredClassName == owner && !Modifier.isStatic(it.field.modifiers) && (type == null || it.field.typeName == type)
    }.map { it.field }.distinctBy { it.descriptor }
    private fun MethodData.reads(field: FieldData) = usingFields.any { it.usingType.isRead() && it.field.descriptor == field.descriptor }
    private fun MethodData.calls(owner: String, name: String, returns: String, vararg params: String) = invokes.any {
        it.declaredClassName == owner && it.methodName == name && it.returnTypeName == returns && it.paramTypeNames == params.toList()
    }
    private fun instance(method: MethodData, returns: String, vararg params: String) =
        !Modifier.isStatic(method.modifiers) && !method.isConstructor && method.returnTypeName == returns && method.paramTypeNames == params.toList()
    private fun MethodData.spec() = "$methodName|$returnTypeName|${paramTypeNames.joinToString(",")}"
    private fun named(bridge: DexKitBridge, owner: String, name: String, returns: String, logger: ScanLogger?, vararg params: String) =
        unique(name, bridge.getClassData(owner)?.methods.orEmpty().filter { it.methodName == name && instance(it, returns, *params) }, logger) { it.descriptor }
    private inline fun <T> role(name: String, logger: ScanLogger?, block: () -> T?): T? = scanSubStep("$TAG.$name", logger, null, block)
    private fun <T> unique(name: String, candidates: List<T>, logger: ScanLogger?, describe: (T) -> String): T? {
        if (candidates.size == 1) return candidates.single()
        issue(name, "candidates=" + candidates.joinToString(",", transform = describe).ifEmpty { "-" }, logger)
        return null
    }
    private fun issue(role: String, detail: String, logger: ScanLogger?) {
        HookSymbolScanSession.get()?.scanErrors?.let {
            HookSymbolScanDiagnostics.recordScanIssue(logger, "$TAG.$role", it, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$TAG.$role $detail")
    }
}
