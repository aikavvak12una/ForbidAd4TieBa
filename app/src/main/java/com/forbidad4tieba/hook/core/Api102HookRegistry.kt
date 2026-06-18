package com.forbidad4tieba.hook.core

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object Api102HookRegistry {
    data class InstallScope(
        val processName: String,
        val phase: String,
        val entryId: String,
    ) {
        fun featureId(): String = "$phase:$entryId"
    }

    data class RegisteredHook(
        val id: String,
        val executable: String,
    )

    data class ReplacementSummary(
        val oldTotal: Int,
        val oldWithId: Int,
        val replaced: Int,
        val unhookedStale: Int,
        val errors: List<String>,
    )

    private val installScope = ThreadLocal<InstallScope?>()
    private val hooks = ConcurrentHashMap<String, HookRecord>()
    private val hookOrdinalMap = ConcurrentHashMap<String, AtomicInteger>()
    private val replacementLock = Any()
    private var replacementSession: ReplacementSession? = null

    private data class HookRecord(
        val id: String,
        val executable: String,
        val handle: XposedInterface.HookHandle,
    )

    private data class ReplacementSession(
        val oldTotal: Int,
        val oldWithId: Int,
        val oldById: LinkedHashMap<String, XposedInterface.HookHandle>,
        val staleHandles: ArrayList<XposedInterface.HookHandle>,
        var replaced: Int = 0,
        val errors: ArrayList<String> = ArrayList(),
    )

    fun <T> withInstallScope(scope: InstallScope, block: () -> T): T {
        val previous = installScope.get()
        installScope.set(scope)
        return try {
            block()
        } finally {
            installScope.set(previous)
        }
    }

    fun currentFeatureId(fallback: String): String {
        return installScope.get()?.featureId() ?: fallback
    }

    fun hookId(featureId: String, executable: Executable): String {
        return hookId(featureId, executable, ordinal = null)
    }

    fun nextHookId(featureId: String, executable: Executable): String {
        val baseId = hookId(featureId, executable, ordinal = null)
        val ordinal = hookOrdinalMap
            .computeIfAbsent(baseId) { AtomicInteger(0) }
            .incrementAndGet()
        return hookId(featureId, executable, ordinal)
    }

    private fun hookId(featureId: String, executable: Executable, ordinal: Int?): String {
        val owner = executable.declaringClass.name
        val params = executable.parameterTypes.joinToString(",") { it.name }
        val suffix = ordinal?.let { ":$it" }.orEmpty()
        return "TbHook:$featureId:$owner#${executable.name}($params)$suffix"
    }

    fun register(id: String, handle: XposedInterface.HookHandle) {
        hooks[id] = HookRecord(
            id = id,
            executable = formatExecutable(handle.executable),
            handle = handle,
        )
    }

    fun beginHotReloadReplacement(oldHandles: List<XposedInterface.HookHandle>) {
        clear()
        val oldById = LinkedHashMap<String, XposedInterface.HookHandle>()
        val staleHandles = ArrayList<XposedInterface.HookHandle>()
        for (handle in oldHandles) {
            val id = runCatching { handle.id }.getOrNull()
            if (id.isNullOrBlank() || oldById.put(id, handle) != null) {
                staleHandles += handle
            }
        }
        synchronized(replacementLock) {
            replacementSession = ReplacementSession(
                oldTotal = oldHandles.size,
                oldWithId = oldById.size,
                oldById = oldById,
                staleHandles = staleHandles,
            )
        }
    }

    fun replaceOldHookIfPresent(
        id: String,
        hooker: XposedInterface.Hooker,
    ): XposedInterface.HookHandle? {
        val oldHandle = synchronized(replacementLock) {
            replacementSession?.oldById?.remove(id)
        } ?: return null

        return try {
            val newHandle = oldHandle.replaceHook(hooker)
            synchronized(replacementLock) {
                replacementSession?.replaced = (replacementSession?.replaced ?: 0) + 1
            }
            register(id, newHandle)
            newHandle
        } catch (t: Throwable) {
            synchronized(replacementLock) {
                replacementSession?.staleHandles?.add(oldHandle)
                replacementSession?.errors?.add("$id: ${t.javaClass.name}: ${t.message.orEmpty()}")
            }
            throw t
        }
    }

    fun finishHotReloadReplacement(): ReplacementSummary {
        val session = synchronized(replacementLock) {
            replacementSession.also { replacementSession = null }
        } ?: return ReplacementSummary(
            oldTotal = 0,
            oldWithId = 0,
            replaced = 0,
            unhookedStale = 0,
            errors = emptyList(),
        )

        val staleHandles = ArrayList<XposedInterface.HookHandle>(
            session.staleHandles.size + session.oldById.size,
        )
        staleHandles += session.staleHandles
        staleHandles += session.oldById.values

        var unhooked = 0
        val errors = ArrayList(session.errors)
        for (handle in staleHandles) {
            try {
                handle.unhook()
                unhooked++
            } catch (t: Throwable) {
                val id = runCatching { handle.id }.getOrNull().orEmpty()
                errors += "unhook $id: ${t.javaClass.name}: ${t.message.orEmpty()}"
            }
        }

        return ReplacementSummary(
            oldTotal = session.oldTotal,
            oldWithId = session.oldWithId,
            replaced = session.replaced,
            unhookedStale = unhooked,
            errors = errors,
        )
    }

    fun registeredHooks(): List<RegisteredHook> {
        return hooks.values
            .map { record -> RegisteredHook(record.id, record.executable) }
            .sortedBy { it.id }
    }

    fun registeredCount(): Int = hooks.size

    fun unhookAll(): Int {
        var count = 0
        for (record in hooks.values) {
            record.handle.unhook()
            count++
        }
        clear()
        return count
    }

    fun clear() {
        hooks.clear()
        hookOrdinalMap.clear()
        installScope.remove()
        synchronized(replacementLock) {
            replacementSession = null
        }
    }

    private fun formatExecutable(executable: Executable): String {
        val owner = executable.declaringClass.name
        val params = executable.parameterTypes.joinToString(",") { it.name }
        return "$owner#${executable.name}($params)"
    }
}
