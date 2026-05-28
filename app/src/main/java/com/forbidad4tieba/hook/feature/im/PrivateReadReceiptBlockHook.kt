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
    private val allowedReadMsgIdByPeer = ConcurrentHashMap<Long, Long>()
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
                    val boundary = updateAllowedBoundaryAfterSend(targets, model)
                    sendBoundaryReadReceipt(targets, boundary)
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
                    XposedCompat.log(
                        "[PrivateReadReceiptBlockHook] block read receipt: invalid request " +
                            "peer=$peerUid msg=$requestedWireMsgId",
                    )
                    showToast(UiText.PrivateReadReceipt.TOAST_REPORT_INTERCEPTED)
                    return@intercept true
                }

                val allowedMsgId = allowedReadMsgIdByPeer[peerUid] ?: 0L
                val allowedWireMsgId = allowedMsgId / 100L
                if (allowedWireMsgId <= 0L) {
                    XposedCompat.log(
                        "[PrivateReadReceiptBlockHook] block read receipt: no boundary " +
                            "peer=$peerUid requested=$requestedWireMsgId",
                    )
                    showToast(UiText.PrivateReadReceipt.TOAST_REPORT_INTERCEPTED)
                    return@intercept true
                }
                var intercepted = false
                val finalWireMsgId = if (requestedWireMsgId > allowedWireMsgId) {
                    val updated = runCatching {
                        targets.requestMsgIdField.setLong(message, allowedWireMsgId)
                    }.isSuccess
                    if (!updated) {
                        XposedCompat.log(
                            "[PrivateReadReceiptBlockHook] block read receipt: downgrade failed " +
                                "peer=$peerUid requested=$requestedWireMsgId allowed=$allowedWireMsgId",
                        )
                        showToast(UiText.PrivateReadReceipt.TOAST_REPORT_INTERCEPTED)
                        return@intercept true
                    }
                    intercepted = true
                    XposedCompat.log(
                        "[PrivateReadReceiptBlockHook] downgrade read receipt: " +
                            "peer=$peerUid requested=$requestedWireMsgId allowed=$allowedWireMsgId",
                    )
                    allowedWireMsgId
                } else {
                    XposedCompat.log(
                        "[PrivateReadReceiptBlockHook] allow read receipt: " +
                            "peer=$peerUid requested=$requestedWireMsgId boundary=$allowedWireMsgId",
                    )
                    requestedWireMsgId
                }
                val result = chain.proceed()
                if (result == true) {
                    submittedReadWireMsgIdByPeer.merge(peerUid, finalWireMsgId, ::maxOf)
                    if (intercepted) {
                        showToast(UiText.PrivateReadReceipt.TOAST_REPORT_INTERCEPTED)
                    }
                }
                result
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

    private fun updateAllowedBoundaryAfterSend(targets: PrivateReadReceiptSymbols, model: Any?): ReadBoundary? {
        if (model == null) return null
        return runCatching {
            val currentAccount = targets.currentAccountMethod.invoke(null) as? String
            val currentUid = currentAccount?.toLongOrNull()?.takeIf { it > 0L } ?: return@runCatching null
            val pageData = targets.modelDataField.get(model) ?: return@runCatching null
            val messages = targets.pageDataChatListMethod.invoke(pageData) as? List<*> ?: return@runCatching null
            if (messages.isEmpty()) return@runCatching null

            var peerUid = 0L
            var boundaryMsgId = 0L
            for (index in messages.size - 1 downTo 0) {
                val message = messages[index] ?: continue
                val userId = readLongMethod(targets.chatMessageUserIdMethod, message) ?: continue
                if (userId > 0L && userId != currentUid) {
                    peerUid = userId
                    boundaryMsgId = readLongMethod(targets.chatMessageMsgIdMethod, message) ?: 0L
                    break
                }
            }
            if (peerUid > 0L) {
                allowedReadMsgIdByPeer[peerUid] = boundaryMsgId.coerceAtLeast(0L)
                XposedCompat.log(
                    "[PrivateReadReceiptBlockHook] boundary updated: peer=$peerUid " +
                        "boundary=${boundaryMsgId.coerceAtLeast(0L)} reason=send_success size=${messages.size}",
                )
                ReadBoundary(peerUid, boundaryMsgId.coerceAtLeast(0L)).takeIf { it.msgId > 0L }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun sendBoundaryReadReceipt(targets: PrivateReadReceiptSymbols, boundary: ReadBoundary?) {
        if (boundary == null || boundary.peerUid <= 0L || boundary.msgId <= 0L) return
        val wireMsgId = boundary.msgId / 100L
        if (wireMsgId <= 0L) return
        val submitted = submittedReadWireMsgIdByPeer[boundary.peerUid] ?: 0L
        if (submitted >= wireMsgId) {
            XposedCompat.log(
                "[PrivateReadReceiptBlockHook] skip direct read receipt: " +
                    "peer=${boundary.peerUid} wire=$wireMsgId submitted=$submitted",
            )
            return
        }
        runCatching {
            val manager = targets.messageManagerGetInstanceMethod.invoke(null)
            val request = targets.requestConstructor.newInstance(wireMsgId, boundary.peerUid)
            XposedCompat.log(
                "[PrivateReadReceiptBlockHook] direct read receipt after send ack: " +
                    "peer=${boundary.peerUid} wire=$wireMsgId",
            )
            val sent = targets.messageManagerSendMethod.invoke(manager, request) as? Boolean ?: false
            if (sent) {
                submittedReadWireMsgIdByPeer.merge(boundary.peerUid, wireMsgId, ::maxOf)
                showToast(UiText.PrivateReadReceipt.TOAST_STATE_SYNCED)
            }
        }.onFailure { t ->
            XposedCompat.log("[PrivateReadReceiptBlockHook] direct read receipt failed: ${t.message}")
        }
    }

    private fun readLongMethod(method: Method, receiver: Any): Long? {
        return (method.invoke(receiver) as? Number)?.toLong()
    }

    private fun readIntMethod(method: Method, receiver: Any): Int? {
        return (method.invoke(receiver) as? Number)?.toInt()
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
