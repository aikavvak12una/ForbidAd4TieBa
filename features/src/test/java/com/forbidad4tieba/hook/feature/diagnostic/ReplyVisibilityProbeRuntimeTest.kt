package com.forbidad4tieba.hook.feature.diagnostic

import android.os.Handler
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.core.OwnedHookSet
import com.forbidad4tieba.hook.core.RuntimeHooks
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ReplyVisibilityProbeSymbols
import com.forbidad4tieba.hook.testing.FakeHookApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplyVisibilityProbeRuntimeTest {
    @Test fun acceptedProbeIsCancelledAndDuplicateResponsesCannotCancelTwice() = withRuntime { f ->
        val probe = f.start()
        f.complete(probe)
        Handler.runCurrent()
        f.complete(probe)
        Handler.runCurrent()
        assertEquals(listOf(0, 1), f.operations())
        assertFalse(Manager.liked)
        assertEquals(0, f.pendingCount())
    }

    @Test fun successAfterTimeoutRetainsCorrelationAndCancelsWhileDisabled() = withRuntime { f ->
        val probe = f.start()
        Handler.advanceBy(3_000L)
        assertEquals(1, f.pendingCount())
        f.enabled(false)
        Handler.advanceBy(5_000L)
        f.complete(probe)
        Handler.runCurrent()
        assertEquals(listOf(0, 1), f.operations())
        assertFalse(Manager.liked)
        assertEquals(0, f.pendingCount())
    }

    @Test fun disabledReplyCallbackDoesNotReadResponseOrStartARequest() = withRuntime { f ->
        f.enabled(false)
        f.reply(allowResponse = false)
        Handler.advanceBy(10_000L)
        assertTrue(Manager.sent.isEmpty())
    }

    @Test fun unrelatedResponsesDoNotCancelOrConsumeOwnedRequests() = withRuntime { f ->
        f.start()
        f.complete(Message(99).apply { tag = "foreign-tag" })
        Handler.runCurrent()
        assertEquals(listOf(0), f.operations())
        assertEquals(1, f.pendingCount())
    }

    @Test fun cancellationRetainsTheSendingAccountIdentity() = withRuntime { f ->
        val probe = f.start()
        Core.zid = "replacement-account"
        f.complete(probe)
        Handler.runCurrent()
        assertEquals("fixture-account", Manager.sent.last().data["z_id"])
    }

    @Test fun ambiguousSendFailureStillAcceptsALateSuccessForCleanup() = withRuntime { f ->
        Manager.throwAfterAccept = true
        val probe = f.start()
        Manager.throwAfterAccept = false
        f.complete(probe)
        Handler.runCurrent()
        assertEquals(listOf(0, 1), f.operations())
        assertFalse(Manager.liked)
    }

    @Test fun outstandingProbeCountIsBoundedAndCompletionRestoresCapacity() = withRuntime { f ->
        repeat(65) { f.reply() }
        Handler.advanceBy(1_000L)
        assertEquals(64, Manager.sent.size)
        assertEquals(64, f.pendingCount())
        Handler.advanceBy(3_600_000L)
        assertEquals(64, f.pendingCount())
        f.reply()
        Handler.advanceBy(1_000L)
        assertEquals(64, Manager.sent.size)
        f.complete(Manager.sent.first(), JSONObject().put("errno", "3280005"))
        f.enabled(false)
        Handler.runCurrent()
        assertEquals(63, f.pendingCount())
        f.enabled(true)
        f.start()
        assertEquals(65, Manager.sent.size)
    }

    private class Fixture(val api: FakeHookApi) {
        private val replyMethod = Response::class.java.getMethod("replyDecode")
        private val agreeMethod = Response::class.java.getMethod("agreeDecode", Int::class.javaPrimitiveType, JSONObject::class.java)
        fun enabled(enabled: Boolean) {
            ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
                .set(null, SettingsSnapshot.bootstrap().copy(isReplyVisibilityProbeEnabled = enabled))
        }
        fun reply(allowResponse: Boolean = true) {
            val message = Message(99).apply { extra = Request() }
            invoke(replyMethod, Response(message, JSONObject().put("pid", "fixture-post").put("tid", "fixture-thread")),
                emptyList(), allowResponse)
        }
        fun start(): Message {
            val before = Manager.sent.size
            reply()
            Handler.advanceBy(1_000L)
            assertEquals(before + 1, Manager.sent.size)
            return Manager.sent.last()
        }
        fun complete(message: Message, json: JSONObject? = JSONObject().put("errno", "0")) =
            invoke(agreeMethod, Response(message, JSONObject()), listOf(0, json), true)
        fun operations() = Manager.sent.map { (it.data["op_type"] as Number).toInt() }
        fun pendingCount(): Int = (ReplyVisibilityProbeHook::class.java.getDeclaredField("pendingByTag")
            .apply { isAccessible = true }.get(null) as Map<*, *>).size
        private fun invoke(method: Method, response: Response, args: List<Any?>, allowResponse: Boolean) {
            var proceeded = 0
            var responseReads = 0
            var argumentReads = 0
            val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, member, values ->
                when (member.name) {
                    "proceed" -> { check(values.isNullOrEmpty()); proceeded++; "host-result" }
                    "getThisObject" -> { responseReads++; check(allowResponse) { "Disabled callback read response" }; response }
                    "getArgs" -> { argumentReads++; check(allowResponse); args }
                    else -> error("Unexpected callback access: ${member.name}")
                }
            } as XposedInterface.Chain
            assertEquals("host-result", checkNotNull(api.callbacks[method]).intercept(chain))
            assertEquals(1, proceeded)
            if (!allowResponse) {
                assertEquals("Caught failures must not hide response reads", 0, responseReads)
                assertEquals("Caught failures must not hide argument reads", 0, argumentReads)
            }
        }
    }

    private fun withRuntime(test: (Fixture) -> Unit) {
        val snapshot = ConfigManager::class.java.getDeclaredField("settingsSnapshot").apply { isAccessible = true }
        val module = XposedCompat::class.java.getDeclaredField("module").apply { isAccessible = true }
        val previousSettings = snapshot.get(null)
        val previousModule = module.get(null)
        val api = FakeHookApi(allowLog = true)
        Handler.beginTest()
        try {
            reset()
            Manager.sent.clear(); Manager.liked = false; Manager.accept = true; Manager.throwAfterAccept = false
            Response.failMetadata = false
            Core.zid = "fixture-account"
            XposedCompat.attachModule(object : XposedModule() {}.apply { attachFramework(api.value) {} })
            val f = Fixture(api)
            f.enabled(true)
            ReplyVisibilityProbeHook.hook(targets())
            test(f)
        } finally {
            try {
                reset()
                val hooks = RuntimeHooks::class.java.getDeclaredField("hooks").apply { isAccessible = true }
                    .get(null) as OwnedHookSet<*, *>
                assertTrue(hooks.rollback().isEmpty())
            } finally {
                snapshot.set(null, previousSettings)
                module.set(null, previousModule)
                Handler.endTest()
            }
        }
    }

    private fun reset() = ReplyVisibilityProbeHook::class.java.getDeclaredMethod("resetHooked")
        .apply { isAccessible = true }.invoke(ReplyVisibilityProbeHook)

    private fun targets() = ReplyVisibilityProbeSymbols(
        Response::class.java.getMethod("replyDecode"), Response::class.java.getField("json"),
        Request::class.java, Request::class.java.getField("data"), Response::class.java.getMethod("original"),
        Message::class.java.getMethod("getExtra"), Message::class.java.getMethod("getTag"),
        Message::class.java.getMethod("setTag", Any::class.java), Message::class.java.getConstructor(Int::class.javaPrimitiveType),
        Message::class.java.getMethod("addParam", String::class.java, Any::class.java),
        Message::class.java.getMethod("addHeader", String::class.java, String::class.java), Manager::class.java.getMethod("instance"),
        Manager::class.java.getMethod("findTask", Int::class.javaPrimitiveType), Manager::class.java.getMethod("registerTask", Any::class.java),
        Manager::class.java.getMethod("send", Message::class.java), Task::class.java.getConstructor(Int::class.javaPrimitiveType, String::class.java),
        Task::class.java.getMethod("responseClass", Class::class.java), Task::class.java.getMethod("needTbs", Boolean::class.javaPrimitiveType),
        Manager::class.java.getMethod("tag"), Core::class.java.getMethod("instance"), Core::class.java.getMethod("getZid"),
        Constants::class.java.getField("SERVER"), Constants::class.java.getField("PATH"), Constants::class.java.getField("COMMAND"),
        Response::class.java, Response::class.java.getMethod("agreeDecode", Int::class.javaPrimitiveType, JSONObject::class.java),
    )

    class Message(command: Int) {
        @JvmField val data = linkedMapOf<String, Any?>()
        @JvmField var tag: Any? = null
        @JvmField var extra: Any? = null
        fun addParam(name: String, value: Any?) { data[name] = value }
        fun addHeader(name: String, value: String) = Unit
        fun getTag(): Any? = tag
        fun setTag(value: Any?) { tag = value }
        fun getExtra(): Any? = extra
    }
    class Response(private val message: Message, @JvmField val json: JSONObject) {
        fun original(): Message {
            check(!failMetadata) { "fixture response metadata failure" }
            return message
        }
        fun replyDecode() = Unit
        fun agreeDecode(code: Int, data: JSONObject?) = Unit
        companion object { @JvmField var failMetadata = false }
    }
    class Request { @JvmField val data = mapOf("fid" to "fixture-forum") }
    class Task(command: Int, url: String) {
        fun responseClass(type: Class<*>) = Unit
        fun needTbs(enabled: Boolean) = Unit
    }
    class Core {
        fun getZid() = zid
        companion object {
            @JvmField var zid = "fixture-account"
            private val value = Core()
            @JvmStatic fun instance() = value
        }
    }
    class Manager {
        fun findTask(command: Int): Any = Any()
        fun registerTask(task: Any) = Unit
        fun send(message: Message): Boolean {
            if (!accept) return false
            sent += message
            liked = (message.data["op_type"] as Number).toInt() == 0
            check(!throwAfterAccept) { "fixture send outcome is ambiguous" }
            return true
        }
        companion object {
            @JvmField val sent = arrayListOf<Message>()
            @JvmField var liked = false
            @JvmField var accept = true
            @JvmField var throwAfterAccept = false
            private val value = Manager()
            private var sequence = 0
            @JvmStatic fun instance() = value
            @JvmStatic fun tag(): Any = "fixture-tag-${++sequence}"
        }
    }
    class Constants {
        companion object {
            @JvmField val SERVER = "https://fixture.invalid/"
            @JvmField val PATH = "fixture"
            @JvmField val COMMAND = 99
        }
    }
}
