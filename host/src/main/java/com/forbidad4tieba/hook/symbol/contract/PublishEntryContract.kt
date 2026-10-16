package com.forbidad4tieba.hook.symbol.contract

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Stable experiment readers are validated at installation, independently of the scan cache. */
object PublishEntryContract {
    fun resolve(classLoader: ClassLoader): List<Method> = validate(
        Class.forName("com.baidu.tbadk.abtest.ABTestDisableConfigManager", false, classLoader),
    )

    internal fun validate(owner: Class<*>): List<Method> = listOf("isHit", "isHitOnce").map { name ->
        owner.getDeclaredMethod(name, String::class.java).apply {
            check(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) &&
                returnType == Boolean::class.javaPrimitiveType
            ) { "Invalid experiment reader: $this" }
            isAccessible = true
        }
    }
}
