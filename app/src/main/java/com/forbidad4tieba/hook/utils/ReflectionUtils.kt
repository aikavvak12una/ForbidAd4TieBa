package com.forbidad4tieba.hook.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.lang.reflect.Method

/**
 * 澶氫釜 hook 绫诲叡鐢ㄧ殑鍙嶅皠宸ュ叿銆? * 鏀惧湪杩欓噷鍙互鍑忓皯閲嶅浠ｇ爜锛屽苟璁╄涓轰繚鎸佷竴鑷淬€? */
object ReflectionUtils {

    /**
     * 娌跨被缁ф壙閾炬寜鍚嶇О鍜屽弬鏁扮被鍨嬫煡鎵惧０鏄庢柟娉曘€?     *
     * 閬囧埌 [Any] 涔熷氨鏄?java.lang.Object 灏卞仠姝紝閬垮厤鎵埌妗嗘灦鍐呴儴銆?     * 杩斿洖鐨勬柟娉曚細璁剧疆涓哄彲璁块棶銆?     */
    fun findMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        vararg paramTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredMethod(name, *paramTypes)
                    .apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    /**
     * 娌跨被缁ф壙閾炬煡鎵惧０鏄庢柟娉曘€?     * 鏂规硶闇€瑕佸悓鏃跺尮閰?[name] 鍜岃嚜瀹氫箟 [predicate]銆?     */
    fun findMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        predicate: (Method) -> Boolean,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val method = current.declaredMethods.firstOrNull { it.name == name && predicate(it) }
            if (method != null) {
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }
        return null
    }

    /**
     * 浠?[Context] 閲屽彇瀹夸富 [Activity]銆?     * 鏈€澶氬悜澶栨媶 [maxDepth] 灞?[ContextWrapper]銆?     */
    fun findActivityFromContext(context: Context?, maxDepth: Int = 12): Activity? {
        var current = context
        var guard = 0
        while (current != null && guard < maxDepth) {
            if (current is Activity) return current
            current = if (current is ContextWrapper) current.baseContext else null
            guard++
        }
        return null
    }

    /**
     * 涓?[Method] 鐢熸垚渚夸簬闃呰鐨勫敮涓€閿€?     * 鍐呭鍖呭惈澹版槑绫汇€佹柟娉曞悕鍜屽弬鏁扮被鍨嬨€?     */
    fun methodSignature(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.declaringClass.name}#${method.name}($params)"
    }
}
