package com.forbidad4tieba.hook.feature.privacy

import android.content.ContentResolver
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Blocks Baidu cross-app / historical identifier links while leaving ordinary device info reads alone.
 */
object PrivacyIdentifierBlockHook {
    private const val TAG = "[PrivacyIdentifierBlockHook]"
    private const val BAIDU_UUID_KEY = "com.baidu.uuid"
    private val installed = AtomicBoolean(false)

    private data class MethodTarget(
        val className: String,
        val methodName: String,
        val returnValue: Any?,
        val parameterTypeNames: List<String> = emptyList(),
        val staticMethod: Boolean,
    )

    private val CUID_TARGETS = arrayOf(
        MethodTarget(
            className = "com.baidu.android.bdutil.cuid.sdk.AppCuidManager",
            methodName = "getCuid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.android.bdutil.cuid.sdk.AppCuidManager",
            methodName = "getEnCuid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.android.bdutil.cuid.sdk.DefaultIAppCuidManager",
            methodName = "getCuid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.android.bdutil.cuid.sdk.DefaultIAppCuidManager",
            methodName = "getEnCuid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.util.BaiduIdentityManager",
            methodName = "getUid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.util.BaiduIdentityManager",
            methodName = "getEnUid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.util.BaiduIdentityManager",
            methodName = "getC3Aid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.util.BaiduIdentityManager",
            methodName = "setCUIDCookie",
            returnValue = null,
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.searchbox.util.CuidCookieSync",
            methodName = "setCUIDCookie",
            returnValue = null,
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.tbadk.core.TbadkCoreApplication",
            methodName = "getCuid",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.tbadk.core.TbadkCoreApplication",
            methodName = "getCuidGalaxy2",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.tbadk.core.TbadkCoreApplication",
            methodName = "getCuidGalaxy3",
            returnValue = "",
            staticMethod = false,
        ),
        MethodTarget(
            className = "com.baidu.tbadk.core.TbadkCoreApplication",
            methodName = "getCuidGid",
            returnValue = "",
            staticMethod = false,
        ),
    )

    fun hook(cl: ClassLoader) {
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        for (target in CUID_TARGETS) {
            val clazz = XposedCompat.findClassOrNull(target.className, cl) ?: continue
            val method = resolveExactMethod(clazz, target, cl) ?: continue
            try {
                method.isAccessible = true
                mod.hook(method).intercept { target.returnValue }
                totalInstalled++
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG hook ${target.className}.${target.methodName} skipped: ${t.message}" }
            }
        }
        if (hookBaiduUuidSystemKey()) {
            totalInstalled += 2
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            XposedCompat.logD("$TAG no identifier methods found in this version")
        }
    }

    private fun hookBaiduUuidSystemKey(): Boolean {
        val mod = XposedCompat.module ?: return false
        val clazz = android.provider.Settings.System::class.java
        var count = 0
        try {
            val getString = clazz.getDeclaredMethod(
                "getString",
                ContentResolver::class.java,
                String::class.java,
            )
            getString.isAccessible = true
            mod.hook(getString).intercept { chain ->
                if (chain.args.getOrNull(1) == BAIDU_UUID_KEY) {
                    return@intercept ""
                }
                chain.proceed()
            }
            count++
        } catch (t: Throwable) {
            XposedCompat.logD { "$TAG hook Settings.System.getString skipped: ${t.message}" }
        }
        try {
            val putString = clazz.getDeclaredMethod(
                "putString",
                ContentResolver::class.java,
                String::class.java,
                String::class.java,
            )
            putString.isAccessible = true
            mod.hook(putString).intercept { chain ->
                if (chain.args.getOrNull(1) == BAIDU_UUID_KEY) {
                    return@intercept true
                }
                chain.proceed()
            }
            count++
        } catch (t: Throwable) {
            XposedCompat.logD { "$TAG hook Settings.System.putString skipped: ${t.message}" }
        }
        return count > 0
    }

    private fun resolveExactMethod(clazz: Class<*>, target: MethodTarget, cl: ClassLoader): Method? {
        val parameterTypes = target.parameterTypeNames.map { typeName ->
            resolveClass(typeName, cl) ?: run {
                XposedCompat.logD { "$TAG parameter class NOT FOUND: ${target.className}.${target.methodName} $typeName" }
                return null
            }
        }.toTypedArray()
        val method = try {
            clazz.getDeclaredMethod(target.methodName, *parameterTypes)
        } catch (_: NoSuchMethodException) {
            XposedCompat.logD { "$TAG method NOT FOUND: ${target.className}.${target.methodName}" }
            return null
        }
        if (Modifier.isStatic(method.modifiers) != target.staticMethod) {
            XposedCompat.logD { "$TAG method skipped: static mismatch ${target.className}.${target.methodName}" }
            return null
        }
        return method
    }

    private fun resolveClass(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            "long" -> Long::class.javaPrimitiveType
            else -> XposedCompat.findClassOrNull(typeName, cl)
        }
    }
}
