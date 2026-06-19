package com.forbidad4tieba.hook.feature.perf

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 在代码层阻断广告 SDK 初始化，减少启动耗时、网络连接、线程池和内存占用。
 *
 * 这是运行期阻断逻辑，不再修改宿主 Manifest 组件状态。
 * 广告 SDK 初始化经常在 Application.onCreate / ContentProvider.onCreate 中执行，比组件启动更早。
 *
 * 由现有性能优化开关 isAdSdkComponentsDisabled 控制。
 */
object AdSdkInitBlockHook {
    private const val TAG = "[AdSdkInitBlockHook]"
    private val installed = AtomicBoolean(false)

    private data class MethodTarget(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val parameterTypeNames: List<String> = emptyList(),
    )

    private val AD_SDK_INIT_TARGETS = arrayOf(
        MethodTarget(
            className = "com.bytedance.sdk.openadsdk.TTAdSdk",
            methodName = "init",
            returnTypeName = "boolean",
            parameterTypeNames = listOf(
                "android.content.Context",
                "com.bytedance.sdk.openadsdk.TTAdConfig",
            ),
        ),
        MethodTarget(
            className = "com.bytedance.sdk.openadsdk.TTAdSdk",
            methodName = "start",
            returnTypeName = "void",
            parameterTypeNames = listOf("com.bytedance.sdk.openadsdk.TTAdSdk\$Callback"),
        ),
        MethodTarget(
            className = "com.kwad.sdk.api.KsAdSDK",
            methodName = "init",
            returnTypeName = "boolean",
            parameterTypeNames = listOf(
                "android.content.Context",
                "com.kwad.sdk.api.SdkConfig",
            ),
        ),
        MethodTarget(
            className = "com.kwad.sdk.api.KsAdSDK",
            methodName = "start",
            returnTypeName = "void",
        ),
        MethodTarget(
            className = "com.qq.e.comm.managers.GDTAdSdk",
            methodName = "init",
            returnTypeName = "void",
            parameterTypeNames = listOf("android.content.Context", "java.lang.String"),
        ),
        MethodTarget(
            className = "com.qq.e.comm.managers.GDTAdSdk",
            methodName = "initWithoutStart",
            returnTypeName = "void",
            parameterTypeNames = listOf("android.content.Context", "java.lang.String"),
        ),
        MethodTarget(
            className = "com.qq.e.comm.managers.GDTAdSdk",
            methodName = "start",
            returnTypeName = "void",
            parameterTypeNames = listOf("com.qq.e.comm.managers.GDTAdSdk\$OnStartListener"),
        ),
    )

    fun hook(cl: ClassLoader) {
        if (!ConfigManager.isAdSdkComponentsDisabled) {
            XposedCompat.logD("$TAG skipped: config disabled")
            return
        }
        if (!installed.compareAndSet(false, true)) return

        val mod = XposedCompat.module ?: run {
            installed.set(false)
            return
        }

        var totalInstalled = 0
        for (target in AD_SDK_INIT_TARGETS) {
            val clazz = XposedCompat.findClassOrNull(target.className, cl) ?: continue
            val method = resolveExactMethod(clazz, target, cl) ?: continue
            try {
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (ConfigManager.isAdSdkComponentsDisabled) {
                        return@intercept nullReturnValue(method)
                    }
                    chain.proceed()
                }
                totalInstalled++
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG hook ${target.className}.${target.methodName} skipped: ${t.message}" }
            }
        }

        if (totalInstalled > 0) {
            XposedCompat.log("$TAG hooks INSTALLED: count=$totalInstalled")
        } else {
            XposedCompat.logD("$TAG no ad SDK init methods found in this version")
        }
    }

    private fun nullReturnValue(method: Method): Any? {
        return when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Void.TYPE -> null
            else -> null
        }
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
        if (!Modifier.isStatic(method.modifiers)) {
            XposedCompat.logD { "$TAG method skipped: non-static ${target.className}.${target.methodName}" }
            return null
        }
        val returnType = resolveClass(target.returnTypeName, cl) ?: return null
        if (method.returnType != returnType) {
            XposedCompat.logD {
                "$TAG method skipped: return mismatch ${target.className}.${target.methodName} " +
                    "${method.returnType.name} != ${returnType.name}"
            }
            return null
        }
        return method
    }

    private fun resolveClass(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "byte" -> Byte::class.javaPrimitiveType
            "char" -> Char::class.javaPrimitiveType
            "short" -> Short::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            "long" -> Long::class.javaPrimitiveType
            "float" -> Float::class.javaPrimitiveType
            "double" -> Double::class.javaPrimitiveType
            else -> XposedCompat.findClassOrNull(typeName, cl)
        }
    }
}
