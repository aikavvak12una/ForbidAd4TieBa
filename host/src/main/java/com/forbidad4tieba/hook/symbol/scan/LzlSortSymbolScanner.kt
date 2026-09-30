package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The default setter is used only during page initialization, never by the H5 sort handler. */
internal object LzlSortSymbolScanner {
    const val MODEL = "com.baidu.tieba.pb.pb.sub.SubPbModel"
    private const val ACTIVITY = "com.baidu.tieba.pb.pb.sub.NewSubPbActivity"
    private const val REQUEST = "com.baidu.tieba.pb.pb.sub.SubPbRequestMessage"

    fun scan(cl: ClassLoader, logger: ScanLogger?): String? {
        val owner = ScanReflection.safeFindClass(MODEL, cl) ?: return null
        val activityMethods = ScanDexQueries.methods(ACTIVITY, logger)
        val onCreate = activityMethods.singleOrNull {
            it.methodName == "onCreate" && it.returnTypeName == "void" &&
                it.paramTypeNames == listOf("android.os.Bundle") && !Modifier.isStatic(it.modifiers)
        } ?: return null
        val initialization = uniqueSemanticCandidate("LzlSort.Initialization", activityMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.returnTypeName == "void" &&
                method.paramTypeNames == listOf("android.os.Bundle") &&
                method.usingStrings.containsAll(listOf("key_open_editor_tips", "key_forum_shop_role_info")) &&
                onCreate.invokes.any { it.descriptor == method.descriptor } &&
                method.invokes.any { it.declaredClassName == MODEL && it.isConstructor } &&
                method.calls(MODEL, "loadData")
        }, logger) ?: return null

        val modelMethods = ScanDexQueries.methods(MODEL, logger)
        val evidence = modelMethods.filter { method ->
            if (method.returnTypeName != "void" || method.paramTypeNames != listOf("java.lang.String") ||
                Modifier.isStatic(method.modifiers) || !method.usingStrings.contains("2")
            ) return@filter false
            val field = method.instanceFields(MODEL, "java.lang.String", FieldUsingType.Write).singleOrNull()
                ?: return@filter false
            // An added caller may be a manual sort action. Stop adapting rather than force that action.
            val callers = method.callers.distinctBy { it.descriptor }
            callers.size == 1 && callers.single().descriptor == initialization.descriptor &&
                initialization.invokes.any { it.descriptor == method.descriptor } &&
                modelMethods.any { consumer ->
                    consumer.invokes.any { it.declaredClassName == REQUEST && it.isConstructor } &&
                        consumer.instanceFields(MODEL, "java.lang.String", FieldUsingType.Read)
                            .any { it.descriptor == field.descriptor }
                }
        }.map { it.getMethodInstance(cl) }.toSet()
        return selectSetter(owner, logger) { it in evidence }?.name
    }

    internal fun selectSetter(owner: Class<*>, logger: ScanLogger?, hasEvidence: (Method) -> Boolean): Method? =
        uniqueSemanticCandidate("LzlSort.DefaultSetter", owner.declaredMethods.filter {
            isSetter(it) && hasEvidence(it)
        }, logger)

    internal fun isSetter(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(String::class.java))
}
