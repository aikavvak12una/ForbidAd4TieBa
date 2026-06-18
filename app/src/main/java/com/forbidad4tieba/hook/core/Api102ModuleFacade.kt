package com.forbidad4tieba.hook.core

import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.io.FileNotFoundException
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

class Api102ModuleFacade(private val base: XposedModule) {
    val apiVersion: Int get() = base.apiVersion
    val frameworkName: String get() = base.frameworkName
    val frameworkVersion: String get() = base.frameworkVersion
    val frameworkVersionCode: Long get() = base.frameworkVersionCode
    val frameworkProperties: Long get() = base.frameworkProperties
    val moduleApplicationInfo: ApplicationInfo get() = base.moduleApplicationInfo

    fun hook(origin: Executable): XposedInterface.HookBuilder {
        return RegisteredHookBuilder(
            delegate = base.hook(origin),
            executable = origin,
        )
    }

    fun hookClassInitializer(origin: Class<*>): XposedInterface.HookBuilder {
        return base.hookClassInitializer(origin)
    }

    fun deoptimize(executable: Executable): Boolean = base.deoptimize(executable)

    fun getInvoker(method: Method): XposedInterface.Invoker<*, Method> = base.getInvoker(method)

    fun <T> getInvoker(constructor: Constructor<T>): XposedInterface.CtorInvoker<T> {
        return base.getInvoker(constructor)
    }

    fun log(priority: Int, tag: String?, msg: String) {
        base.log(priority, tag, msg)
    }

    fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) {
        base.log(priority, tag, msg, tr)
    }

    fun listRemoteFiles(): Array<String> = base.listRemoteFiles()

    @Throws(FileNotFoundException::class)
    fun openRemoteFile(name: String): ParcelFileDescriptor = base.openRemoteFile(name)

    private class RegisteredHookBuilder(
        private val delegate: XposedInterface.HookBuilder,
        private val executable: Executable,
    ) : XposedInterface.HookBuilder {
        private var explicitIdSet = false
        private var explicitId: String? = null

        override fun setPriority(priority: Int): XposedInterface.HookBuilder {
            delegate.setPriority(priority)
            return this
        }

        override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder {
            delegate.setExceptionMode(mode)
            return this
        }

        override fun setId(id: String?): XposedInterface.HookBuilder {
            explicitIdSet = true
            explicitId = id
            delegate.setId(id)
            return this
        }

        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            val id = if (explicitIdSet) {
                explicitId
            } else {
                Api102HookRegistry.nextHookId(
                    featureId = Api102HookRegistry.currentFeatureId("direct"),
                    executable = executable,
                )
            }
            if (!explicitIdSet) {
                delegate.setId(id)
            }
            if (id != null) {
                Api102HookRegistry.replaceOldHookIfPresent(id, hooker)?.let { return it }
            }
            val handle = delegate.intercept(hooker)
            if (id != null) {
                Api102HookRegistry.register(id, handle)
            }
            return handle
        }
    }
}
