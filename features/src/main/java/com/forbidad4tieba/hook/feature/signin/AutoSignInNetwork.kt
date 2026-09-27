package com.forbidad4tieba.hook.feature.signin



import com.forbidad4tieba.hook.symbol.contract.*

import android.content.Context
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.AutoSignInHybridNativeProxySymbols
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/** Existing host network paths; all obfuscated members still come from the symbol resolver. */
internal class AutoSignInNetwork private constructor(private val bridge: NativeNetworkBridge) : SignInGateway {
    private data class Reply(val json: JSONObject? = null, val failure: SignInFailure? = null)

    fun currentAccountId(): String = try {
        (bridge.getCurrentAccountMethod.invoke(null) as? String).orEmpty()
    } catch (t: Throwable) {
        XposedCompat.logW("[AutoSignIn] current account unavailable: ${t.javaClass.simpleName}")
        ""
    }

    override fun fetchForums(): SignInSnapshotResult {
        val hybrid = postHybrid(FORUM_LIST_PATH, linkedMapOf())
        if (hybrid.json != null) {
            val parsed = AutoSignInResponses.snapshot(hybrid.json, System.currentTimeMillis() / 1000L)
            if (parsed.snapshot != null || parsed.failure?.kind == SignInFailureKind.API) return parsed
        }
        val native = postNative(FORUM_LIST_PATH, linkedMapOf("user_id" to currentAccountId()), false, false)
        return native.json?.let { AutoSignInResponses.snapshot(it, System.currentTimeMillis() / 1000L) }
            ?: SignInSnapshotResult(failure = native.failure ?: hybrid.failure)
    }

    override fun signBatch(forums: List<SignInForum>): SignInBatchResult {
        val reply = postHybrid(MSIGN_PATH, linkedMapOf("forum_ids" to forums.joinToString(",") { it.id }))
        return reply.json?.let { AutoSignInResponses.batch(it, forums) }
            ?: SignInBatchResult(failure = reply.failure)
    }

    override fun signSingle(forum: SignInForum): SignInAttempt {
        val params = linkedMapOf("kw" to forum.name)
        if (forum.id.isNotEmpty()) params["fid"] = forum.id
        val reply = postNative(SIGN_PATH, params, true, true)
        return reply.json?.let(AutoSignInResponses::single) ?: SignInAttempt(false, reply.failure)
    }

    private fun postHybrid(path: String, params: LinkedHashMap<String, String>): Reply {
        val proxy = bridge.hybridNativeProxy
            ?: return Reply(failure = SignInFailure(SignInFailureKind.REQUEST_FAILED))
        return try {
            val task = proxy.taskConstructor.newInstance(
                buildUrl(HYBRID_API_HOST, path), "post", 1, 1, System.currentTimeMillis(), HashMap(params), null,
            )
            val rawTaskParams: Array<Any> = emptyArray()
            val result = proxy.doInBackgroundMethod.invoke(task, rawTaskParams as Any) as? Map<*, *>
            val raw = result?.get("result") as? String
            if (raw.isNullOrBlank()) {
                val code = result?.get("error_code")?.toString()?.takeIf { it.isNotBlank() && it != "0" }
                Reply(failure = if (code != null) SignInFailure(SignInFailureKind.API, code,
                    AutoSignInResponses.safeMessage(result["error_msg"]?.toString().orEmpty()))
                else SignInFailure(SignInFailureKind.NO_RESPONSE))
            } else parseReply(raw)
        } catch (t: Throwable) {
            transportFailure(t)
        }
    }

    private fun postNative(path: String, params: LinkedHashMap<String, String>, needTbs: Boolean, needSig: Boolean): Reply {
        return try {
            val address = (bridge.serverAddressField.get(null) as? String)?.takeIf { it.isNotBlank() }
                ?: return Reply(failure = SignInFailure(SignInFailureKind.REQUEST_FAILED))
            val net = bridge.netCtor.newInstance(buildUrl(address, path))
            params.forEach { (key, value) -> bridge.addPostDataMethod.invoke(net, key, value) }
            bridge.setNeedTbsMethod.invoke(net, needTbs)
            bridge.setNeedSigMethod.invoke(net, needSig)
            val raw = bridge.postNetDataMethod.invoke(net) as? String
            if (raw.isNullOrBlank()) Reply(failure = SignInFailure(SignInFailureKind.NO_RESPONSE))
            else parseReply(raw)
        } catch (t: Throwable) {
            transportFailure(t)
        }
    }

    private fun parseReply(raw: String): Reply = try {
        Reply(JSONObject(raw))
    } catch (_: Exception) {
        Reply(failure = SignInFailure(SignInFailureKind.INVALID_RESPONSE))
    }

    private fun transportFailure(thrown: Throwable): Reply {
        val cause = if (thrown is InvocationTargetException) thrown.targetException else thrown
        val kind = if (cause is SocketTimeoutException || cause is TimeoutException)
            SignInFailureKind.TIMEOUT else SignInFailureKind.REQUEST_FAILED
        XposedCompat.logW("[AutoSignIn] request failed: ${cause.javaClass.simpleName}")
        return Reply(failure = SignInFailure(kind))
    }

    private fun buildUrl(address: String, path: String) = address.trimEnd('/') + "/" + path.trimStart('/')

    private data class NativeNetworkBridge(
        val netCtor: Constructor<*>,
        val addPostDataMethod: Method,
        val postNetDataMethod: Method,
        val setNeedTbsMethod: Method,
        val setNeedSigMethod: Method,
        val serverAddressField: Field,
        val getCurrentAccountMethod: Method,
        val hybridNativeProxy: AutoSignInHybridNativeProxySymbols?,
    )

    companion object {
        private const val HYBRID_API_HOST = "https://tiebac.baidu.com"
        private const val FORUM_LIST_PATH = "c/f/forum/getforumlist"
        private const val MSIGN_PATH = "c/c/forum/msign"
        private const val SIGN_PATH = "c/c/forum/sign"
        private val bridges = mutableMapOf<ClassLoader, NativeNetworkBridge>()

        fun resolve(context: Context): AutoSignInNetwork? {
            val loader = context.classLoader ?: return null
            synchronized(bridges) { bridges[loader]?.let { return AutoSignInNetwork(it) } }
            return try {
                val symbols = HookSymbolResolver.getMemorySymbols() ?: return null
                fun required(name: String, value: String?): String =
                    requireNotNull(value?.takeIf { it.isNotBlank() }) { "Missing $name" }
                val networkClass = Class.forName(required("autoSignInNetworkClass", symbols[AutoSignInContract.autoSignInNetworkClass]), false, loader)
                required("autoSignInNetworkConstructorSpec", symbols[AutoSignInContract.autoSignInNetworkConstructorSpec])
                val configClass = Class.forName(required("autoSignInTbConfigClass", symbols[AutoSignInContract.autoSignInTbConfigClass]), false, loader)
                val appClass = Class.forName(required("autoSignInCoreApplicationClass", symbols[AutoSignInContract.autoSignInCoreApplicationClass]), false, loader)
                val bridge = NativeNetworkBridge(
                    networkClass.getDeclaredConstructor(String::class.java).apply { isAccessible = true },
                    networkClass.getDeclaredMethod(required("autoSignInNetworkAddPostDataMethod", symbols[AutoSignInContract.autoSignInNetworkAddPostDataMethod]),
                        String::class.java, String::class.java).apply { isAccessible = true },
                    networkClass.getDeclaredMethod(required("autoSignInNetworkPostNetDataMethod", symbols[AutoSignInContract.autoSignInNetworkPostNetDataMethod]))
                        .apply { isAccessible = true },
                    networkClass.getDeclaredMethod(required("autoSignInNetworkSetNeedTbsMethod", symbols[AutoSignInContract.autoSignInNetworkSetNeedTbsMethod]),
                        Boolean::class.javaPrimitiveType).apply { isAccessible = true },
                    networkClass.getDeclaredMethod(required("autoSignInNetworkSetNeedSigMethod", symbols[AutoSignInContract.autoSignInNetworkSetNeedSigMethod]),
                        Boolean::class.javaPrimitiveType).apply { isAccessible = true },
                    configClass.getDeclaredField(required("autoSignInServerAddressField", symbols[AutoSignInContract.autoSignInServerAddressField]))
                        .apply { isAccessible = true },
                    appClass.getDeclaredMethod(required("autoSignInCurrentAccountMethod", symbols[AutoSignInContract.autoSignInCurrentAccountMethod]))
                        .apply { isAccessible = true },
                    AutoSignInContract.resolveAutoSignInHybridNativeProxySymbols(loader, symbols),
                )
                synchronized(bridges) { bridges[loader] = bridge }
                AutoSignInNetwork(bridge)
            } catch (t: Throwable) {
                XposedCompat.logW("[AutoSignIn] network bridge unavailable: ${t.message}")
                null
            }
        }
    }
}
