package com.forbidad4tieba.hook.ui

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object TiebaAccountIdentity {
    private val methodCache = WeakHashMap<ClassLoader, Method>()

    fun currentAccountId(context: Context): String? {
        val loader = context.classLoader ?: return null
        val method = synchronized(methodCache) {
            methodCache[loader] ?: runCatching {
                val appClass = Class.forName(
                    StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS,
                    false,
                    loader,
                )
                appClass.getDeclaredMethod(StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT)
                    .apply { isAccessible = true }
            }.getOrNull()?.also { methodCache[loader] = it }
        } ?: return null

        return runCatching {
            when (val value = method.invoke(null)) {
                is Number -> value.toLong()
                    .takeIf { it > 0L }
                    ?.toString()
                is String -> value.trim()
                    .takeIf { it.isNotEmpty() && it != "0" }
                else -> null
            }
        }.getOrNull()
    }
}
