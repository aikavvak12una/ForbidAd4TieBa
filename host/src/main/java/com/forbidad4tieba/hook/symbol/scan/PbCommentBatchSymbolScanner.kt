package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.PbCommentBatchSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** All batch consumers are traced from the native threshold, request and canonical commit anchors. */
internal object PbCommentBatchSymbolScanner {
    private const val VM = "com.baidu.tieba.pb.pagebrowser.comment.viewmodel.CommentListViewModel"
    private const val FRAGMENT = "com.baidu.tieba.pb.pagebrowser.comment.ui.CommentListFragment"
    private const val MODE = "com.baidu.tieba.pb.pagebrowser.comment.tab.PbCommentListCommitMode"
    private const val KIND = "com.baidu.tieba.pb.pagebrowser.comment.tab.PbTabRequestKind"
    private const val DATA = "com.baidu.tieba.tbadkcore.data.PbDataType"

    fun scan(cl: ClassLoader, configSpec: String?, logger: ScanLogger?): String? {
        val config = PbCommentPreloadSymbolScanner.restore(cl, configSpec) ?: return null
        return ScanDexQueries.query("PbCommentAutoLoadHook.Batch", logger) { bridge ->
            fun one(label: String, candidates: List<MethodData>): MethodData = requireNotNull(
                selectUniqueScanCandidate("CommentBatch.$label", candidates.distinctBy { it.descriptor }, logger) { it.descriptor },
            ) { "Missing or ambiguous batch $label" }
            val scroll = one("scroll", bridge.getMethodData(config)!!.callers.filter {
                it.returnTypeName == "void" && it.paramTypeNames == listOf(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, "int", "int") &&
                    bridge.getClassData(it.declaredClassName)!!.fields.any { field -> field.typeName == FRAGMENT }
            })
            val fragment = requireNotNull(selectUniqueScanCandidate("CommentBatch.fragment",
                bridge.getClassData(scroll.declaredClassName)!!.fields.filter {
                    it.typeName == FRAGMENT && !Modifier.isStatic(it.modifiers)
                }, logger) { it.descriptor })
            val prefetch = one("prefetch", scroll.invokes.filter {
                it.declaredClassName == VM && it.returnTypeName == "boolean" && it.paramTypeNames == listOf("int", "int", "int")
            })
            val request = one("request", bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba")
                .matcher(MethodMatcher.create().addEqString("正在请求数据，拦截刷新"))).filter {
                it.declaredClassName == VM && it.returnTypeName == "boolean" && it.paramTypeNames.size == 3 &&
                    it.paramTypeNames[1] == KIND && it.paramTypeNames[2] == "kotlin.jvm.functions.Function1"
            })
            check(prefetch.invokes.any { call -> call.descriptor == request.descriptor ||
                (call.declaredClassName == VM && Modifier.isStatic(call.modifiers) &&
                    call.invokes.any { it.descriptor == request.descriptor }) })
            val commit = one("commit", bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba")
                .matcher(MethodMatcher.create().addEqString("discard canonical list: tab="))).filter {
                it.declaredClassName == VM && it.paramTypeNames.size == 4 && it.paramTypeNames[2] == MODE &&
                    it.paramTypeNames[3] == DATA && it.returnTypeName == it.paramTypeNames[1]
            })
            val loading = one("loading", request.invokes.filter {
                it.declaredClassName == VM && it.returnTypeName == "void" &&
                    it.paramTypeNames == listOf(commit.paramTypeNames[0], "boolean", "boolean")
            })
            val items = one("items", prefetch.invokes.filter {
                it.declaredClassName == VM && it.returnTypeName == "java.util.List" && it.paramTypeNames.isEmpty() &&
                    commit.invokes.any { call -> call.descriptor == it.descriptor }
            })
            val json = JSONObject().put("scrollClass", scroll.declaredClassName).put("fragment", fragment.fieldName)
            listOf("scroll" to scroll, "prefetch" to prefetch, "request" to request,
                "loading" to loading, "commit" to commit, "items" to items).forEach { (key, method) ->
                json.put(key, method.methodName)
            }
            json.toString().also { check(restore(cl, it) != null) { "Invalid batch target structure" } }
        }
    }

    fun restore(cl: ClassLoader, spec: String?): PbCommentBatchSymbols? {
        if (spec.isNullOrBlank()) return null
        return scanSubStep("PbCommentAutoLoadHook.Batch.Restore", null, null as PbCommentBatchSymbols?) {
            val json = JSONObject(spec)
            val vm = Class.forName(VM, false, cl)
            val fragmentClass = Class.forName(FRAGMENT, false, cl)
            fun member(owner: Class<*>, key: String): Method = owner.declaredMethods.single {
                it.name == json.getString(key)
            }.apply {
                check(Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers))
                isAccessible = true
            }
            val scroll = member(Class.forName(json.getString("scrollClass"), false, cl), "scroll")
            check(scroll.returnType == Void.TYPE && scroll.parameterTypes.map { it.name } ==
                listOf(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, "int", "int"))
            val fragment = scroll.declaringClass.getDeclaredField(json.getString("fragment")).apply {
                check(type == fragmentClass && !Modifier.isStatic(modifiers)); isAccessible = true
            }
            val prefetch = member(vm, "prefetch")
            check(prefetch.returnType == Boolean::class.javaPrimitiveType && prefetch.parameterTypes.map { it.name } == listOf("int", "int", "int"))
            val request = member(vm, "request")
            check(request.returnType == Boolean::class.javaPrimitiveType && request.parameterTypes.size == 3 &&
                request.parameterTypes[1].name == KIND && request.parameterTypes[2].name == "kotlin.jvm.functions.Function1")
            val commit = member(vm, "commit")
            check(commit.parameterTypes.size == 4 && commit.parameterTypes[2].name == MODE &&
                commit.parameterTypes[3].name == DATA && commit.returnType == commit.parameterTypes[1])
            val loading = member(vm, "loading")
            check(loading.returnType == Void.TYPE && loading.parameterTypes.toList() ==
                listOf(commit.parameterTypes[0], Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
            val items = member(vm, "items")
            check(items.returnType == List::class.java && items.parameterTypes.isEmpty())
            fun lifecycle(name: String) = fragmentClass.getDeclaredMethod(name).apply {
                check(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
            }
            PbCommentBatchSymbols(scroll, fragment, prefetch, request, loading, commit, items,
                lifecycle("onPause"), lifecycle("onDestroyView"),
                requireNotNull(commit.parameterTypes[2].enumConstants).single { (it as Enum<*>).name == "APPEND" },
                requireNotNull(request.parameterTypes[1].enumConstants).single { (it as Enum<*>).name == "APPEND" },
                requireNotNull(commit.parameterTypes[3].enumConstants).single { (it as Enum<*>).name == "PB_DATA_TYPE_NETWORK" })
        }
    }
}
