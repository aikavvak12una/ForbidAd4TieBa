package com.forbidad4tieba.hook.feature.diagnostic

import android.util.Log
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.DetailedLogSession
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object TiebaHostLogHook {
    private const val TAG = "[TiebaHostLogHook]"
    private const val DEFAULT_SPACE = "default"
    private const val MAX_SPACE_CHARACTERS = 128
    private const val MAX_LOG_ID_CHARACTERS = 256
    private const val MAX_TAG_CHARACTERS = 256
    private const val MAX_MESSAGE_CHARACTERS = 15_000
    private const val TRUNCATED_SUFFIX = "...[truncated]"

    @Volatile private var hooked = false
    private val callbackErrorLogged = AtomicBoolean(false)

    fun hook(classLoader: ClassLoader) {
        if (!tryMarkHooked()) return
        val handles = ArrayList<XposedInterface.HookHandle>(3)
        try {
            val managerClass = requireNotNull(
                XposedCompat.findClassOrNull(StableTiebaHookPoints.TB_LOG_MANAGER_CLASS, classLoader),
            ) {
                "class missing: ${StableTiebaHookPoints.TB_LOG_MANAGER_CLASS}"
            }
            val levelClass = requireNotNull(
                XposedCompat.findClassOrNull(
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

            handles += requireNotNull(
                XposedCompat.interceptHook("$TAG.log", logMethod) { chain ->
                    capture {
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
            handles += requireNotNull(
                XposedCompat.interceptHook("$TAG.logI", logInfoMethod) { chain ->
                    capture {
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
            handles += requireNotNull(
                XposedCompat.interceptHook("$TAG.logE", logErrorMethod) { chain ->
                    capture {
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
            XposedCompat.log(
                "$TAG hooks INSTALLED: ${managerClass.name}.log/logI/logE",
            )
        } catch (t: Throwable) {
            handles.forEach { handle ->
                runCatching { handle.unhook() }
            }
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun requireStaticVoidMethod(
        owner: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method {
        val method = XposedCompat.findMethodOrNull(owner, name, *parameterTypes)
            ?: error("method missing: ${owner.name}.$name")
        require(Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers)) {
            "method is not public static: ${owner.name}.$name"
        }
        require(method.returnType == Void.TYPE) {
            "method does not return void: ${owner.name}.$name"
        }
        return method
    }

    private inline fun capture(block: () -> Unit) {
        if (!ConfigManager.shouldOutputDetailedLogs()) return
        try {
            block()
        } catch (t: Throwable) {
            if (callbackErrorLogged.compareAndSet(false, true)) {
                XposedCompat.logW("$TAG malformed log entry rejected: ${t.message}")
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
        val boundedSpace = truncate(space, MAX_SPACE_CHARACTERS)
        val boundedLogId = truncate(logId, MAX_LOG_ID_CHARACTERS)
        val boundedTag = truncate(tag, MAX_TAG_CHARACTERS)
        val boundedMessage = truncate(message, MAX_MESSAGE_CHARACTERS)
        val detail = "space=$boundedSpace logId=$boundedLogId message=$boundedMessage"
        val recorded = DetailedLogSession.recordTieba(
            level = level,
            tag = boundedTag,
            message = detail,
        )
        if (!recorded) return
        XposedCompat.emitTiebaHostLog(
            priority = priorityFor(level),
            tag = boundedTag,
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

    private fun truncate(value: String, maxCharacters: Int): String {
        if (value.length <= maxCharacters) return value
        return value.take(maxCharacters - TRUNCATED_SUFFIX.length) + TRUNCATED_SUFFIX
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) {
            hooked = false
        }
    }
}
