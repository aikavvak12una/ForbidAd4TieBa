package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val HOME_TOP_TAB_RECOMMEND_CODE = "recommend"

/** Owns host member access and its positive/negative caches; does not retain page instances. */
internal class HomeNativeGlassHostAccessors(
    private val onDynamicBackgroundError: (String, Throwable) -> Unit,
) {
    private val emManagerFromViewFields = ConcurrentHashMap<Class<*>, Field>()
    private val emManagerFromViewMissingClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val emManagerRealBackgroundColorMethods = ConcurrentHashMap<Class<*>, Method>()
    private val emManagerRealBackgroundColorMissingClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val emManagerFromMethods = ConcurrentHashMap<Class<*>, Method>()
    private val emManagerFromMethodMissingClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val chromeNoArgViewMethods = ConcurrentHashMap<RuntimeMethodKey, Method>()
    private val chromeNoArgViewMissingMethods =
        Collections.newSetFromMap(ConcurrentHashMap<RuntimeMethodKey, Boolean>())
    private val chromeBooleanMethods = ConcurrentHashMap<RuntimeMethodKey, Method>()
    private val chromeBooleanMissingMethods =
        Collections.newSetFromMap(ConcurrentHashMap<RuntimeMethodKey, Boolean>())

    fun readEmManagerView(manager: Any): View? {
        return try {
            cachedFieldInHierarchy(
                manager.javaClass,
                "fromView",
                emManagerFromViewFields,
                emManagerFromViewMissingClasses,
            )?.get(manager) as? View
        } catch (t: Throwable) {
            onDynamicBackgroundError("EMManager.fromView unavailable", t)
            null
        }
    }

    fun createEmManagerForView(view: View): Any? {
        return try {
            val classLoader = view.javaClass.classLoader ?: return null
            val managerClass = XposedCompat.findClassOrNull(StableTiebaHookPoints.EM_MANAGER_CLASS, classLoader)
                ?: return null
            val fromMethod = cachedMethodInHierarchy(
                managerClass,
                "from",
                emManagerFromMethods,
                emManagerFromMethodMissingClasses,
                View::class.java,
            ) ?: return null
            fromMethod.invoke(null, view)
        } catch (t: Throwable) {
            onDynamicBackgroundError("EMManager.from(view) unavailable", t)
            null
        }
    }

    fun applyEmManagerRealBackgroundColor(manager: Any, color: Int): Boolean {
        return try {
            cachedMethodInHierarchy(
                manager.javaClass,
                "setBackGroundRealColor",
                emManagerRealBackgroundColorMethods,
                emManagerRealBackgroundColorMissingClasses,
                java.lang.Integer.TYPE,
            )
                ?.invoke(manager, color)
                ?: return false
            true
        } catch (t: Throwable) {
            onDynamicBackgroundError("EMManager dynamic background failed", t)
            false
        }
    }

    private fun cachedFieldInHierarchy(
        clazz: Class<*>,
        name: String,
        cache: ConcurrentHashMap<Class<*>, Field>,
        missing: MutableSet<Class<*>>,
    ): Field? {
        cache[clazz]?.let { return it }
        if (missing.contains(clazz)) return null
        val field = findFieldInHierarchy(clazz, name)
        if (field != null) {
            cache[clazz] = field
        } else {
            missing.add(clazz)
        }
        return field
    }

    private fun cachedMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        cache: ConcurrentHashMap<Class<*>, Method>,
        missing: MutableSet<Class<*>>,
        vararg paramTypes: Class<*>,
    ): Method? {
        cache[clazz]?.let { return it }
        if (missing.contains(clazz)) return null
        val method = findMethodInHierarchy(clazz, name, *paramTypes)
        if (method != null) {
            cache[clazz] = method
        } else {
            missing.add(clazz)
        }
        return method
    }

    fun invokeNoArgView(target: View, methodName: String): View? {
        return try {
            cachedRuntimeMethodInHierarchy(
                target.javaClass,
                methodName,
                "()View",
                chromeNoArgViewMethods,
                chromeNoArgViewMissingMethods,
                validate = { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        View::class.java.isAssignableFrom(method.returnType)
                },
            )?.invoke(target) as? View
        } catch (_: Throwable) {
            null
        }
    }

    fun invokeBooleanMethod(target: View, methodName: String, value: Boolean): Boolean {
        return try {
            cachedRuntimeMethodInHierarchy(
                target.javaClass,
                methodName,
                "(boolean)",
                chromeBooleanMethods,
                chromeBooleanMissingMethods,
                validate = { method ->
                    !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE
                },
                java.lang.Boolean.TYPE,
            )
                ?.invoke(target, value)
                ?: return false
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun cachedRuntimeMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        signature: String,
        cache: ConcurrentHashMap<RuntimeMethodKey, Method>,
        missing: MutableSet<RuntimeMethodKey>,
        validate: (Method) -> Boolean,
        vararg paramTypes: Class<*>,
    ): Method? {
        val key = RuntimeMethodKey(clazz, name, signature)
        cache[key]?.let { return it }
        if (missing.contains(key)) return null
        val method = findMethodInHierarchy(clazz, name, *paramTypes)
        if (method != null && validate(method)) {
            cache[key] = method
            return method
        }
        missing.add(key)
        return null
    }

    fun resolveCurrentTopTabItem(topChrome: View): Any? {
        return runCatching {
            XposedCompat.callMethod(topChrome, "getCurrentFragmentTabItem")
        }.getOrNull()
    }

    fun resolveRecommendTopTabItemState(item: Any, runtimeTargets: RuntimeTargets?): Boolean? {
        val targets = runtimeTargets ?: return null
        val code = targets.homeTabItemCodeField?.let { fieldName ->
            runCatching { XposedCompat.getObjectField(item, fieldName) as? String }.getOrNull()
        }
        if (code == HOME_TOP_TAB_RECOMMEND_CODE) return true
        if (!code.isNullOrBlank()) return false
        val type = targets.homeTabItemTypeField?.let { fieldName ->
            runCatching { (XposedCompat.getObjectField(item, fieldName) as? Number)?.toInt() }.getOrNull()
        }
        return type?.let { it == HOME_TOP_TAB_RECOMMEND_TYPE }
    }

    private data class RuntimeMethodKey(
        val clazz: Class<*>,
        val name: String,
        val signature: String,
    )
}
