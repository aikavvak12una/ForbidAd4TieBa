package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object CommentFilterSymbolScanner {
    const val PAGE = "tbclient.PbPage.DataRes"
    const val FLOOR = "tbclient.PbFloor.DataRes"
    enum class Path(val protocol: String, val json: Boolean) {
        PAGE_NATIVE(PAGE, false), PAGE_JSON(PAGE, true), FLOOR_NATIVE(FLOOR, false), FLOOR_JSON(FLOOR, true),
    }

    fun scan(path: Path, cl: ClassLoader, logger: ScanLogger?): String? {
        val candidates = ScanDexQueries.query("CommentFilter.${path.name}", logger) { bridge ->
            bridge.findMethod(FindMethod.create().searchPackages("com.baidu.tieba").matcher(
                MethodMatcher.create().paramTypes(path.protocol),
            )).filter { method ->
                val reads = method.usingFields.filter { it.usingType == FieldUsingType.Read }
                    .map { it.field }.filter { it.declaredClassName == path.protocol }.map { it.name }.toSet()
                val fields = if (path.protocol == PAGE) setOf("post_list", "user_list", "thread")
                    else setOf("subpost_list", "post", "thread")
                val semantic = if (path.json) method.usingStrings.containsAll(fields) else
                    method.calls("com.baidu.tbadk.core.data.ThreadData", "parserProtobuf") &&
                        (path.protocol != PAGE || "trace_code" in method.usingStrings)
                fields.all { it in reads } && semantic
            }.map { it.getMethodInstance(cl) }
        }.orEmpty()
        return select(path, candidates, logger)?.let { "${it.declaringClass.name}#${it.name}" }
    }

    internal fun select(path: Path, candidates: List<Method>, logger: ScanLogger?): Method? =
        uniqueSemanticCandidate("CommentFilter.${path.name}", candidates.filter { valid(it, path) }, logger)

    internal fun valid(method: Method, path: Path): Boolean =
        !Modifier.isAbstract(method.modifiers) && !method.isBridge &&
            method.parameterTypes.map { it.name } == listOf(path.protocol) &&
            when {
                path.json -> Modifier.isStatic(method.modifiers) && method.returnType.name == "org.json.JSONObject"
                path.protocol == PAGE -> !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
                else -> Modifier.isStatic(method.modifiers) && method.returnType == method.declaringClass
            }

    fun restore(descriptor: String, path: Path, cl: ClassLoader): Method {
        val parts = descriptor.split('#')
        require(parts.size == 2)
        return Class.forName(parts[0], false, cl).getDeclaredMethod(parts[1], Class.forName(path.protocol, false, cl)).apply {
            check(valid(this, path)) { "Invalid comment parser: $descriptor" }
            isAccessible = true
        }
    }
}
