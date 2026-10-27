package com.forbidad4tieba.hook.testing

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

/** Hook registration only. Each test retains its own strict callback Chain. */
internal class FakeHookApi(
    failing: Set<Executable> = emptySet(),
    private val allowLog: Boolean = false,
) {
    val callbacks = LinkedHashMap<Executable, XposedInterface.Hooker>()
    val snapshots = ArrayList<XposedInterface.Hooker>()
    val failInstall = HashSet(failing)
    val failUnhook = HashSet<Executable>()
    val exceptionModes = LinkedHashMap<Executable, XposedInterface.ExceptionMode>()
    var afterInstall: () -> Unit = {}
    var created = 0
        private set

    val value = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, method, args ->
        when {
            method.name == "hook" -> builder(args!![0] as Executable)
            method.name == "log" && allowLog -> null
            else -> error("Unexpected API call: ${method.name}")
        }
    } as XposedInterface

    private fun builder(member: Executable) = object : XposedInterface.HookBuilder {
        override fun setId(id: String?) = this
        override fun setPriority(priority: Int) = this
        override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = apply { exceptionModes[member] = mode }
        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            check(member !in failInstall) { "fixture installation failure" }
            callbacks[member] = hooker
            snapshots += hooker
            created++
            afterInstall()
            return object : XposedInterface.HookHandle {
                override fun getExecutable() = member
                override fun getId(): String? = null
                override fun unhook() {
                    check(member !in failUnhook) { "fixture unhook failure" }
                    callbacks.remove(member)
                }
                override fun replaceHook(hooker: XposedInterface.Hooker): XposedInterface.HookHandle = error("unused")
            }
        }
    }
}
