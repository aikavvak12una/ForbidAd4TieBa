package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The current comment list owns prefetch requests, session checks and in-flight deduplication. */
internal object PbCommentPreloadSymbolScanner {
    private const val FRAGMENT = "com.baidu.tieba.pb.pagebrowser.comment.ui.CommentListFragment"
    private const val RECYCLER = "com.baidu.tieba.pb.pagebrowser.comment.ui.CommentRecyclerView"
    private const val VIEW_MODEL = "com.baidu.tieba.pb.pagebrowser.comment.viewmodel.CommentListViewModel"

    fun scan(cl: ClassLoader, logger: ScanLogger?): String? =
        ScanDexQueries.query("PbCommentAutoLoadHook.Config", logger) { bridge ->
            val candidates = bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba")
                .matcher(MethodMatcher.create().addEqString("pb_preload_not_see_floor_num_sp_key")))
                .filter { method ->
                    !Modifier.isStatic(method.modifiers) && method.paramTypeNames.isEmpty() &&
                        method.returnTypeName == "int" &&
                        method.calls("com.baidu.tbadk.core.sharedPref.SharedPrefHelper", "getInt") &&
                        method.calls(StableTiebaHookPoints.UBS_AB_TEST_HELPER_CLASS, "isPbCommentPreloadMore") &&
                        method.callers.any { caller ->
                            val fields = bridge.getClassData(caller.declaredClassName)?.fields.orEmpty()
                            caller.returnTypeName == "void" && caller.paramTypeNames ==
                                listOf(StableTiebaHookPoints.RECYCLER_VIEW_CLASS, "int", "int") &&
                                fields.any { it.typeName == FRAGMENT && !Modifier.isStatic(it.modifiers) } &&
                                fields.any { it.typeName == RECYCLER && !Modifier.isStatic(it.modifiers) } &&
                                caller.invokes.any { it.declaredClassName == VIEW_MODEL &&
                                    it.returnTypeName == "boolean" && it.paramTypeNames == listOf("int", "int", "int") }
                        }
                }.distinctBy { it.descriptor }
            val method = selectUniqueScanCandidate("PbCommentAutoLoadHook.Config", candidates, logger) { it.descriptor }
                ?: return@query null
            val spec = "${method.declaredClassName}#${method.methodName}"
            check(restore(cl, spec) != null) { "Invalid comment prefetch threshold signature" }
            spec
        }

    fun restore(cl: ClassLoader, spec: String?): Method? {
        if (spec.isNullOrBlank()) return null
        return scanSubStep("PbCommentAutoLoadHook.Restore", null, null as Method?) {
            val parts = spec.split('#')
            check(parts.size == 2 && parts.all(String::isNotBlank))
            Class.forName(parts[0], false, cl).getDeclaredMethod(parts[1]).apply {
                check(Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) &&
                    !Modifier.isAbstract(modifiers) && returnType == Int::class.javaPrimitiveType)
                isAccessible = true
            }
        }
    }
}
