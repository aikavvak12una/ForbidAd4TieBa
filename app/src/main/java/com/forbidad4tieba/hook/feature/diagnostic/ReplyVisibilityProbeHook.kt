package com.forbidad4tieba.hook.feature.diagnostic

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.ReplyVisibilityProbeSymbols
import com.forbidad4tieba.hook.ui.UiText
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

object ReplyVisibilityProbeHook {
    private const val TAG = "[ReplyVisibilityProbeHook]"
    private const val RESPONSE_TIMEOUT_MS = 3000L
    private const val AGREE_OP_TYPE_ADD = 0
    private const val AGREE_OP_TYPE_CANCEL = 1
    private const val AGREE_OBJ_TYPE_REPLY = 1
    private const val AGREE_OBJ_TYPE_SUB_REPLY = 2
    private const val AGREE_TYPE_LIKE = 2
    private const val CODE_SUCCESS = "0"
    private const val CODE_NOT_EXISTS = "3280005"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingByTag = ConcurrentHashMap<Any, ProbeRequest>()

    @Volatile private var hooked = false
    @Volatile private var runtimeDisabled = false

    internal fun hook(targets: ReplyVisibilityProbeSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return
        try {
            mod.hook(targets.replyDecodeMethod).intercept { chain ->
                val result = chain.proceed()
                if (!runtimeDisabled && ConfigManager.isReplyVisibilityProbeEnabled) {
                    runCatching { onReplyDecoded(targets, chain.thisObject) }
                        .onFailure { disableRuntime("reply handling failed", it) }
                }
                result
            }
            mod.hook(targets.agreeDecodeLogicMethod).intercept { chain ->
                val result = chain.proceed()
                if (!runtimeDisabled && ConfigManager.isReplyVisibilityProbeEnabled && pendingByTag.isNotEmpty()) {
                    runCatching {
                        onAgreeDecoded(
                            targets = targets,
                            response = chain.thisObject,
                            json = chain.args.getOrNull(1) as? JSONObject,
                        )
                    }.onFailure { disableRuntime("agree handling failed", it) }
                }
                result
            }
            XposedCompat.log(
                "$TAG hook INSTALLED: " +
                    "${targets.replyDecodeMethod.declaringClass.name}.${targets.replyDecodeMethod.name} / " +
                    "${targets.agreeDecodeLogicMethod.declaringClass.name}.${targets.agreeDecodeLogicMethod.name}",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun onReplyDecoded(targets: ReplyVisibilityProbeSymbols, response: Any?) {
        response ?: return
        val json = targets.replyResultJsonField.get(response) as? JSONObject ?: return
        if (!isReplyAccepted(json)) return

        val originalMessage = targets.getOriginalMessageMethod.invoke(response) ?: return
        val extra = targets.messageGetExtraMethod.invoke(originalMessage) ?: return
        if (!targets.addPostRequestClass.isInstance(extra)) return
        val requestData = targets.addPostRequestDataField.get(extra) as? Map<*, *> ?: return

        val pid = json.optText("pid") ?: return
        val tid = json.optText("tid") ?: requestData.textValue("tid") ?: return
        val fid = requestData.textValue("fid") ?: return
        if (pid == "0" || tid == "0" || fid == "0") return
        val quoteId = requestData.textValue("quote_id")
        val subPostId = requestData.textValue("sub_post_id")
        val objType = if (!quoteId.isNullOrBlank() || !subPostId.isNullOrBlank()) {
            AGREE_OBJ_TYPE_SUB_REPLY
        } else {
            AGREE_OBJ_TYPE_REPLY
        }
        if (ConfigManager.shouldOutputDetailedLogs()) {
            XposedCompat.logD {
                "$TAG accepted reply pid=$pid tid=$tid fid=$fid " +
                    "quote_id=${quoteId.orEmpty()} " +
                    "sub_post_id=${subPostId.orEmpty()} obj_type=$objType"
            }
        }

        dispatchProbe(
            targets = targets,
            request = ProbeRequest(tid = tid, pid = pid, fid = fid, objType = objType, attempt = 1),
        )
    }

    private fun isReplyAccepted(json: JSONObject): Boolean {
        val error = json.optJSONObject("error")
        val errno = error?.optText("errno")
        if (errno != null && errno != CODE_SUCCESS) return false
        return !json.optText("pid").isNullOrBlank()
    }

    private fun dispatchProbe(targets: ReplyVisibilityProbeSymbols, request: ProbeRequest) {
        mainHandler.postDelayed({
            if (runtimeDisabled || !ConfigManager.isReplyVisibilityProbeEnabled) return@postDelayed
            sendProbe(targets, request)
        }, probeIntervalMs())
    }

    private fun sendProbe(targets: ReplyVisibilityProbeSymbols, request: ProbeRequest) {
        sendAgreeRequest(targets, request, AGREE_OP_TYPE_ADD, trackResponse = true)
    }

    private fun sendCancel(targets: ReplyVisibilityProbeSymbols, request: ProbeRequest) {
        mainHandler.post {
            if (runtimeDisabled || !ConfigManager.isReplyVisibilityProbeEnabled) return@post
            sendAgreeRequest(targets, request, AGREE_OP_TYPE_CANCEL, trackResponse = false)
        }
    }

    private fun sendAgreeRequest(
        targets: ReplyVisibilityProbeSymbols,
        request: ProbeRequest,
        opType: Int,
        trackResponse: Boolean,
    ) {
        var tag: Any? = null
        try {
            val cmd = (targets.cmdPbFloorAgreeField.get(null) as? Number)?.toInt() ?: run {
                handleSendFailed(targets, request, trackResponse)
                return
            }
            val zid = resolveZid(targets)
            if (zid.isNullOrBlank()) {
                handleSendFailed(targets, request, trackResponse)
                return
            }
            val manager = targets.messageManagerGetInstanceMethod.invoke(null) ?: run {
                handleSendFailed(targets, request, trackResponse)
                return
            }
            if (!ensureAgreeTask(targets, manager, cmd)) {
                handleSendFailed(targets, request, trackResponse)
                return
            }

            val message = targets.httpMessageConstructor.newInstance(cmd)
            targets.httpMessageAddParamMethod.invoke(message, "z_id", zid)
            targets.httpMessageAddParamMethod.invoke(message, "thread_id", request.tid)
            targets.httpMessageAddParamMethod.invoke(message, "op_type", opType)
            targets.httpMessageAddParamMethod.invoke(message, "obj_type", request.objType)
            targets.httpMessageAddParamMethod.invoke(message, "agree_type", AGREE_TYPE_LIKE)
            targets.httpMessageAddParamMethod.invoke(message, "forum_id", request.fid)
            targets.httpMessageAddParamMethod.invoke(message, "post_id", request.pid)
            targets.httpMessageAddHeaderMethod.invoke(message, "needSig", "1")
            if (ConfigManager.shouldOutputDetailedLogs()) {
                XposedCompat.logD {
                    "$TAG send ${if (trackResponse) "probe" else "cancel"} " +
                        "thread_id=${request.tid} post_id=${request.pid} forum_id=${request.fid} " +
                        "op_type=$opType obj_type=${request.objType} agree_type=$AGREE_TYPE_LIKE"
                }
            }

            if (trackResponse) {
                tag = targets.bdUniqueIdGenMethod.invoke(null) ?: run {
                    handleSendFailed(targets, request, trackResponse = true)
                    return
                }
                targets.messageSetTagMethod.invoke(message, tag)
                pendingByTag[tag] = request
            }

            val sent = targets.messageManagerSendMethod.invoke(manager, message) as? Boolean ?: false
            if (!sent) {
                pendingByTag.remove(tag)
                handleSendFailed(targets, request, trackResponse)
            } else if (trackResponse) {
                tag?.let { scheduleResponseTimeout(targets, it, request) }
            }
        } catch (t: Throwable) {
            tag?.let { pendingByTag.remove(it) }
            XposedCompat.logD { "$TAG agree request send failed: ${t.message}" }
            handleSendFailed(targets, request, trackResponse)
        }
    }

    private fun ensureAgreeTask(targets: ReplyVisibilityProbeSymbols, manager: Any, cmd: Int): Boolean {
        if (targets.messageManagerFindTaskMethod.invoke(manager, cmd) != null) return true
        val serverAddress = targets.tbConfigServerAddressField.get(null) as? String
        val agreePath = targets.tbConfigPbFloorAgreeUrlField.get(null) as? String
        if (serverAddress.isNullOrBlank() || agreePath.isNullOrBlank()) return false
        val task = targets.tbHttpMessageTaskConstructor.newInstance(cmd, serverAddress + agreePath)
        targets.httpMessageTaskSetResponsedClassMethod.invoke(task, targets.agreeResponseClass)
        targets.tbHttpMessageTaskSetIsNeedTbsMethod.invoke(task, true)
        targets.messageManagerRegisterTaskMethod.invoke(manager, task)
        return true
    }

    private fun resolveZid(targets: ReplyVisibilityProbeSymbols): String? {
        val app = targets.tbadkCoreApplicationGetInstMethod.invoke(null) ?: return null
        return targets.tbadkCoreApplicationGetZidMethod.invoke(app) as? String
    }

    private fun onAgreeDecoded(targets: ReplyVisibilityProbeSymbols, response: Any?, json: JSONObject?) {
        response ?: return
        val originalMessage = targets.getOriginalMessageMethod.invoke(response) ?: return
        val tag = targets.messageGetTagMethod.invoke(originalMessage) ?: return
        val request = pendingByTag.remove(tag) ?: return
        if (!ConfigManager.isReplyVisibilityProbeEnabled) return
        if (json == null) {
            handleProbeResult(
                targets,
                request,
                AgreeResult(
                    UiText.Settings.REPLY_VISIBILITY_PROBE_CODE_EMPTY_RESPONSE,
                    UiText.Settings.REPLY_VISIBILITY_PROBE_EMPTY_RESPONSE,
                ),
            )
            return
        }

        val result = parseAgreeResult(json)
        if (result.code.isNullOrBlank()) {
            handleProbeResult(
                targets,
                request,
                AgreeResult(
                    UiText.Settings.REPLY_VISIBILITY_PROBE_CODE_MISSING_RESULT,
                    UiText.Settings.REPLY_VISIBILITY_PROBE_MISSING_RESULT,
                ),
            )
            return
        }

        handleProbeResult(targets, request, result)
    }

    private fun parseAgreeResult(json: JSONObject): AgreeResult {
        val topCode = json.optText("error_code") ?: json.optText("errno")
        val error = json.optJSONObject("error")
        val nestedCode = error?.optText("errno") ?: error?.optText("error_code")
        return AgreeResult(
            code = topCode ?: nestedCode,
            message = firstText(
                json.optText("error_msg"),
                json.optText("errmsg"),
                error?.optText("error_msg"),
                error?.optText("errmsg"),
                error?.optText("error_user_msg"),
                json.optJSONObject("data")?.optText("toast"),
                json.optJSONObject("data")?.optText("toast_msg"),
            ),
        )
    }

    private fun handleProbeResult(
        targets: ReplyVisibilityProbeSymbols,
        request: ProbeRequest,
        result: AgreeResult,
    ) {
        if (result.code == CODE_SUCCESS) {
            showToast(result.code.orEmpty() + result.message.orEmpty())
            sendCancel(targets, request)
            return
        }
        if (result.code != CODE_NOT_EXISTS) {
            showToast(result.code.orEmpty() + result.message.orEmpty())
            return
        }
        if (request.attempt < maxAttempts()) {
            XposedCompat.logD {
                "$TAG retry probe pid=${request.pid} code=${result.code.orEmpty()}"
            }
            scheduleRetry(targets, request)
            return
        }
        showToast(result.code.orEmpty() + result.message.orEmpty())
    }

    private fun handleSendFailed(
        targets: ReplyVisibilityProbeSymbols,
        request: ProbeRequest,
        trackResponse: Boolean,
    ) {
        if (!trackResponse) return
        handleProbeResult(
            targets,
            request,
            AgreeResult(
                UiText.Settings.REPLY_VISIBILITY_PROBE_CODE_SEND_FAILED,
                UiText.Settings.REPLY_VISIBILITY_PROBE_SEND_FAILED,
            ),
        )
    }

    private fun scheduleRetry(targets: ReplyVisibilityProbeSymbols, request: ProbeRequest) {
        mainHandler.postDelayed({
            if (runtimeDisabled || !ConfigManager.isReplyVisibilityProbeEnabled) return@postDelayed
            sendProbe(targets, request.copy(attempt = request.attempt + 1))
        }, probeIntervalMs())
    }

    private fun scheduleResponseTimeout(targets: ReplyVisibilityProbeSymbols, tag: Any, request: ProbeRequest) {
        mainHandler.postDelayed({
            val pending = pendingByTag.remove(tag) ?: return@postDelayed
            if (runtimeDisabled || !ConfigManager.isReplyVisibilityProbeEnabled) return@postDelayed
            handleProbeResult(
                targets,
                pending,
                AgreeResult(
                    UiText.Settings.REPLY_VISIBILITY_PROBE_CODE_TIMEOUT,
                    UiText.Settings.REPLY_VISIBILITY_PROBE_TIMEOUT,
                ),
            )
        }, RESPONSE_TIMEOUT_MS)
    }

    private fun maxAttempts(): Int = ConfigManager.replyVisibilityProbeMaxAttempts

    private fun probeIntervalMs(): Long = ConfigManager.replyVisibilityProbeIntervalMs.toLong()

    private fun JSONObject.optText(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun Map<*, *>.textValue(key: String): String? {
        return get(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun firstText(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.take(120)
    }

    private fun showToast(message: String) {
        val appContext = ConfigManager.getAppContext() ?: return
        mainHandler.post {
            runCatching {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableRuntime(reason: String, throwable: Throwable) {
        runtimeDisabled = true
        XposedCompat.log("$TAG runtime FAILED, disabled for this process: $reason: ${throwable.message}")
        XposedCompat.log(throwable)
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            runtimeDisabled = false
            pendingByTag.clear()
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) {
            hooked = false
            runtimeDisabled = false
            pendingByTag.clear()
        }
    }

    private data class ProbeRequest(
        val tid: String,
        val pid: String,
        val fid: String,
        val objType: Int,
        val attempt: Int,
    )

    private data class AgreeResult(
        val code: String?,
        val message: String?,
    )
}
