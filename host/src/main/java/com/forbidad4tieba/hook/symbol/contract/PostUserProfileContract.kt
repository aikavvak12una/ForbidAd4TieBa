package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The same stable profile message used by Tieba's own profile entry points. */
object PostUserProfileContract {
    fun resolve(classLoader: ClassLoader): PostUserProfileTargets {
        fun type(name: String) = Class.forName(name, false, classLoader)
        val profile = type(StableTiebaHookPoints.PERSON_POLYMERIC_ACTIVITY_CONFIG_CLASS)
        val manager = type(StableTiebaHookPoints.MESSAGE_MANAGER_CLASS)
        val message = type(StableTiebaHookPoints.MESSAGE_CLASS)
        val customMessage = type(StableTiebaHookPoints.CUSTOM_MESSAGE_CLASS)
        val application = type(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS)
        val create = profile.getMethod("createNormalConfig", Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply {
            check(!Modifier.isStatic(modifiers) && returnType == profile) { "Invalid profile factory" }
            isAccessible = true
        }
        val instance = manager.getMethod("getInstance").apply {
            check(Modifier.isStatic(modifiers) && returnType == manager) { "Invalid MessageManager instance" }
            isAccessible = true
        }
        val send = manager.getMethod("sendMessage", message).apply {
            check(!Modifier.isStatic(modifiers) && returnType == Boolean::class.javaPrimitiveType) { "Invalid message sender" }
            isAccessible = true
        }
        val account = application.getMethod("getCurrentAccount").apply {
            check(Modifier.isStatic(modifiers) && returnType == String::class.java) { "Invalid current account API" }
            isAccessible = true
        }
        check(message.isAssignableFrom(customMessage)) { "Invalid profile message type" }
        return PostUserProfileTargets(
            profile.getConstructor(Context::class.java).apply { isAccessible = true }, create,
            customMessage.getConstructor(Int::class.javaPrimitiveType, Any::class.java).apply { isAccessible = true },
            instance, send, account,
        )
    }
}

class PostUserProfileTargets internal constructor(
    private val profile: Constructor<*>,
    private val create: Method,
    private val message: Constructor<*>,
    private val instance: Method,
    private val send: Method,
    private val account: Method,
) {
    fun open(context: Context, userId: String): Boolean {
        val uid = userId.toLongOrNull()?.takeIf { it > 0 } ?: return false
        val config = profile.newInstance(context)
        create.invoke(config, uid, account.invoke(null) == userId, false)
        val manager = instance.invoke(null) ?: return false
        // CustomMessage dispatch does not acknowledge activity startup through its boolean result.
        send.invoke(manager, message.newInstance(2002001, config))
        return true
    }
}
