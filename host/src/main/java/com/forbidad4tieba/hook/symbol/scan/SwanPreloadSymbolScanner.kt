package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object SwanPreloadSymbolScanner {
    private const val DISPATCH_ACTION = "com.baidu.searchbox.action.SWAN_APP_MSG_SERVICE_PRELOAD_NEXT"
    private const val SCENE = "bundle_key_preload_preload_scene"

    fun scan(cl: ClassLoader, logger: ScanLogger?): Pair<String?, String?> {
        val owners = ScanDexQueries.classesUsingStrings(logger, DISPATCH_ACTION)
        val methods = owners.flatMap { ScanDexQueries.methods(it, logger) }
        val dispatch = uniqueSemanticCandidate("SwanPreload.Dispatch", methods.filter {
            isShape(it.modifiers, it.returnTypeName, it.paramTypeNames, false) && DISPATCH_ACTION in it.usingStrings &&
                it.calls("android.content.Context", "startService")
        }, logger)
        val schedule = uniqueSemanticCandidate("SwanPreload.Schedule", methods.filter {
            isShape(it.modifiers, it.returnTypeName, it.paramTypeNames, true) &&
                it.usingStrings.containsAll(listOf(SCENE, "by_host_launch", "by_preload")) &&
                it.calls("com.baidu.searchbox.process.ipc.util.ProcessUtils", "isMainProcess")
        }, logger)
        fun descriptor(method: org.luckypray.dexkit.result.MethodData?, scheduled: Boolean): String? {
            val member = method?.getMethodInstance(cl) ?: return null
            check(isShape(member.modifiers, member.returnType.name, member.parameterTypes.map { it.name }, scheduled))
            return "${member.declaringClass.name}#${member.name}"
        }
        return descriptor(dispatch, false) to descriptor(schedule, true)
    }

    internal fun isShape(modifiers: Int, result: String, params: List<String>, schedule: Boolean): Boolean =
        Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers) && result == "void" &&
            if (schedule) params.size == 3 && params.first() == "android.content.Context" && params.last() == "android.os.Bundle"
            else params == listOf("android.content.Context", "android.os.Bundle")

    fun restore(cl: ClassLoader, descriptor: String, schedule: Boolean): Method {
        val parts = descriptor.split('#')
        require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
        return Class.forName(parts[0], false, cl).declaredMethods.singleOrNull {
            it.name == parts[1] && isShape(it.modifiers, it.returnType.name, it.parameterTypes.map { type -> type.name }, schedule)
        }?.apply { isAccessible = true } ?: error("Invalid Swan preload target")
    }
}
