package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassHostDarkModeSwitchTargets
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal object HomeNativeGlassHostDarkModeBridge {
    private const val LEGACY_DARK_SKIN_TYPE = 1
    private const val DARK_SKIN_TYPE = 4
    private const val LIGHT_SKIN_TYPE = 0
    private const val HOST_SKIN_TYPE_PREF_KEY = "skin_"
    private const val FOLLOW_SYSTEM_MODE_PREF_KEY = "key_is_follow_system_mode"
    private const val HOST_TBADK_SETTINGS_CLASS = "com.baidu.tbadk.TbadkSettings"
    private const val HOST_TBADK_CORE_APPLICATION_CLASS = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val HOST_SHARED_PREF_HELPER_CLASS = "com.baidu.tbadk.core.sharedPref.SharedPrefHelper"
    private const val HOST_SKIN_MANAGER_CLASS = "com.baidu.tbadk.core.util.SkinManager"
    private const val HOST_BITMAP_HELPER_CLASS = "com.baidu.tbadk.core.util.BitmapHelper"

    @Volatile private var targets: HomeNativeGlassHostDarkModeSwitchTargets? = null
    @Volatile private var activityRef: WeakReference<Activity>? = null
    @Volatile private var controllerRef: WeakReference<Any>? = null
    @Volatile private var switchRef: WeakReference<Any>? = null
    @Volatile private var lastKnownDarkMode: Boolean? = null
    @Volatile private var hostSkinApi: HostSkinApi? = null
    @Volatile private var hostSkinApiResolveAttempted: Boolean = false

    private val firstCacheErrorLogged = AtomicBoolean(false)
    private val firstSwitchErrorLogged = AtomicBoolean(false)
    private val firstHostSkinApiErrorLogged = AtomicBoolean(false)
    private val darkModeChangeListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun configure(targets: HomeNativeGlassHostDarkModeSwitchTargets) {
        this.targets = targets
        hostSkinApi = null
        hostSkinApiResolveAttempted = false
        firstHostSkinApiErrorLogged.set(false)
    }

    fun cacheFromActivity(activity: Any?) {
        val resolved = targets ?: return
        val hostActivity = activity as? Activity ?: return
        if (!resolved.moreActivityClass.isInstance(hostActivity)) return
        try {
            val controller = resolved.controllerField.get(hostActivity) ?: return
            cacheResolved(resolved, hostActivity, controller)
        } catch (t: Throwable) {
            logCacheErrorOnce(t)
        }
    }

    fun cacheFromController(controller: Any?) {
        val resolved = targets ?: return
        if (controller == null || !resolved.controllerField.type.isInstance(controller)) return
        try {
            cacheResolved(resolved, activity = null, controller = controller)
        } catch (t: Throwable) {
            logCacheErrorOnce(t)
        }
    }

    fun cacheFromHostCallback(activity: Any?, switchView: Any?, state: Any?) {
        val resolved = targets ?: return
        val hostActivity = activity as? Activity ?: return
        if (!resolved.moreActivityClass.isInstance(hostActivity) || switchView == null) return
        try {
            val controller = resolved.controllerField.get(hostActivity) ?: return
            val expectedSwitch = resolved.switchGetterMethod.invoke(controller) ?: return
            if (expectedSwitch !== switchView) return
            cacheResolved(
                resolved = resolved,
                activity = hostActivity,
                controller = controller,
                knownSwitch = expectedSwitch,
            )
            stateToDarkMode(state)?.let(::rememberDarkMode)
        } catch (t: Throwable) {
            logCacheErrorOnce(t)
        }
    }

    fun isAvailable(): Boolean {
        val resolved = targets ?: return false
        return currentSwitch(resolved) != null || resolveHostSkinApi(resolved) != null
    }

    fun addDarkModeChangeListener(listener: (Boolean) -> Unit) {
        if (!darkModeChangeListeners.contains(listener)) {
            darkModeChangeListeners.add(listener)
        }
    }

    fun currentKnownDarkMode(): Boolean? = lastKnownDarkMode

    fun onHostSkinTypeChanged(skinType: Int?) {
        rememberDarkMode(skinTypeToDarkMode(skinType))
    }

    fun isDarkModeEnabled(): Boolean? {
        val resolved = targets ?: return rememberDarkMode(lastKnownDarkMode)
        readHostDarkModeEnabled(resolved)?.let { enabled ->
            return rememberDarkMode(enabled)
        }
        val switchView = currentSwitch(resolved) ?: return rememberDarkMode(lastKnownDarkMode)
        return try {
            readDarkModeEnabled(resolved, switchView)?.let(::rememberDarkMode)
                ?: rememberDarkMode(lastKnownDarkMode)
        } catch (t: Throwable) {
            logSwitchErrorOnce(t)
            rememberDarkMode(lastKnownDarkMode)
        }
    }

    fun setDarkModeEnabled(enabled: Boolean): Boolean {
        val resolved = targets ?: return false
        val switchView = currentSwitch(resolved)
        if (switchView != null && setViaHostSwitch(resolved, switchView, enabled)) {
            return true
        }
        return setViaHostSkinApi(resolved, enabled)
    }

    private fun setViaHostSwitch(
        resolved: HomeNativeGlassHostDarkModeSwitchTargets,
        switchView: Any,
        enabled: Boolean,
    ): Boolean {
        return try {
            val current = readHostDarkModeEnabled(resolved)
                ?: readDarkModeEnabled(resolved, switchView)
            if (current != enabled) {
                val method = if (enabled) resolved.switchSetOnMethod else resolved.switchSetOffMethod
                method.invoke(switchView)
            }
            rememberDarkMode(
                readHostDarkModeEnabled(resolved)
                ?: readDarkModeEnabled(resolved, switchView)
                    ?: enabled
            )
            lastKnownDarkMode == enabled
        } catch (t: Throwable) {
            logSwitchErrorOnce(t)
            false
        }
    }

    private fun setViaHostSkinApi(
        resolved: HomeNativeGlassHostDarkModeSwitchTargets,
        enabled: Boolean,
    ): Boolean {
        val api = resolveHostSkinApi(resolved) ?: return false
        return try {
            api.setFollowSystemMode(false)
            if (enabled) {
                api.setSkinType(DARK_SKIN_TYPE)
            } else {
                api.setDayOrDarkSkinTypeWithSystemMode(showAnimation = true, fromSystem = false)
                if (api.getSkinType() != LIGHT_SKIN_TYPE) {
                    api.setSkinType(LIGHT_SKIN_TYPE)
                }
            }
            api.clearBitmapCache()
            rememberDarkMode(skinTypeToDarkMode(api.getSkinType()))
            XposedCompat.logD {
                "[HomeNativeGlassHostDarkModeBridge] host skin api applied: " +
                    "enabled=$enabled, skinType=${api.getSkinType()}"
            }
            lastKnownDarkMode == enabled
        } catch (t: Throwable) {
            logSwitchErrorOnce(t)
            false
        }
    }

    private fun currentSwitch(resolved: HomeNativeGlassHostDarkModeSwitchTargets): Any? {
        switchRef?.get()?.let { return it }
        val controller = controllerRef?.get()
            ?: activityRef?.get()?.let { activity ->
                try {
                    resolved.controllerField.get(activity)
                } catch (t: Throwable) {
                    logCacheErrorOnce(t)
                    null
                }
            }
            ?: return null
        return try {
            resolved.switchGetterMethod.invoke(controller)?.also { switchView ->
                controllerRef = WeakReference(controller)
                switchRef = WeakReference(switchView)
            }
        } catch (t: Throwable) {
            logCacheErrorOnce(t)
            null
        }
    }

    private fun cacheResolved(
        resolved: HomeNativeGlassHostDarkModeSwitchTargets,
        activity: Activity?,
        controller: Any,
        knownSwitch: Any? = null,
    ) {
        val switchView = knownSwitch ?: resolved.switchGetterMethod.invoke(controller) ?: return
        if (activity != null) {
            activityRef = WeakReference(activity)
        }
        controllerRef = WeakReference(controller)
        switchRef = WeakReference(switchView)
        readDarkModeEnabled(resolved, switchView)?.let(::rememberDarkMode)
    }

    private fun rememberDarkMode(enabled: Boolean?): Boolean? {
        if (enabled == null) return lastKnownDarkMode
        val previous = lastKnownDarkMode
        lastKnownDarkMode = enabled
        val changed = ConfigManager.setHomeNativeGlassDarkModeActive(enabled) || previous != enabled
        if (changed) {
            darkModeChangeListeners.forEach { listener ->
                runCatching { listener(enabled) }
            }
        }
        return enabled
    }

    private fun readDarkModeEnabled(
        resolved: HomeNativeGlassHostDarkModeSwitchTargets,
        switchView: Any,
    ): Boolean? {
        val state = resolved.switchStateField.get(switchView) ?: return null
        return when ((state as? Enum<*>)?.name ?: state.toString()) {
            "ON" -> true
            "OFF" -> false
            else -> null
        }
    }

    private fun stateToDarkMode(state: Any?): Boolean? {
        if (state == null) return null
        return when ((state as? Enum<*>)?.name ?: state.toString()) {
            "ON" -> true
            "OFF" -> false
            else -> null
        }
    }

    private fun readHostDarkModeEnabled(resolved: HomeNativeGlassHostDarkModeSwitchTargets): Boolean? {
        return try {
            val api = resolveHostSkinApi(resolved) ?: return null
            val skinType = api.getSkinType()
            val runningDarkMode = skinTypeToDarkMode(skinType)
            runningDarkMode ?: readHostStartupDarkMode(api)
        } catch (t: Throwable) {
            logSwitchErrorOnce(t)
            null
        }
    }

    private fun readHostStartupDarkMode(api: HostSkinApi): Boolean? {
        val followSystem = api.getFollowSystemMode() ?: return null
        if (followSystem) {
            return api.isCurrentSystemDarkMode()
        }
        return skinTypeToDarkMode(api.loadSkinType())
    }

    private fun skinTypeToDarkMode(skinType: Int?): Boolean? {
        return when (skinType) {
            null -> null
            LEGACY_DARK_SKIN_TYPE, DARK_SKIN_TYPE -> true
            LIGHT_SKIN_TYPE -> false
            else -> false
        }
    }

    private fun resolveHostSkinApi(resolved: HomeNativeGlassHostDarkModeSwitchTargets): HostSkinApi? {
        hostSkinApi?.let { return it }
        if (hostSkinApiResolveAttempted) return null
        synchronized(this) {
            hostSkinApi?.let { return it }
            if (hostSkinApiResolveAttempted) return null
            hostSkinApiResolveAttempted = true
            return try {
                val cl = resolved.moreActivityClass.classLoader
                    ?: error("host classloader unavailable")
                val settingsClass = XposedCompat.findClassOrNull(HOST_TBADK_SETTINGS_CLASS, cl)
                val appClass = XposedCompat.findClassOrNull(HOST_TBADK_CORE_APPLICATION_CLASS, cl)
                    ?: error("class missing: $HOST_TBADK_CORE_APPLICATION_CLASS")
                val sharedPrefClass = XposedCompat.findClassOrNull(HOST_SHARED_PREF_HELPER_CLASS, cl)
                    ?: error("class missing: $HOST_SHARED_PREF_HELPER_CLASS")
                val skinManagerClass = XposedCompat.findClassOrNull(HOST_SKIN_MANAGER_CLASS, cl)
                    ?: error("class missing: $HOST_SKIN_MANAGER_CLASS")
                val bitmapHelperClass = XposedCompat.findClassOrNull(HOST_BITMAP_HELPER_CLASS, cl)
                    ?: error("class missing: $HOST_BITMAP_HELPER_CLASS")

                val appInstance = appClass.staticNoArgMethod("getInst").invoke(null)
                    ?: error("TbadkCoreApplication.getInst() returned null")
                val settingsInstance = settingsClass?.staticNoArgMethod("getInst")?.invoke(null)
                val sharedPrefInstance = sharedPrefClass.staticNoArgMethod("getInstance").invoke(null)
                    ?: error("SharedPrefHelper.getInstance() returned null")
                HostSkinApi(
                    appInstance = appInstance,
                    getSkinTypeMethod = appClass.instanceNoArgMethod("getSkinType"),
                    setSkinTypeMethod = appClass.instanceIntMethod("setSkinType"),
                    settingsInstance = settingsInstance,
                    loadIntMethod = settingsClass?.instanceStringIntMethod("loadInt"),
                    sharedPrefInstance = sharedPrefInstance,
                    getBooleanMethod = sharedPrefClass.instanceStringBooleanMethod("getBoolean"),
                    putBooleanMethod = sharedPrefClass.instanceStringBooleanMethod("putBoolean"),
                    isCurrentSystemDarkModeMethod = skinManagerClass.staticNoArgMethod("isCurrentSystemDarkMode"),
                    setDayOrDarkSkinTypeWithSystemModeMethod =
                        skinManagerClass.staticBooleanBooleanMethod("setDayOrDarkSkinTypeWithSystemMode"),
                    clearBitmapCacheMethod = bitmapHelperClass.staticNoArgMethod("clearCashBitmap"),
                ).also {
                    hostSkinApi = it
                    XposedCompat.logD("[HomeNativeGlassHostDarkModeBridge] host skin api READY")
                }
            } catch (t: Throwable) {
                logHostSkinApiErrorOnce(t)
                null
            }
        }
    }

    private fun Class<*>.staticNoArgMethod(name: String): Method {
        return getDeclaredMethod(name).apply { isAccessible = true }
    }

    private fun Class<*>.instanceNoArgMethod(name: String): Method {
        return getDeclaredMethod(name).apply { isAccessible = true }
    }

    private fun Class<*>.instanceIntMethod(name: String): Method {
        return getDeclaredMethod(name, Int::class.javaPrimitiveType).apply { isAccessible = true }
    }

    private fun Class<*>.instanceStringIntMethod(name: String): Method {
        return getDeclaredMethod(
            name,
            String::class.java,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
    }

    private fun Class<*>.instanceStringBooleanMethod(name: String): Method {
        return getDeclaredMethod(
            name,
            String::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
    }

    private fun Class<*>.staticBooleanBooleanMethod(name: String): Method {
        return getDeclaredMethod(
            name,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
    }

    private fun logCacheErrorOnce(t: Throwable) {
        if (firstCacheErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logW("[HomeNativeGlassHostDarkModeBridge] cache failed: ${t.message}")
        }
    }

    private fun logSwitchErrorOnce(t: Throwable) {
        if (firstSwitchErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logW("[HomeNativeGlassHostDarkModeBridge] switch failed: ${t.message}")
        }
    }

    private fun logHostSkinApiErrorOnce(t: Throwable) {
        if (firstHostSkinApiErrorLogged.compareAndSet(false, true)) {
            XposedCompat.logW("[HomeNativeGlassHostDarkModeBridge] host skin api unavailable: ${t.message}")
        }
    }

    private class HostSkinApi(
        private val appInstance: Any,
        private val getSkinTypeMethod: Method,
        private val setSkinTypeMethod: Method,
        private val settingsInstance: Any?,
        private val loadIntMethod: Method?,
        private val sharedPrefInstance: Any,
        private val getBooleanMethod: Method,
        private val putBooleanMethod: Method,
        private val isCurrentSystemDarkModeMethod: Method,
        private val setDayOrDarkSkinTypeWithSystemModeMethod: Method,
        private val clearBitmapCacheMethod: Method,
    ) {
        fun getSkinType(): Int? {
            return getSkinTypeMethod.invoke(appInstance) as? Int
        }

        fun loadSkinType(): Int? {
            val settings = settingsInstance ?: return null
            val method = loadIntMethod ?: return null
            return method.invoke(settings, HOST_SKIN_TYPE_PREF_KEY, LIGHT_SKIN_TYPE) as? Int
        }

        fun getFollowSystemMode(): Boolean? {
            return getBooleanMethod.invoke(sharedPrefInstance, FOLLOW_SYSTEM_MODE_PREF_KEY, false) as? Boolean
        }

        fun isCurrentSystemDarkMode(): Boolean? {
            return isCurrentSystemDarkModeMethod.invoke(null) as? Boolean
        }

        fun setSkinType(skinType: Int) {
            setSkinTypeMethod.invoke(appInstance, skinType)
        }

        fun setFollowSystemMode(enabled: Boolean) {
            putBooleanMethod.invoke(sharedPrefInstance, FOLLOW_SYSTEM_MODE_PREF_KEY, enabled)
        }

        fun setDayOrDarkSkinTypeWithSystemMode(showAnimation: Boolean, fromSystem: Boolean) {
            setDayOrDarkSkinTypeWithSystemModeMethod.invoke(null, showAnimation, fromSystem)
        }

        fun clearBitmapCache() {
            clearBitmapCacheMethod.invoke(null)
        }
    }
}
