package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Stable host APIs for a settings action. No obfuscated discovery, hooks, or persisted scan rules. */
object PostUserLookupContract {
    fun resolve(classLoader: ClassLoader): PostUserLookupTargets = validate(
        Class.forName(StableTiebaHookPoints.NETWORK_CLASS, false, classLoader),
    )

    internal fun validate(network: Class<*>): PostUserLookupTargets {
        check(!Modifier.isAbstract(network.modifiers)) { "NetWork is abstract" }
        fun method(name: String, returns: Class<*>, vararg parameters: Class<*>): Method =
            network.getMethod(name, *parameters).apply {
                check(!Modifier.isStatic(modifiers) && returnType == returns) { "Invalid NetWork.$name" }
                isAccessible = true
            }
        val booleanType = Boolean::class.javaPrimitiveType!!
        return PostUserLookupTargets(
            constructor = network.getConstructor(String::class.java).apply { isAccessible = true },
            add = method("addPostData", Void.TYPE, String::class.java, String::class.java),
            post = method("postNetData", String::class.java),
            cancel = method("setCancel", Void.TYPE),
            bduss = method("setNeedBdussForPost", Void.TYPE, booleanType),
            toast = method("setNeedShowSeverToast", Void.TYPE, booleanType),
            tbs = method("setNeedTbs", Void.TYPE, booleanType),
            signature = method("setNeedSig", Void.TYPE, booleanType),
        )
    }
}

class PostUserLookupTargets internal constructor(
    private val constructor: Constructor<*>,
    private val add: Method,
    private val post: Method,
    private val cancel: Method,
    private val bduss: Method,
    private val toast: Method,
    private val tbs: Method,
    private val signature: Method,
) {
    fun newCall(profileId: String): PostUserLookupCall {
        val request = constructor.newInstance("https://tiebac.baidu.com/c/u/user/getUserByTiebaUid")
        add.invoke(request, "tieba_uid", profileId)
        bduss.invoke(request, true)
        toast.invoke(request, false)
        tbs.invoke(request, true)
        signature.invoke(request, true)
        return PostUserLookupCall(request, post, cancel)
    }
}

class PostUserLookupCall internal constructor(
    private val request: Any,
    private val post: Method,
    private val cancel: Method,
) {
    fun execute(): String? = post.invoke(request) as? String
    fun cancel() { cancel.invoke(request) }
}
