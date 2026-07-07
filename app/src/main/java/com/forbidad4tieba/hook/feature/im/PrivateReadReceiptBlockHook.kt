package com.forbidad4tieba.hook.feature.im

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.forbidad4tieba.hook.symbol.model.PrivateReadReceiptSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PrivateReadReceiptBlockHook {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val installed = AtomicBoolean(false)
    private val syncDispatchDepth = ThreadLocal.withInitial { 0 }
    private val submittedReadWireMsgIdByPeer = ConcurrentHashMap<Long, Long>()

    private data class ReadBoundary(
        val peerUid: Long,
        val msgId: Long,
    )

    internal fun hook(targets: PrivateReadReceiptSymbols) {
        val mod = XposedCompat.module ?: return
        if (!installed.compareAndSet(false, true)) {
            XposedCompat.logD("[PrivateReadReceiptBlockHook] already installed, skip")
            return
        }

        try {
            mod.hook(targets.processAckMethod).intercept { chain ->
                val result = chain.proceed()
                if (!ConfigManager.isPrivateReadReceiptInvisibleEnabled) {
                    return@intercept result
                }
                val model = chain.thisObject
                if (!targets.modelClass.isInstance(model)) {
                    return@intercept result
                }
                val response = chain.args.firstOrNull() ?: return@intercept result
                val error = readIntMethod(targets.responseErrorMethod, response) ?: return@intercept result
                if (error == 0) {
                    syncReadStateAfterSendAck(targets, model)
                }
                result
            }

            mod.hook(targets.messageManagerSendMethod).intercept { chain ->
                if (!ConfigManager.isPrivateReadReceiptInvisibleEnabled) {
                    return@intercept chain.proceed()
                }
                val message = chain.args.firstOrNull() ?: return@intercept chain.proceed()
                if (!targets.requestMessageClass.isInstance(message)) {
                    return@intercept chain.proceed()
                }

                val peerUid = readLongField(targets.requestToUidField, message)
                val requestedWireMsgId = readLongField(targets.requestMsgIdField, message)
                if (peerUid == null || requestedWireMsgId == null || requestedWireMsgId <= 0L) {
                    XposedCompat.logD(
                        "[PrivateReadReceiptBlockHook] block read receipt: invalid request " +
                            "peer=$peerUid msg=$requestedWireMsgId",
                    )
                    showToast(UiText.PrivateReadReceipt.TOAST_REPORT_INTERCEPTED)
                    return@intercept true
                }

                if ((syncDispatchDepth.get() ?: 0) > 0) {
                    XposedCompat.logD(
                        "[PrivateReadReceiptBlockHook] allow sync read receipt after send: " +
                            "peer=$peerUid requested=$requestedWireMsgId",
                    )
                    val sent = chain.proceed()
                    if (sent == true) {
                        submittedReadWireMsgIdByPeer.merge(peerUid, requestedWireMsgId, ::maxOf)
                        showToast(UiText.PrivateReadReceipt.TOAST_STATE_SYNCED)
                    }
                    return@intercept sent
                }

                XposedCompat.logD(
                    "[PrivateReadReceiptBlockHook] block read receipt: " +
                        "peer=$peerUid requested=$requestedWireMsgId",
                )
                showToast(UiText.PrivateReadReceipt.TOAST_REPORT_INTERCEPTED)
                true
            }

            XposedCompat.log(
                "[PrivateReadReceiptBlockHook] hook INSTALLED: " +
                    "${targets.processAckMethod.declaringClass.name}.${targets.processAckMethod.name} / " +
                    "${targets.messageManagerSendMethod.declaringClass.name}.${targets.messageManagerSendMethod.name}",
            )
        } catch (t: Throwable) {
            installed.set(false)
            XposedCompat.log("[PrivateReadReceiptBlockHook] FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun syncReadStateAfterSendAck(targets: PrivateReadReceiptSymbols, model: Any?) {
        if (model == null) return
        val boundary = readLatestPeerBoundary(targets, model)
        val previousDepth = syncDispatchDepth.get() ?: 0
        syncDispatchDepth.set(previousDepth + 1)
        try {
            val hostSynced = runCatching {
                targets.modelReadDispatchMethod.invoke(model)
            }.onFailure { t ->
                XposedCompat.log("[PrivateReadReceiptBlockHook] sync read state failed: ${t.message}")
            }.isSuccess
            if (!hostSynced) return
            sendBoundaryReadReceipt(targets, boundary)
        } finally {
            syncDispatchDepth.set(previousDepth)
        }
    }

    private fun readLatestPeerBoundary(targets: PrivateReadReceiptSymbols, model: Any): ReadBoundary? {
        return runCatching {
            val currentAccount = targets.currentAccountMethod.invoke(null) as? String
            val currentUid = currentAccount?.toLongOrNull()?.takeIf { it > 0L } ?: return@runCatching null
            val pageData = targets.modelDataField.get(model) ?: return@runCatching null
            val messages = targets.pageDataChatListMethod.invoke(pageData) as? List<*> ?: return@runCatching null
            for (index in messages.size - 1 downTo 0) {
                val message = messages[index] ?: continue
                val userId = readLongMethod(targets.chatMessageUserIdMethod, message) ?: continue
                if (userId <= 0L || userId == currentUid) continue
                val msgId = readLongMethod(targets.chatMessageMsgIdMethod, message) ?: continue
                if (msgId > 0L) return@runCatching ReadBoundary(userId, msgId)
            }
            null
        }.onFailure { t ->
            XposedCompat.log("[PrivateReadReceiptBlockHook] read boundary failed: ${t.message}")
        }.getOrNull()
    }

    private fun sendBoundaryReadReceipt(targets: PrivateReadReceiptSymbols, boundary: ReadBoundary?) {
        if (boundary == null || boundary.peerUid <= 0L || boundary.msgId <= 0L) return
        val wireMsgId = boundary.msgId / 100L
        if (wireMsgId <= 0L) return
        val submitted = submittedReadWireMsgIdByPeer[boundary.peerUid] ?: 0L
        if (submitted >= wireMsgId) {
            XposedCompat.logD(
                "[PrivateReadReceiptBlockHook] skip sync read receipt: " +
                    "peer=${boundary.peerUid} wire=$wireMsgId submitted=$submitted",
            )
            return
        }
        runCatching {
            val manager = targets.messageManagerGetInstanceMethod.invoke(null) ?: return
            val request = targets.requestConstructor.newInstance(wireMsgId, boundary.peerUid)
            when (isDuplicateReadReceipt(targets, manager, request)) {
                true -> {
                    XposedCompat.logD(
                        "[PrivateReadReceiptBlockHook] skip sync read receipt: " +
                            "peer=${boundary.peerUid} wire=$wireMsgId duplicate=true",
                    )
                    return@runCatching
                }
                false -> Unit
                null -> {
                    XposedCompat.log(
                        "[PrivateReadReceiptBlockHook] skip sync read receipt: duplicate check unavailable",
                    )
                    return@runCatching
                }
            }
            targets.messageManagerSendMethod.invoke(manager, request)
        }.onFailure { t ->
            XposedCompat.log("[PrivateReadReceiptBlockHook] direct sync read receipt failed: ${t.message}")
        }
    }

    private fun isDuplicateReadReceipt(
        targets: PrivateReadReceiptSymbols,
        manager: Any,
        request: Any,
    ): Boolean? {
        return runCatching {
            val socketClient = targets.messageManagerGetSocketClientMethod.invoke(manager)
                ?: return@runCatching null
            targets.socketDuplicateCheckMethod.invoke(socketClient, request) as? Boolean
        }.onFailure { t ->
            XposedCompat.log("[PrivateReadReceiptBlockHook] duplicate check failed: ${t.message}")
        }.getOrNull()
    }

    private fun readIntMethod(method: Method, receiver: Any): Int? {
        return (method.invoke(receiver) as? Number)?.toInt()
    }

    private fun readLongMethod(method: Method, receiver: Any): Long? {
        return (method.invoke(receiver) as? Number)?.toLong()
    }

    private fun readLongField(field: java.lang.reflect.Field, receiver: Any): Long? {
        return runCatching { field.getLong(receiver) }.getOrNull()
    }

    private fun showToast(message: String) {
        val context = ConfigManager.getAppContext() ?: return
        val appContext = context.applicationContext ?: context
        runCatching {
            mainHandler.post {
                runCatching {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }.onFailure { t ->
                    XposedCompat.logD("[PrivateReadReceiptBlockHook] toast failed: ${t.message}")
                }
            }
        }.onFailure { t ->
            XposedCompat.logD("[PrivateReadReceiptBlockHook] toast post failed: ${t.message}")
        }
    }
}
