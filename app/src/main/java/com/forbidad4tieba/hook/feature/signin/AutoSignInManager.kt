package com.forbidad4tieba.hook.feature.signin

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import com.forbidad4tieba.hook.symbol.model.AutoSignInHybridNativeProxySymbols
import com.forbidad4tieba.hook.HookSymbolResolver
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.ui.UiText
import org.json.JSONObject
import java.math.BigInteger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object AutoSignInManager {
    private const val TAG = "TBHook-AutoSignIn"
    private const val COOKIE_URL = "https://tieba.baidu.com"
    private const val HYBRID_API_HOST = "https://tiebac.baidu.com"
    private const val FORUM_LIST_PATH = "c/f/forum/getforumlist"
    private const val MSIGN_PATH = "c/c/forum/msign"
    private const val SIGN_PATH = "c/c/forum/sign"
    private const val PREF_LAST_SUCCESS_DAY_PREFIX = "last_success_day_"
    private const val DAY_STAMP_PATTERN = "yyyyMMdd"
    private const val LOGIN_COOKIE_ATTEMPTS = 3
    private const val LOGIN_COOKIE_RETRY_DELAY_MS = 5000L
    private const val RETRY_DELAY_MS = 2000L
    private const val LEGACY_SIGN_DELAY_MS = 500L
    private const val DEFAULT_SIGN_MAX_NUM = 50
    private const val LEGACY_MAX_RETRY = 3
    private const val ORDINARY_USER_STATUS = 0
    private const val ORDINARY_USER_MIN_BATCH_LEVEL = 7
    private const val RESPONSE_PREVIEW_LIMIT = 240

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val running = AtomicBoolean(false)
    private val nativeBridgeCache = mutableMapOf<ClassLoader, NativeNetworkBridge>()

    private data class LoginCookie(
        val bduss: String,
    )

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

    private data class ForumInfo(
        val forumId: String,
        val forumName: String,
        val userLevel: Int,
        val isSigned: Boolean,
        val listIndex: Int,
    )

    private data class ForumSnapshot(
        val pendingForums: List<ForumInfo>,
        val signableForums: List<ForumInfo>,
        val totalForums: Int,
        val valid: Boolean,
        val showDialog: Boolean,
        val signNotice: String,
        val userStatus: Int,
        val signMaxNum: Int,
        val batchMinLevel: Int,
        val canUseAllLevels: Boolean,
        val fetched: Boolean,
    )

    private data class BatchSignResult(
        val apiSuccess: Boolean,
        val responseCount: Int,
        val signedCount: Int,
    )

    fun tryAutoSignIn(context: Context, force: Boolean = false) {
        val enabled = ConfigManager.isAutoSignInEnabled(context)
        XposedCompat.log("$TAG: trigger force=$force enabled=$enabled")
        val statePrefs = ConfigManager.getModuleStatePrefs(context)
        if (!enabled && !force) {
            XposedCompat.log("$TAG: skipped - auto sign switch disabled")
            return
        }

        if (!running.compareAndSet(false, true)) {
            XposedCompat.log("$TAG: skipped - task already running")
            if (force) toast(context, UiText.AutoSignIn.TOAST_TASK_RUNNING)
            return
        }

        thread(isDaemon = true, name = "tbhook-auto-signin") {
            val startedAtMs = System.currentTimeMillis()
            var outcome = "unknown"
            var totalPending = 0
            var remainPending = 0
            try {
                val loginCookie = readLoginCookie(force)
                if (loginCookie == null) {
                    XposedCompat.log("$TAG: BDUSS not found in cookie, skip")
                    outcome = "skip_bduss_missing"
                    if (force) toast(context, UiText.AutoSignIn.TOAST_BDUSS_MISSING)
                    return@thread
                }
                val bduss = loginCookie.bduss
                val nativeBridge = resolveNativeNetworkBridge(context)
                if (nativeBridge == null) {
                    XposedCompat.log("$TAG: native network bridge not found, skip")
                    outcome = "skip_native_bridge_missing"
                    return@thread
                }
                val userId = getCurrentAccountId(nativeBridge)
                if (userId.isEmpty()) {
                    XposedCompat.log("$TAG: current account user_id not found, skip")
                    outcome = "skip_user_id_missing"
                    return@thread
                }

                val accountKey = getMd5(bduss).take(12)
                val successDayKey = "$PREF_LAST_SUCCESS_DAY_PREFIX$accountKey"
                val todayDay = currentDayStamp()
                val lastSuccessDay = statePrefs.getString(successDayKey, "").orEmpty()
                XposedCompat.log("$TAG: account=$accountKey day=$todayDay lastSuccessDay=$lastSuccessDay")
                if (!force && lastSuccessDay == todayDay) {
                    XposedCompat.log("$TAG: skip - already completed today")
                    outcome = "skip_success_today"
                    return@thread
                }

                var snapshot = getForumSnapshot(nativeBridge, userId)
                if (!snapshot.fetched) {
                    XposedCompat.log("$TAG: fetch forum sign list failed, skip")
                    outcome = "skip_forum_list_fetch_failed"
                    return@thread
                }
                val initialPendingCount = snapshot.pendingForums.size
                totalPending = initialPendingCount
                remainPending = initialPendingCount

                if (snapshot.pendingForums.isEmpty()) {
                    XposedCompat.log("$TAG: all followed forums already signed, total=${snapshot.totalForums}")
                    markSuccessDay(statePrefs, successDayKey, todayDay)
                    if (force || lastSuccessDay != todayDay) {
                        toast(context, UiText.AutoSignIn.toastAlreadyAllSigned(snapshot.totalForums))
                    }
                    outcome = "already_all_signed"
                    return@thread
                }
                clearStaleSuccessDay(statePrefs, successDayKey, todayDay, lastSuccessDay)

                var hasStartedSigning = false
                var batchRound = 0

                while (snapshot.pendingForums.isNotEmpty()) {
                    if (!snapshot.valid) {
                        XposedCompat.log("$TAG: forum list is not valid for batch sign")
                        outcome = "skip_invalid"
                        break
                    }
                    if (snapshot.showDialog) {
                        XposedCompat.log("$TAG: server requires sign dialog, notice=${snapshot.signNotice}")
                        outcome = "skip_server_dialog"
                        break
                    }
                    val signableForums = snapshot.signableForums
                    if (signableForums.isEmpty()) {
                        XposedCompat.log(
                            "$TAG: no signable forum by batch rule, pending=${snapshot.pendingForums.size} " +
                                "status=${snapshot.userStatus} level=${snapshot.batchMinLevel} " +
                                "canUseAll=${snapshot.canUseAllLevels} signMax=${snapshot.signMaxNum}"
                        )
                        outcome = "skip_no_signable_forum"
                        break
                    }

                    if (!hasStartedSigning) {
                        XposedCompat.log(
                            "$TAG: start batch sign pending=${snapshot.pendingForums.size} " +
                                "signable=${signableForums.size} total=${snapshot.totalForums}"
                        )
                        toast(context, UiText.AutoSignIn.toastStart(snapshot.pendingForums.size))
                        hasStartedSigning = true
                    }
                    val pendingBeforeBatch = snapshot.pendingForums.size
                    batchRound++
                    val result = signBatch(signableForums, nativeBridge)

                    XposedCompat.log(
                        "$TAG: batch sign finished, round=$batchRound signable=${signableForums.size}, " +
                            "pendingBefore=$pendingBeforeBatch, " +
                            "apiSuccess=${result.apiSuccess}, " +
                            "responseSigned=${result.signedCount}"
                    )

                    if (!result.apiSuccess) {
                        outcome = "batch_failed"
                        break
                    }

                    val refreshed = getForumSnapshot(nativeBridge, userId)
                    if (!refreshed.fetched) {
                        XposedCompat.log("$TAG: refresh forum sign list failed after batch sign, fallback legacy")
                        outcome = "batch_refresh_failed"
                        break
                    }
                    snapshot = refreshed
                    remainPending = snapshot.pendingForums.size
                    if (snapshot.pendingForums.isEmpty()) break
                    val batchSignedCount = (pendingBeforeBatch - snapshot.pendingForums.size).coerceAtLeast(0)
                    if (batchSignedCount <= 0) {
                        XposedCompat.log(
                            "$TAG: batch sign made no progress, pending=${snapshot.pendingForums.size}, " +
                                "responseSigned=${result.signedCount}"
                        )
                        outcome = "batch_no_progress"
                        break
                    }
                    toast(context, UiText.AutoSignIn.toastBatchDone(batchSignedCount, snapshot.pendingForums.size))
                    outcome = "batch_partial_signed"
                }

                val legacyFallbackReason = legacyFallbackReason(outcome)
                if (snapshot.pendingForums.isNotEmpty() && legacyFallbackReason != null) {
                    XposedCompat.log(
                        "$TAG: start legacy sign fallback reason=$legacyFallbackReason " +
                            "pending=${snapshot.pendingForums.size}"
                    )
                    if (!hasStartedSigning) {
                        toast(context, UiText.AutoSignIn.toastStart(snapshot.pendingForums.size))
                        hasStartedSigning = true
                    }
                    snapshot = signRemainingLegacy(snapshot, nativeBridge, userId)
                    remainPending = snapshot.pendingForums.size
                    if (snapshot.pendingForums.isNotEmpty()) {
                        outcome = "legacy_partial_signed"
                    }
                }

                if (snapshot.pendingForums.isEmpty()) {
                    remainPending = 0
                    markSuccessDay(statePrefs, successDayKey, todayDay)
                    toast(context, UiText.AutoSignIn.toastAllSigned(initialPendingCount))
                    outcome = "success_all_signed"
                } else {
                    remainPending = snapshot.pendingForums.size
                    val signedCount = (initialPendingCount - snapshot.pendingForums.size).coerceAtLeast(0)
                    XposedCompat.log("$TAG: batch sign incomplete, remain=${snapshot.pendingForums.size}")
                    toast(context, UiText.AutoSignIn.toastPartialDone(signedCount, snapshot.pendingForums.size))
                    if (outcome == "unknown") {
                        outcome = "partial_signed"
                    }
                }
            } catch (t: Throwable) {
                XposedCompat.log("$TAG: sign task exception: ${t.message}")
                XposedCompat.log(t)
                outcome = "exception"
                if (force) toast(context, UiText.AutoSignIn.toastError(t.message))
            } finally {
                val elapsedMs = System.currentTimeMillis() - startedAtMs
                val signedCount = (totalPending - remainPending).coerceAtLeast(0)
                XposedCompat.log(
                    "$TAG: finished outcome=$outcome elapsedMs=$elapsedMs " +
                        "pending=$totalPending signed=$signedCount remain=$remainPending force=$force"
                )
                running.set(false)
            }
        }
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getForumSnapshot(bridge: NativeNetworkBridge, userId: String): ForumSnapshot {
        val hybridJson = postHybridNativeProxyJson(
            bridge = bridge,
            path = FORUM_LIST_PATH,
            params = linkedMapOf(),
            needBduss = true,
            needTbs = true,
        )
        val hybridSnapshot = parseForumSnapshotJson(
            json = hybridJson,
            source = "hybrid",
        )
        if (hybridSnapshot.fetched) return hybridSnapshot
        XposedCompat.log("$TAG: hybrid native proxy forum list failed, fallback native")
        return getForumSnapshot(
            bridge = bridge,
            source = "native",
            params = linkedMapOf(
                "user_id" to userId,
            ),
            needSig = false,
            needTbs = false,
        )
    }

    private fun getForumSnapshot(
        bridge: NativeNetworkBridge,
        source: String,
        params: LinkedHashMap<String, String>,
        needSig: Boolean,
        needTbs: Boolean,
    ): ForumSnapshot {
        try {
            val json = postNativeJson(
                bridge,
                FORUM_LIST_PATH,
                params,
                needTbs = needTbs,
                needSig = needSig,
            )
            return parseForumSnapshotJson(json, source)
        } catch (t: Exception) {
            XposedCompat.log("$TAG: forum snapshot parse failed source=$source: ${t.message}")
            return emptyForumSnapshot(fetched = false)
        }
    }

    private fun parseForumSnapshotJson(json: JSONObject?, source: String): ForumSnapshot {
        try {
            if (json == null) {
                XposedCompat.log("$TAG: forum list fetch failed source=$source")
                return emptyForumSnapshot(fetched = false)
            }
            val body = responsePayloadJson(json)
            val forumInfo = json.optJSONArray("forum_info")
                ?: body.optJSONArray("forum_info")
            if (forumInfo == null) {
                XposedCompat.log(
                    "$TAG: forum list response missing forum_info source=$source: ${responseErrorText(body)} " +
                        "keys=${jsonKeySummary(body)}"
                )
                return emptyForumSnapshot(fetched = false)
            }
            val userStatus = parseUserStatus(body.optJSONObject("user"))
            val signMaxNumNew = body.optInt("sign_max_num_new", 0)
            val signMaxNumOld = body.optInt("sign_max_num", 0)
            val signMaxNum = signMaxNumNew
                .takeIf { it > 0 }
                ?: DEFAULT_SIGN_MAX_NUM
            val batchMinLevel = body.optInt("level", ORDINARY_USER_MIN_BATCH_LEVEL)
                .takeIf { it > 0 }
                ?: ORDINARY_USER_MIN_BATCH_LEVEL
            val serverCanUse = body.optString("can_use")
            val canUseAllLevels = when (serverCanUse) {
                "1" -> true
                "0" -> false
                else -> userStatus != ORDINARY_USER_STATUS
            }
            val pendingList = mutableListOf<ForumInfo>()
            for (i in 0 until forumInfo.length()) {
                val forum = forumInfo.optJSONObject(i) ?: continue
                val info = ForumInfo(
                    forumId = forum.optString("forum_id", forum.optString("id")).trim(),
                    forumName = forum.optString("forum_name").trim(),
                    userLevel = forum.optInt("user_level", 0),
                    isSigned = forum.optString("is_sign_in") == "1",
                    listIndex = i,
                )
                if (!info.isSigned) pendingList.add(info)
            }
            val eligibleList = pendingList
                .asSequence()
                .filter { it.forumId.isNotEmpty() }
                .filter { canUseAllLevels || it.userLevel >= batchMinLevel }
                .take(signMaxNum)
                .toList()
            XposedCompat.log(
                "$TAG: forum snapshot source=$source total=${forumInfo.length()} pending=${pendingList.size} " +
                    "signable=${eligibleList.size} signMax=$signMaxNum signMaxNew=$signMaxNumNew " +
                    "signMaxOld=$signMaxNumOld status=$userStatus level=$batchMinLevel " +
                    "canUseAll=$canUseAllLevels serverCanUse=$serverCanUse valid=${body.optString("valid")}"
            )
            return ForumSnapshot(
                pendingForums = pendingList,
                signableForums = eligibleList,
                totalForums = forumInfo.length(),
                valid = body.optString("valid") == "1",
                showDialog = body.optString("show_dialog") == "1",
                signNotice = body.optString("sign_notice"),
                userStatus = userStatus,
                signMaxNum = signMaxNum,
                batchMinLevel = batchMinLevel,
                canUseAllLevels = canUseAllLevels,
                fetched = true,
            )
        } catch (t: Exception) {
            XposedCompat.log("$TAG: forum snapshot parse failed source=$source: ${t.message}")
            return emptyForumSnapshot(fetched = false)
        }
    }

    private fun emptyForumSnapshot(fetched: Boolean): ForumSnapshot {
        return ForumSnapshot(
            pendingForums = emptyList(),
            signableForums = emptyList(),
            totalForums = 0,
            valid = false,
            showDialog = false,
            signNotice = "",
            userStatus = ORDINARY_USER_STATUS,
            signMaxNum = DEFAULT_SIGN_MAX_NUM,
            batchMinLevel = ORDINARY_USER_MIN_BATCH_LEVEL,
            canUseAllLevels = false,
            fetched = fetched,
        )
    }

    private fun parseUserStatus(user: JSONObject?): Int {
        val vipInfo = user?.optJSONObject("vipInfo") ?: user?.optJSONObject("vip_info")
        val vipStatus = vipInfo?.optInt("v_status", 0) ?: 0
        val expireTimeSec = vipInfo?.optLong("e_time", 0L) ?: 0L
        val nowSec = System.currentTimeMillis() / 1000L
        return if (expireTimeSec > nowSec && vipStatus >= 1) vipStatus else ORDINARY_USER_STATUS
    }

    private fun currentDayStamp(): String {
        return SimpleDateFormat(DAY_STAMP_PATTERN, Locale.getDefault()).format(Date())
    }

    private fun markSuccessDay(
        prefs: SharedPreferences,
        successDayKey: String,
        dayStamp: String,
    ) {
        if (dayStamp.length != 8) return
        prefs.edit()
            .putString(successDayKey, dayStamp)
            .apply()
    }

    private fun clearStaleSuccessDay(
        prefs: SharedPreferences,
        successDayKey: String,
        todayDay: String,
        lastSuccessDay: String,
    ) {
        if (lastSuccessDay != todayDay) return
        prefs.edit()
            .remove(successDayKey)
            .apply()
        XposedCompat.log("$TAG: cleared stale success day because pending forums remain")
    }

    private fun legacyFallbackReason(outcome: String): String? {
        return when (outcome) {
            "skip_invalid" -> "batch_invalid"
            "skip_server_dialog" -> "server_dialog"
            "skip_no_signable_forum" -> "no_batch_signable"
            "batch_failed" -> "batch_failed"
            "batch_refresh_failed" -> "batch_refresh_failed"
            "batch_no_progress" -> "batch_no_progress"
            else -> null
        }
    }

    private fun signRemainingLegacy(
        snapshot: ForumSnapshot,
        bridge: NativeNetworkBridge,
        userId: String,
    ): ForumSnapshot {
        var current = snapshot
        var round = 0
        while (round < LEGACY_MAX_RETRY && current.pendingForums.isNotEmpty()) {
            val candidates = current.pendingForums.filter { it.forumName.isNotEmpty() }
            if (candidates.isEmpty()) {
                XposedCompat.log("$TAG: legacy sign fallback has no forum name candidates")
                return current
            }
            var successCount = 0
            for ((index, forum) in candidates.withIndex()) {
                if (signSingleLegacy(forum, bridge)) successCount++
                if (index < candidates.lastIndex && !sleepSafely(LEGACY_SIGN_DELAY_MS)) return current
            }
            XposedCompat.log(
                "$TAG: legacy round ${round + 1} finished, pending=${current.pendingForums.size}, " +
                    "candidates=${candidates.size}, success=$successCount"
            )
            val refreshed = getForumSnapshot(bridge, userId)
            if (!refreshed.fetched) {
                XposedCompat.log("$TAG: refresh forum sign list failed after legacy round ${round + 1}, stop")
                return current
            }
            current = refreshed
            if (current.pendingForums.isEmpty()) return current
            round++
            if (round < LEGACY_MAX_RETRY && !sleepSafely(RETRY_DELAY_MS)) return current
        }
        return current
    }

    private fun signSingleLegacy(forum: ForumInfo, bridge: NativeNetworkBridge): Boolean {
        return try {
            val params = linkedMapOf(
                "kw" to forum.forumName,
            )
            if (forum.forumId.isNotEmpty()) {
                params["fid"] = forum.forumId
            }
            val json = postNativeJson(bridge, SIGN_PATH, params, needTbs = true, needSig = true)
                ?: return false
            val success = isSingleSignSuccess(json)
            if (!success) {
                XposedCompat.log(
                    "$TAG: legacy sign failed, fid=${forum.forumId}, error=${responseErrorText(json)}"
                )
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    private fun isSingleSignSuccess(json: JSONObject): Boolean {
        val userInfo = json.optJSONObject("user_info")
        if (userInfo != null && userInfo.optInt("is_sign_in", 0) != 0) return true
        return isApiSuccess(json)
    }

    private fun signBatch(forums: List<ForumInfo>, bridge: NativeNetworkBridge): BatchSignResult {
        return try {
            val forumIds = forums.joinToString(",") { it.forumId }
            val params = linkedMapOf(
                "forum_ids" to forumIds,
            )
            val json = postHybridNativeProxyJson(
                bridge,
                MSIGN_PATH,
                params,
                needBduss = true,
                needTbs = true,
            )
                ?: return BatchSignResult(apiSuccess = false, responseCount = 0, signedCount = 0)
            val body = responsePayloadJson(json)
            val success = isApiSuccess(body) || isApiSuccess(json)
            val info = body.optJSONArray("info") ?: json.optJSONArray("info")
            val responseCount = info?.length() ?: 0
            var signedCount = 0
            if (info != null) {
                for (i in 0 until info.length()) {
                    val item = info.optJSONObject(i) ?: continue
                    if (item.optInt("signed", 0) != 0) signedCount++
                }
            }
            XposedCompat.log(
                "$TAG: msign result request=${forums.size} apiSuccess=$success " +
                    "info=$responseCount signed=$signedCount"
            )
            if (!success) {
                XposedCompat.log("$TAG: msign failed, ids=${forums.size}, error=${responseErrorText(body)}")
            } else if (responseCount > 0 && signedCount < forums.size) {
                XposedCompat.log(
                    "$TAG: msign partial, request=${forums.size}, info=$responseCount, signed=$signedCount"
                )
                logMsignPartialDetail(forums, info, body)
            }
            BatchSignResult(apiSuccess = success, responseCount = responseCount, signedCount = signedCount)
        } catch (t: Exception) {
            XposedCompat.log("$TAG: msign exception: ${t.message}")
            BatchSignResult(apiSuccess = false, responseCount = 0, signedCount = 0)
        }
    }

    private fun logMsignPartialDetail(
        requestForums: List<ForumInfo>,
        info: org.json.JSONArray?,
        body: JSONObject,
    ) {
        if (info == null) return
        val responseIds = LinkedHashSet<String>()
        val returnedUnsigned = mutableListOf<String>()
        var sampleKeys = ""
        for (i in 0 until info.length()) {
            val item = info.optJSONObject(i) ?: continue
            if (sampleKeys.isEmpty()) sampleKeys = jsonKeySummary(item)
            val forumId = msignInfoForumId(item)
            if (forumId.isNotEmpty()) responseIds.add(forumId)
            if (!isMsignInfoSigned(item)) {
                returnedUnsigned.add(msignInfoSummary(item))
            }
        }
        val missingForums = if (responseIds.isEmpty()) {
            emptyList()
        } else {
            requestForums.filter { it.forumId !in responseIds }
        }
        XposedCompat.log(
            "$TAG: msign partial detail missing=${missingForums.size} " +
                "missingSample=${forumSummary(missingForums)} " +
                "returnedUnsigned=${returnedUnsigned.size} " +
                "unsignedSample=${returnedUnsigned.take(6).joinToString(";").ifEmpty { "-" }} " +
                "infoKeys=${sampleKeys.ifEmpty { "-" }} error=${responseErrorText(body)}"
        )
    }

    private fun msignInfoForumId(item: JSONObject): String {
        return item.optString("forum_id")
            .ifEmpty { item.optString("fid") }
            .ifEmpty { item.optString("forumId") }
            .ifEmpty { item.optString("id") }
            .trim()
    }

    private fun isMsignInfoSigned(item: JSONObject): Boolean {
        val signed = item.optString("signed")
        if (signed.isNotEmpty()) return signed != "0"
        val isSignIn = item.optString("is_sign_in")
        if (isSignIn.isNotEmpty()) return isSignIn != "0"
        val signStatus = item.optString("sign_status")
        if (signStatus.isNotEmpty()) return signStatus != "0"
        return false
    }

    private fun msignInfoSummary(item: JSONObject): String {
        val forumId = msignInfoForumId(item).takeLast(6).ifEmpty { "-" }
        val errno = item.optString("errno")
            .ifEmpty { item.optString("error_code") }
            .ifEmpty { item.optString("code") }
            .ifEmpty { "-" }
        val errmsg = item.optString("errmsg")
            .ifEmpty { item.optString("error_msg") }
            .ifEmpty { item.optString("msg") }
            .take(24)
            .ifEmpty { "-" }
        return "$forumId:$errno:$errmsg"
    }

    private fun forumSummary(forums: List<ForumInfo>): String {
        if (forums.isEmpty()) return "-"
        return forums.take(8).joinToString(";") { forum ->
            val name = forum.forumName.take(12).ifEmpty { "-" }
            val forumId = forum.forumId.takeLast(6).ifEmpty { "-" }
            "#${forum.listIndex}:$name:L${forum.userLevel}:$forumId"
        }
    }

    private fun isApiSuccess(json: JSONObject): Boolean {
        val error = json.optJSONObject("error")
        if (error != null) {
            val errno = error.optString("errno")
            if (errno.isNotEmpty()) return errno == "0"
        }
        val errno = json.optString("errno")
        if (errno.isNotEmpty()) return errno == "0"
        val errorCode = json.optString("error_code")
        if (errorCode.isNotEmpty()) return errorCode == "0"
        return false
    }

    private fun responseErrorText(json: JSONObject): String {
        val error = json.optJSONObject("error")
        if (error != null) {
            val errno = error.optString("errno")
            val errmsg = error.optString("errmsg", error.optString("usermsg"))
            return "errno=$errno errmsg=$errmsg"
        }
        return "errno=${json.optString("errno")} error_code=${json.optString("error_code")} " +
            "errmsg=${json.optString("error_msg", json.optString("errmsg"))}"
    }

    private fun jsonKeySummary(json: JSONObject): String {
        val keys = json.keys()
        val result = mutableListOf<String>()
        while (keys.hasNext() && result.size < 12) {
            result.add(keys.next())
        }
        return result.joinToString(",").ifEmpty { "<none>" }
    }

    private fun responsePayloadJson(json: JSONObject): JSONObject {
        return json.optJSONObject("data") ?: json
    }

    private fun responsePreview(raw: String): String {
        val compact = raw.replace('\n', ' ').replace('\r', ' ').trim()
        val preview = if (compact.length > RESPONSE_PREVIEW_LIMIT) {
            compact.take(RESPONSE_PREVIEW_LIMIT) + "..."
        } else {
            compact
        }
        return preview
            .replace(Regex("(?i)(BDUSS|STOKEN|tbs)=([^&;\\s\"]+)")) {
                "${it.groupValues[1]}=<redacted>"
            }
            .replace(Regex("(?i)\"(BDUSS|STOKEN|tbs)\"\\s*:\\s*\"[^\"]*\"")) {
                "\"${it.groupValues[1]}\":\"<redacted>\""
            }
    }

    private fun getMd5(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            var hex = BigInteger(1, bytes).toString(16)
            while (hex.length < 32) hex = "0$hex"
            hex
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveNativeNetworkBridge(context: Context): NativeNetworkBridge? {
        val loader = context.classLoader ?: return null
        synchronized(nativeBridgeCache) {
            nativeBridgeCache[loader]?.let { return it }
        }
        return try {
            val netClass = Class.forName(StableTiebaHookPoints.NETWORK_CLASS, false, loader)
            val tbConfigClass = Class.forName(StableTiebaHookPoints.TB_CONFIG_CLASS, false, loader)
            val coreAppClass = Class.forName(StableTiebaHookPoints.TBADK_CORE_APPLICATION_CLASS, false, loader)
            val hybridNativeProxy = HookSymbolResolver.resolveAutoSignInHybridNativeProxySymbols(loader)
            val bridge = NativeNetworkBridge(
                netCtor = netClass.getDeclaredConstructor(String::class.java).apply { isAccessible = true },
                addPostDataMethod = netClass.getDeclaredMethod(
                    StableTiebaHookPoints.METHOD_ADD_POST_DATA,
                    String::class.java,
                    String::class.java,
                ).apply { isAccessible = true },
                postNetDataMethod = netClass.getDeclaredMethod(StableTiebaHookPoints.METHOD_POST_NET_DATA)
                    .apply { isAccessible = true },
                setNeedTbsMethod = netClass.getDeclaredMethod(
                    StableTiebaHookPoints.METHOD_SET_NEED_TBS,
                    Boolean::class.javaPrimitiveType,
                ).apply { isAccessible = true },
                setNeedSigMethod = netClass.getDeclaredMethod(
                    StableTiebaHookPoints.METHOD_SET_NEED_SIG,
                    Boolean::class.javaPrimitiveType,
                ).apply { isAccessible = true },
                serverAddressField = tbConfigClass.getDeclaredField(StableTiebaHookPoints.FIELD_SERVER_ADDRESS)
                    .apply { isAccessible = true },
                getCurrentAccountMethod = coreAppClass.getDeclaredMethod(StableTiebaHookPoints.METHOD_GET_CURRENT_ACCOUNT)
                    .apply { isAccessible = true },
                hybridNativeProxy = hybridNativeProxy,
            )
            synchronized(nativeBridgeCache) {
                nativeBridgeCache[loader] = bridge
            }
            bridge
        } catch (t: Throwable) {
            XposedCompat.log("$TAG: resolve native network bridge failed: ${t.message}")
            null
        }
    }

    private fun getCurrentAccountId(bridge: NativeNetworkBridge): String {
        return try {
            (bridge.getCurrentAccountMethod.invoke(null) as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun postHybridNativeProxyJson(
        bridge: NativeNetworkBridge,
        path: String,
        params: LinkedHashMap<String, String>,
        needBduss: Boolean,
        needTbs: Boolean,
    ): JSONObject? {
        val proxy = bridge.hybridNativeProxy ?: run {
            XposedCompat.log("$TAG: hybrid native proxy missing path=$path")
            return null
        }
        return try {
            val url = buildUrl(HYBRID_API_HOST, path)
            val task = proxy.taskConstructor.newInstance(
                url,
                "post",
                if (needBduss) 1 else 0,
                if (needTbs) 1 else 0,
                System.currentTimeMillis(),
                HashMap(params),
                null,
            )
            val rawTaskParams: Array<Any> = emptyArray()
            val resultMap = proxy.doInBackgroundMethod.invoke(task, rawTaskParams as Any) as? Map<*, *>
            val raw = resultMap?.get("result") as? String
            if (raw.isNullOrBlank()) {
                XposedCompat.log(
                    "$TAG: hybrid native proxy empty response path=$path " +
                        "error=${hybridProxyResultErrorText(resultMap)}"
                )
                return null
            }
            try {
                JSONObject(raw)
            } catch (t: Throwable) {
                XposedCompat.log(
                    "$TAG: hybrid native proxy invalid json path=$path err=${t.message} " +
                        "preview=${responsePreview(raw)}"
                )
                null
            }
        } catch (t: Throwable) {
            XposedCompat.log("$TAG: hybrid native proxy failed path=$path err=${t.message}")
            null
        }
    }

    private fun postNativeJson(
        bridge: NativeNetworkBridge,
        path: String,
        params: LinkedHashMap<String, String>,
        needTbs: Boolean,
        needSig: Boolean,
        hostOverride: String? = null,
    ): JSONObject? {
        return try {
            val baseAddress = hostOverride
                ?: (bridge.serverAddressField.get(null) as? String)?.takeIf { it.isNotBlank() }
            if (baseAddress == null) {
                XposedCompat.log("$TAG: native post missing base address path=$path")
                return null
            }
            val url = buildUrl(baseAddress, path)
            val net = bridge.netCtor.newInstance(url)
            params.forEach { (key, value) ->
                bridge.addPostDataMethod.invoke(net, key, value)
            }
            bridge.setNeedTbsMethod.invoke(net, needTbs)
            bridge.setNeedSigMethod.invoke(net, needSig)
            val raw = bridge.postNetDataMethod.invoke(net) as? String
            if (raw.isNullOrBlank()) {
                XposedCompat.log("$TAG: native post empty response path=$path")
                null
            } else {
                try {
                    JSONObject(raw)
                } catch (t: Throwable) {
                    XposedCompat.log(
                        "$TAG: native post invalid json path=$path err=${t.message} " +
                            "preview=${responsePreview(raw)}"
                    )
                    null
                }
            }
        } catch (t: Throwable) {
            XposedCompat.log("$TAG: native post failed path=$path err=${t.message}")
            null
        }
    }

    private fun buildUrl(baseAddress: String, path: String): String {
        val normalizedPath = path.removePrefix("/")
        return if (baseAddress.endsWith("/")) {
            baseAddress + normalizedPath
        } else {
            "$baseAddress/$normalizedPath"
        }
    }

    private fun hybridProxyResultErrorText(resultMap: Map<*, *>?): String {
        if (resultMap == null) return "result=<null>"
        val errorCode = resultMap["error_code"]?.toString().orEmpty()
        val errorMsg = resultMap["error_msg"]?.toString().orEmpty()
        val userInfo = resultMap["user_info"]?.toString().orEmpty()
        return "error_code=$errorCode error_msg=$errorMsg user_info=$userInfo"
    }

    private fun readLoginCookie(force: Boolean): LoginCookie? {
        val attempts = if (force) 1 else LOGIN_COOKIE_ATTEMPTS
        repeat(attempts) { index ->
            val cookie = CookieManager.getInstance().getCookie(COOKIE_URL) ?: ""
            val bduss = extractBduss(cookie)
            if (bduss.isNotEmpty()) return LoginCookie(bduss)
            if (index < attempts - 1) {
                XposedCompat.log("$TAG: BDUSS missing, retry cookie after ${LOGIN_COOKIE_RETRY_DELAY_MS}ms")
                if (!sleepSafely(LOGIN_COOKIE_RETRY_DELAY_MS)) return null
            }
        }
        return null
    }

    private fun extractBduss(cookie: String): String {
        return cookie
            .split(";")
            .map { it.trim() }
            .find { it.startsWith("BDUSS=") }
            ?.removePrefix("BDUSS=")
            ?: ""
    }

    private fun sleepSafely(durationMs: Long): Boolean {
        return try {
            Thread.sleep(durationMs)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}
