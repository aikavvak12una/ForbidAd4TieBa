package com.forbidad4tieba.hook.feature.diagnostic

import com.forbidad4tieba.hook.contracts.MemberAccess
import android.os.SystemClock
import android.util.Log
import com.forbidad4tieba.hook.InstallOutcome
import com.forbidad4tieba.hook.InstallState
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.DetailedLogSession
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object TiebaHostLogHook {
    private const val TAG = "[TiebaHostLogHook]"
    private const val DEFAULT_SPACE = "default"
    private const val MAX_LEVEL_CHARACTERS = 32
    private const val MAX_SPACE_CHARACTERS = 128
    private const val MAX_LOG_ID_CHARACTERS = 256
    private const val MAX_TAG_CHARACTERS = 256
    private const val MAX_MESSAGE_CHARACTERS = 15_000
    private const val DETAIL_PREFIX_CHARACTERS = 22
    private const val SUMMARY_CHARACTER_RESERVE = 128

    private val hooks = OwnedHookSet<Method, XposedInterface.HookHandle> { it.unhook() }
    private val regularBudget = HostLogRateBudget(maxEntries = 32, maxCharacters = 48 * 1024)
    private val priorityBudget = HostLogRateBudget(maxEntries = 8, maxCharacters = 16 * 1024)
    @Volatile private var activeInstallation: Any? = null
    private val callbackErrorLogged = AtomicBoolean(false)

    @Synchronized
    fun hook(classLoader: ClassLoader): InstallOutcome {
        if (activeInstallation != null) return InstallOutcome(InstallState.ALREADY_INSTALLED, hooks.size())
        if (hooks.size() != 0) {
            val cleanup = rollback("retrying incomplete host log installation")
            if (cleanup.installedCount != 0) return cleanup
        }
        if (XposedCompat.module == null) return InstallOutcome.skipped("module unavailable")
        // Old framework snapshots must not become active when a later attempt succeeds.
        val installation = Any()
        try {
            val managerClass = requireNotNull(
                MemberAccess.findClassOrNull(StableTiebaHookPoints.TB_LOG_MANAGER_CLASS, classLoader),
            ) {
                "class missing: ${StableTiebaHookPoints.TB_LOG_MANAGER_CLASS}"
            }
            val levelClass = requireNotNull(
                MemberAccess.findClassOrNull(
                    StableTiebaHookPoints.TB_LOG_MANAGER_LEVEL_CLASS,
                    classLoader,
                ),
            ) {
                "class missing: ${StableTiebaHookPoints.TB_LOG_MANAGER_LEVEL_CLASS}"
            }
            require(levelClass.isEnum) {
                "invalid level type: ${levelClass.name}"
            }

            val logMethod = requireStaticVoidMethod(
                managerClass,
                StableTiebaHookPoints.METHOD_LOG,
                String::class.java,
                levelClass,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            val logInfoMethod = requireStaticVoidMethod(
                managerClass,
                StableTiebaHookPoints.METHOD_LOG_INFO,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            val logErrorMethod = requireStaticVoidMethod(
                managerClass,
                StableTiebaHookPoints.METHOD_LOG_ERROR,
                String::class.java,
                String::class.java,
                String::class.java,
            )

            hooks.install(logMethod) {
                requireNotNull(
                    XposedCompat.interceptHook("$TAG.log", logMethod) { chain ->
                        capture(installation) {
                            val args = chain.args
                            record(
                                level = (args[1] as? Enum<*>)?.name ?: args[1].toString(),
                                space = args[0] as String,
                                logId = args[2] as String,
                                tag = args[3] as String,
                                message = args[4] as String,
                            )
                        }
                        chain.proceed()
                    },
                ) {
                    "hook unavailable: ${logMethod.name}"
                }
            }
            hooks.install(logInfoMethod) {
                requireNotNull(
                    XposedCompat.interceptHook("$TAG.logI", logInfoMethod) { chain ->
                        capture(installation) {
                            val args = chain.args
                            record(
                                level = "INFO",
                                space = DEFAULT_SPACE,
                                logId = args[0] as String,
                                tag = args[1] as String,
                                message = args[2] as String,
                            )
                        }
                        chain.proceed()
                    },
                ) {
                    "hook unavailable: ${logInfoMethod.name}"
                }
            }
            hooks.install(logErrorMethod) {
                requireNotNull(
                    XposedCompat.interceptHook("$TAG.logE", logErrorMethod) { chain ->
                        capture(installation) {
                            val args = chain.args
                            record(
                                level = "ERROR",
                                space = DEFAULT_SPACE,
                                logId = args[0] as String,
                                tag = args[1] as String,
                                message = args[2] as String,
                            )
                        }
                        chain.proceed()
                    },
                ) {
                    "hook unavailable: ${logErrorMethod.name}"
                }
            }
            activeInstallation = installation
            XposedCompat.log(
                "$TAG hooks INSTALLED: ${managerClass.name}.log/logI/logE",
            )
            return InstallOutcome(InstallState.INSTALLED, hooks.size())
        } catch (t: Throwable) {
            val reason = t.message ?: t.javaClass.simpleName
            val outcome = rollback(reason)
            XposedCompat.log("$TAG install FAILED: $reason")
            return outcome
        }
    }

    private fun requireStaticVoidMethod(
        owner: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method {
        val method = MemberAccess.findMethodOrNull(owner, name, *parameterTypes)
            ?: error("method missing: ${owner.name}.$name")
        require(Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers)) {
            "method is not public static: ${owner.name}.$name"
        }
        require(method.returnType == Void.TYPE) {
            "method does not return void: ${owner.name}.$name"
        }
        return method
    }

    private inline fun capture(installation: Any, block: () -> Unit) {
        if (activeInstallation !== installation || !ConfigManager.snapshot().isDetailedLoggingEnabled) return
        try {
            block()
        } catch (t: Throwable) {
            if (callbackErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logW("$TAG malformed log entry rejected: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun record(
        level: String,
        space: String,
        logId: String,
        tag: String,
        message: String,
    ) {
        val characterCount = HostLogContent.boundedCharacterCount(level, MAX_LEVEL_CHARACTERS) +
            HostLogContent.boundedCharacterCount(space, MAX_SPACE_CHARACTERS) +
            HostLogContent.boundedCharacterCount(logId, MAX_LOG_ID_CHARACTERS) +
            HostLogContent.boundedCharacterCount(tag, MAX_TAG_CHARACTERS) +
            HostLogContent.boundedCharacterCount(message, MAX_MESSAGE_CHARACTERS) +
            DETAIL_PREFIX_CHARACTERS + SUMMARY_CHARACTER_RESERVE
        val priority = level == "WARN" || level == "ERROR" || level == "ASSERT"
        val budget = if (priority) priorityBudget else regularBudget
        val suppressed = budget.acquire(characterCount, SystemClock.elapsedRealtime())
        if (suppressed < 0) return
        if (suppressed > 0) {
            val group = if (priority) "priority" else "regular"
            emit("WARN", TAG, "suppressed=$suppressed host log entries; budget=$group")
        }
        val boundedLevel = HostLogContent.sanitize(level, MAX_LEVEL_CHARACTERS)
        val boundedSpace = HostLogContent.sanitize(space, MAX_SPACE_CHARACTERS)
        val boundedLogId = HostLogContent.sanitize(logId, MAX_LOG_ID_CHARACTERS)
        val boundedTag = HostLogContent.sanitize(tag, MAX_TAG_CHARACTERS)
        val boundedMessage = HostLogContent.sanitize(message, MAX_MESSAGE_CHARACTERS)
        val detail = "space=$boundedSpace logId=$boundedLogId message=$boundedMessage"
        emit(boundedLevel, boundedTag, detail)
    }

    private fun emit(level: String, tag: String, detail: String) {
        val recorded = DetailedLogSession.recordTieba(
            level = level,
            tag = tag,
            message = detail,
        )
        if (!recorded) return
        XposedCompat.emitTiebaHostLog(
            priority = priorityFor(level),
            tag = tag,
            message = detail,
        )
    }

    private fun priorityFor(level: String): Int {
        return when (level) {
            "VERBOSE" -> Log.VERBOSE
            "DEBUG" -> Log.DEBUG
            "WARN" -> Log.WARN
            "ERROR" -> Log.ERROR
            else -> Log.INFO
        }
    }

    private fun rollback(reason: String): InstallOutcome {
        // Surviving callbacks must only proceed while their handles await a successful release.
        activeInstallation = null
        val hadHooks = hooks.size() != 0
        val failures = hooks.rollback()
        for ((method, failure) in failures) {
            XposedCompat.logW("$TAG rollback failed: ${method.name}: ${failure.message}")
        }
        val state = when {
            failures.isNotEmpty() -> InstallState.ROLLBACK_FAILED
            hadHooks -> InstallState.ROLLED_BACK
            else -> InstallState.FAILED
        }
        return InstallOutcome(state, hooks.size(), reason)
    }
}
