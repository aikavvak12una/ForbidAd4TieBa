package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.widget.BaseAdapter
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.CollectionSearchScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** Collection roles follow the SDK callbacks, collection protocol and adapter state consumers. */
internal object CollectionSearchSymbolScanner {
    private const val TAG = "CollectionSearchHook"
    private const val ACTIVITY = StableTiebaHookPoints.COLLECT_TAB_ACTIVITY_CLASS
    private const val FRAGMENT = StableTiebaHookPoints.COLLECTION_THREAD_FRAGMENT_CLASS
    private const val NAVIGATION = "com.baidu.tbadk.core.view.NavigationBar"
    private const val MARK = "com.baidu.tbadk.baseEditMark.MarkData"
    private const val LIST_VIEW = "com.baidu.adp.widget.ListView.BdListView"
    private const val JSON_OBJECT = "org.json.JSONObject"
    private const val JSON_ARRAY = "org.json.JSONArray"
    private const val VIEW = "android.view.View"
    private const val BUNDLE = "android.os.Bundle"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): CollectionSearchScanSymbols =
        scanSubStep(TAG, logger, CollectionSearchScanSymbols()) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) { scan(it.bridge, cl, logger) }
                ?: CollectionSearchScanSymbols().also { issue("Bridge", "DexKit bridge unavailable", logger) }
        }

    private fun scan(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): CollectionSearchScanSymbols {
        val click = role("ItemClick", logger) {
            sdkMethod(bridge, FRAGMENT, "onItemClick", "void", logger,
                "android.widget.AdapterView", VIEW, "int", "long")
        }
        val resume = role("Resume", logger) { sdkMethod(bridge, FRAGMENT, "onResume", "void", logger) }
        val model = click?.let { role("Model", logger) { model(it, cl, logger) } }
        val parser = model?.let { role("Parser", logger) { parser(bridge, it, cl, logger) } }
        val presenter = resume?.let { role("Presenter", logger) { presenter(bridge, it, cl, logger) } }
        val displayList = presenter?.let { role("DisplayList", logger) { displayList(resume!!, it, logger) } }
        val navigation = role("Navigation", logger) { navigation(bridge, logger) }
        val editMode = click?.let { role("EditMode", logger) { editMode(bridge, it, logger) } }
        val footer = if (presenter != null && model != null) {
            role("Footer", logger) { footer(bridge, presenter, model, logger) } ?: Footer()
        } else Footer()
        return CollectionSearchScanSymbols(
            presenterField = presenter?.field?.fieldName,
            presenterListSetterMethod = presenter?.setter?.methodName,
            presenterListSetterMethodSpec = presenter?.setter?.spec(),
            presenterAdapterField = presenter?.adapter?.fieldName,
            modelField = model?.field?.fieldName,
            modelListGetterMethod = model?.getter?.methodName,
            modelListGetterMethodSpec = model?.getter?.spec(),
            modelParseMethod = parser?.methodName,
            modelParseMethodSpec = parser?.spec(),
            modelListField = model?.list?.fieldName,
            fragmentDisplayListField = displayList?.fieldName,
            activityNavControllerField = navigation?.controller?.fieldName,
            navBarField = navigation?.bar?.fieldName,
            adapterShowFooterMethod = footer.show?.method?.methodName,
            adapterLoadingMethod = footer.loading?.method?.methodName,
            adapterHasMoreMethod = footer.more?.method?.methodName,
            editModeMethod = editMode?.methodName,
        ).also { HookSymbolScanDiagnostics.log(logger, "$TAG $it") }
    }

    private data class Model(val field: FieldData, val getter: MethodData, val list: FieldData)
    private data class Presenter(
        val field: FieldData, val setter: MethodData, val adapter: FieldData,
        val adapterSetter: MethodData, val count: MethodData,
    )
    private data class Navigation(val controller: FieldData, val bar: FieldData)
    private data class Forwarder(val field: FieldData, val target: MethodData)
    private data class BooleanWriter(val method: MethodData, val field: FieldData)
    private data class Footer(
        val show: BooleanWriter? = null, val loading: BooleanWriter? = null, val more: BooleanWriter? = null,
    )

    private fun model(click: MethodData, cl: ClassLoader, logger: ScanLogger?): Model? {
        val candidates = click.readFields(FRAGMENT).flatMap { field ->
            click.invokes.filter {
                it.declaredClassName == field.typeName && instance(it) && it.paramCount == 0 &&
                    isList(it.returnTypeName, cl)
            }.distinctBy { it.descriptor }.mapNotNull { getter ->
                val list = leafField(getter, write = false, boolean = false) ?: return@mapNotNull null
                if (!isList(list.typeName, cl) || list.typeName != getter.returnTypeName) return@mapNotNull null
                Model(field, getter, list)
            }
        }
        return unique("Model", candidates, logger) { "${it.field.descriptor}->${it.getter.descriptor}" }
    }

    private fun parser(bridge: DexKitBridge, model: Model, cl: ClassLoader, logger: ScanLogger?): MethodData? =
        unique("Parser", bridge.getClassData(model.field.typeName)?.methods.orEmpty().filter {
            instance(it, model.getter.returnTypeName, "java.lang.String") && isList(it.returnTypeName, cl) &&
                it.usingStrings.containsAll(listOf("error", "errno", "store_thread")) &&
                it.calls(JSON_OBJECT, "<init>", "void", "java.lang.String") &&
                it.calls(JSON_OBJECT, "optJSONArray", JSON_ARRAY, "java.lang.String") &&
                it.calls(JSON_ARRAY, "getJSONObject", JSON_OBJECT, "int") &&
                it.calls(MARK, "paserJson", "void", JSON_OBJECT)
        }, logger) { it.descriptor }

    private fun presenter(bridge: DexKitBridge, resume: MethodData, cl: ClassLoader, logger: ScanLogger?): Presenter? {
        val candidates = resume.readFields(FRAGMENT).flatMap { field ->
            resume.invokes.filter {
                it.declaredClassName == field.typeName && instance(it) && it.returnTypeName == "void" &&
                    it.paramCount == 1 && isList(it.paramTypeNames.single(), cl)
            }.distinctBy { it.descriptor }.mapNotNull { setter ->
                val forwarder = forwarder(setter) ?: return@mapNotNull null
                val adapterClass = ScanReflection.safeFindClass(forwarder.field.typeName, cl) ?: return@mapNotNull null
                if (!BaseAdapter::class.java.isAssignableFrom(adapterClass)) return@mapNotNull null
                val list = adapterList(forwarder.target, cl) ?: return@mapNotNull null
                val counts = bridge.getClassData(forwarder.field.typeName)?.methods.orEmpty().filter {
                    it.methodName == "getCount" && instance(it, "int") && it.reads(list)
                }
                val count = counts.singleOrNull() ?: return@mapNotNull null
                Presenter(field, setter, forwarder.field, forwarder.target, count)
            }
        }
        return unique("Presenter", candidates, logger) { "${it.field.descriptor}->${it.setter.descriptor}" }
    }

    private fun adapterList(setter: MethodData, cl: ClassLoader): FieldData? {
        if (!instance(setter) || setter.returnTypeName != "void" || setter.paramCount != 1 ||
            !isList(setter.paramTypeNames.single(), cl)) return null
        val ops = mainOps(setter) ?: return null
        if (ops.any { it !in setOf("iget-object", "invoke-virtual", "invoke-interface", "if-eqz", "return-void") } ||
            ops.count { it == "iget-object" } != 2 || ops.count { it == "if-eqz" } > 1) return null
        val usages = setter.businessFields()
        if (usages.size != 2 || usages.any { !it.usingType.isRead() || Modifier.isStatic(it.field.modifiers) }) return null
        val list = usages.map { it.field }.distinctBy { it.descriptor }.singleOrNull() ?: return null
        if (list.declaredClassName != setter.declaredClassName || !isList(list.typeName, cl)) return null
        val calls = setter.businessCalls()
        if (calls.size != 3 || !calls[0].listCall("clear", "void") ||
            !calls[1].listCall("addAll", "boolean", "java.util.Collection") ||
            calls[2].methodName != "notifyDataSetChanged" || !instance(calls[2], "void") ||
            calls[2].declaredClassName !in setOf(setter.declaredClassName, "android.widget.BaseAdapter")) return null
        return list
    }

    private fun displayList(resume: MethodData, presenter: Presenter, logger: ScanLogger?): FieldData? {
        // onResume has no List argument. Before the update it only calls super and reads
        // the presenter and display list, so another object source cannot supply the list.
        val calls = resume.invokes.take(2)
        val prefix = withoutTitanPrefix(resume)?.let { ops ->
            val secondInvoke = ops.indices.filter { ops[it].startsWith("invoke-") }.getOrNull(1)
            secondInvoke?.let { ops.take(it + 1) }
        }
        val fields = resume.businessFields()
        val directPrefix = listOf("invoke-super", "iget-object", "if-eqz", "iget-object", "invoke-virtual")
        val reloadPrefix = listOf("invoke-super", "iget-object", "if-eqz", "iget-object", "iget-object", "invoke-virtual")
        // D8 may reload the same receiver after its null check. The extra read must be
        // the very same presenter field; it does not permit another list or helper source.
        val receiverReads = if (prefix == reloadPrefix) 2 else 1
        val list = fields.getOrNull(receiverReads)
        val valid = calls.size == 2 && calls[0].methodName == "onResume" && instance(calls[0], "void") &&
            calls[1].descriptor == presenter.setter.descriptor &&
            (prefix == directPrefix || prefix == reloadPrefix) && fields.size > receiverReads &&
            fields.take(receiverReads).all { it.usingType.isRead() && it.field.descriptor == presenter.field.descriptor } &&
            list?.usingType?.isRead() == true && list.field.declaredClassName == FRAGMENT &&
            !Modifier.isStatic(list.field.modifiers) && list.field.typeName == presenter.setter.paramTypeNames.single()
        if (!valid) {
            issue("DisplayList", "onResume list source is not a direct field update", logger)
            return null
        }
        return list?.field
    }

    private fun navigation(bridge: DexKitBridge, logger: ScanLogger?): Navigation? {
        val create = sdkMethod(bridge, ACTIVITY, "onCreate", "void", logger, BUNDLE) ?: return null
        val candidates = create.usingFields.filter {
            it.usingType.isWrite() && it.field.declaredClassName == ACTIVITY && !Modifier.isStatic(it.field.modifiers)
        }.map { it.field }.distinctBy { it.descriptor }.flatMap { field ->
            create.invokes.filter {
                it.isConstructor && it.declaredClassName == field.typeName && it.paramTypeNames == listOf(ACTIVITY) &&
                    it.calls(NAVIGATION, "addCustomView", VIEW, "$NAVIGATION\$ControlAlign", "int", "$VIEW\$OnClickListener")
            }.distinctBy { it.descriptor }.flatMap { constructor ->
                constructor.readFields(field.typeName, NAVIGATION).filter { bar ->
                    constructor.usingFields.any { it.usingType.isWrite() && it.field.descriptor == bar.descriptor }
                }.map { Navigation(field, it) }
            }
        }.distinctBy { "${it.controller.descriptor}:${it.bar.descriptor}" }
        return unique("Navigation", candidates, logger) { "${it.controller.descriptor}->${it.bar.descriptor}" }
    }

    private fun editMode(bridge: DexKitBridge, click: MethodData, logger: ScanLogger?): MethodData? {
        val candidates = click.invokes.filter { it.declaredClassName == ACTIVITY && instance(it, "boolean") }
            .distinctBy { it.descriptor }.filter { activityGetter ->
                val delegate = forwarder(activityGetter) ?: return@filter false
                val state = leafField(delegate.target, write = false, boolean = true) ?: return@filter false
                bridge.getClassData(delegate.field.typeName)?.methods.orEmpty().any { writer ->
                    instance(writer, "void", "boolean") && "is_edit_state" in writer.usingStrings &&
                        writer.calls(BUNDLE, "putBoolean", "void", "java.lang.String", "boolean") &&
                        writer.calls("com.baidu.adp.framework.MessageManager", "dispatchResponsedMessage", "void",
                            "com.baidu.adp.framework.message.ResponsedMessage") &&
                        writer.readFields(delegate.field.typeName, "boolean").map { it.descriptor } == listOf(state.descriptor) &&
                        writer.usingFields.filter { it.usingType.isWrite() && it.field.typeName == "boolean" }
                            .map { it.field.descriptor }.distinct() == listOf(state.descriptor)
                }
            }
        return unique("EditMode", candidates, logger) { it.descriptor }
    }

    private fun footer(bridge: DexKitBridge, presenter: Presenter, model: Model, logger: ScanLogger?): Footer {
        val owner = bridge.getClassData(presenter.field.typeName)
        val responses = owner?.methods.orEmpty().filter {
            instance(it) && it.calls(model.getter) && it.calls(presenter.adapterSetter) && it.reads(presenter.adapter)
        }
        val candidates = bridge.getClassData(presenter.adapter.typeName)?.methods.orEmpty().mapNotNull { method ->
            if (!instance(method, "void", "boolean") || responses.none { it.calls(method) }) return@mapNotNull null
            leafField(method, write = true, boolean = true)?.let { BooleanWriter(method, it) }
        }
        val show = role("ShowFooter", logger) {
            unique("ShowFooter", candidates.filter { presenter.count.reads(it.field) }, logger) { it.method.descriptor }
        }
        val view = role("AdapterView", logger) {
            sdkMethod(bridge, presenter.adapter.typeName, "getView", VIEW, logger, "int", VIEW, "android.view.ViewGroup")
        }
        val loading = role("Loading", logger) {
            val refresh = owner?.methods.orEmpty().filter {
                it.calls(LIST_VIEW, "startPullRefresh", "void") && it.reads(presenter.adapter)
            }
            unique("Loading", candidates.filter { candidate ->
                view?.reads(candidate.field) == true && refresh.any { it.calls(candidate.method) }
            }, logger) { it.method.descriptor }
        }
        val more = role("HasMore", logger) {
            if (show == null || loading == null || show.field.descriptor == loading.field.descriptor) {
                issue("HasMore", "distinct footer count/loading roles required", logger)
                null
            } else unique("HasMore", candidates.filter {
                it.field.descriptor != show.field.descriptor && it.field.descriptor != loading.field.descriptor &&
                    view?.reads(it.field) == true
            }, logger) { it.method.descriptor }
        }
        return Footer(show, loading, more)
    }

    private fun leafField(method: MethodData, write: Boolean, boolean: Boolean): FieldData? {
        if (!instance(method) || method.businessCalls().isNotEmpty()) return null
        val expected = when {
            write -> listOf("iput-boolean", "return-void")
            boolean -> listOf("iget-boolean", "return")
            else -> listOf("iget-object", "return-object")
        }
        if (mainOps(method) != expected) return null
        val usage = method.businessFields().singleOrNull() ?: return null
        return usage.field.takeIf {
            !Modifier.isStatic(it.modifiers) && it.declaredClassName == method.declaredClassName &&
                (if (write) usage.usingType.isWrite() else usage.usingType.isRead()) &&
                (!boolean || it.typeName == "boolean")
        }
    }

    private fun forwarder(method: MethodData): Forwarder? {
        val expected = when (method.returnTypeName) {
            "void" -> listOf("iget-object", "invoke-virtual", "return-void")
            "boolean" -> listOf("iget-object", "invoke-virtual", "move-result", "return")
            else -> return null
        }
        if (mainOps(method) != expected) return null
        val usage = method.businessFields().singleOrNull() ?: return null
        val call = method.businessCalls().singleOrNull() ?: return null
        if (!usage.usingType.isRead() || Modifier.isStatic(usage.field.modifiers) ||
            usage.field.declaredClassName != method.declaredClassName || usage.field.typeName != call.declaredClassName ||
            !instance(call) || call.returnTypeName != method.returnTypeName || call.paramTypeNames != method.paramTypeNames) return null
        return Forwarder(usage.field, call)
    }

    /** Only direct leaves and the verified Titan dispatch shape are accepted, not arbitrary data flow. */
    private fun mainOps(method: MethodData): List<String>? {
        val ops = withoutTitanPrefix(method) ?: return null
        val end = ops.indexOfFirst { it.startsWith("return") }
        if (end < 0) return null
        val tail = ops.drop(end + 1)
        if (method.opNames.take(2) != listOf("sget-object", "if-nez")) return ops.takeIf { tail.isEmpty() }
        val resultOps = when (method.returnTypeName) {
            "void" -> listOf("return-void")
            "boolean" -> listOf("iget-boolean", "return")
            else -> listOf("iget-object", "check-cast", "return-object")
        }
        if (tail != listOf("move-object", "const", "invoke-interface", "move-result-object", "if-eqz") + resultOps) return null
        val dispatch = method.invokes.filter { it.declaredClassName == INTERCEPTABLE }.singleOrNull() ?: return null
        val signature = when (method.paramTypeNames) {
            emptyList<String>() -> "invokeV" to listOf("int", "java.lang.Object")
            listOf("boolean") -> "invokeZ" to listOf("int", "java.lang.Object", "boolean")
            else -> "invokeL" to listOf("int", "java.lang.Object", "java.lang.Object")
        }
        if (dispatch.methodName != signature.first || dispatch.paramTypeNames != signature.second ||
            dispatch.returnTypeName != INTERCEPT_RESULT) return null
        return ops.take(end + 1)
    }

    private fun withoutTitanPrefix(method: MethodData): List<String>? {
        val ops = method.opNames.map { it.substringBefore('/') }
        if (ops.take(2) != listOf("sget-object", "if-nez")) return ops
        val guard = method.usingFields.firstOrNull() ?: return null
        if (!guard.usingType.isRead() || guard.field.typeName != INTERCEPTABLE ||
            guard.field.declaredClassName != method.declaredClassName || !Modifier.isStatic(guard.field.modifiers)) return null
        return ops.drop(2)
    }

    private fun MethodData.businessFields() = usingFields.filterNot {
        (Modifier.isStatic(it.field.modifiers) && it.field.typeName == INTERCEPTABLE) ||
            it.field.declaredClassName == INTERCEPT_RESULT
    }

    private fun MethodData.businessCalls() = invokes.filterNot { it.declaredClassName == INTERCEPTABLE }

    private fun MethodData.readFields(owner: String, type: String? = null): List<FieldData> = usingFields.filter {
        it.usingType.isRead() && it.field.declaredClassName == owner && !Modifier.isStatic(it.field.modifiers) &&
            (type == null || it.field.typeName == type)
    }.map { it.field }.distinctBy { it.descriptor }

    private fun sdkMethod(
        bridge: DexKitBridge, owner: String, name: String, returns: String, logger: ScanLogger?, vararg params: String,
    ): MethodData? = unique(name, bridge.getClassData(owner)?.methods.orEmpty().filter {
        it.methodName == name && instance(it, returns, *params)
    }, logger) { it.descriptor }

    private fun isList(name: String, cl: ClassLoader): Boolean =
        ScanReflection.safeFindClass(name, cl)?.let { List::class.java.isAssignableFrom(it) } == true

    private fun instance(method: MethodData): Boolean = !Modifier.isStatic(method.modifiers) && !method.isConstructor

    private fun instance(method: MethodData, returns: String, vararg params: String): Boolean =
        instance(method) && method.returnTypeName == returns && method.paramTypeNames == params.toList()

    private fun MethodData.spec(): String = "$methodName|$returnTypeName|${paramTypeNames.joinToString(",")}"

    private fun MethodData.calls(method: MethodData): Boolean = invokes.any { it.descriptor == method.descriptor }

    private fun MethodData.reads(field: FieldData): Boolean = usingFields.any {
        it.usingType.isRead() && it.field.descriptor == field.descriptor
    }

    private fun MethodData.calls(owner: String, name: String, returns: String, vararg params: String): Boolean =
        invokes.any { it.declaredClassName == owner && it.methodName == name &&
            it.returnTypeName == returns && it.paramTypeNames == params.toList() }

    private fun MethodData.listCall(name: String, returns: String, vararg params: String): Boolean =
        declaredClassName in setOf("java.util.List", "java.util.ArrayList") && methodName == name &&
            returnTypeName == returns && paramTypeNames == params.toList()

    private inline fun <T> role(name: String, logger: ScanLogger?, block: () -> T?): T? =
        scanSubStep("$TAG.$name", logger, null, block)

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
