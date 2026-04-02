package com.forbidad4tieba.hook.feature.signin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.widget.Toast
import com.forbidad4tieba.hook.config.ConfigManager
import de.robv.android.xposed.XposedBridge
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 后台自动签到管理器
 * 通过免配置 Cookie (截获宿主) 实现后台无感连续签到
 */
object AutoSignInManager {
    private const val TAG = "TBHook-AutoSignIn"
    private const val LIKE_URL = "https://tieba.baidu.com/mo/q/newmoindex"
    private const val TBS_URL = "http://tieba.baidu.com/dc/common/tbs"
    private const val SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun tryAutoSignIn(context: Context, force: Boolean = false) {
        val prefs = ConfigManager.getPrefs(context)
        // 默认开启，用户可以去设置里关掉
        if (!prefs.getBoolean("enable_auto_sign_in", true) && !force) return

        thread(isDaemon = true, name = "tbhook-auto-signin") {
            try {
                // 1. 获取内部 Cookie 和 BDUSS 凭证
                val cookie = CookieManager.getInstance().getCookie("https://tieba.baidu.com") ?: ""
                val bduss = extractBduss(cookie)
                if (bduss.isEmpty()) {
                    XposedBridge.log("$TAG: Cookie不包含BDUSS，取消签到。")
                    if (force) toast(context, "自动签到失败：未找到账户凭证(BDUSS)，请先登录贴吧")
                    return@thread
                }

                // 2. 账号隔离：使用 BDUSS 的 hash 作为持久化记录的 key，保证多账号切换独立触发签到
                val accountKey = getMd5(bduss).take(12)
                val prefKey = "last_sign_date_$accountKey"
                
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastSignDate = prefs.getString(prefKey, "")

                // 每日节流：强制执行（用于测试）或今日未签到才放行
                if (today == lastSignDate && !force) return@thread

                // 3. 获取防刷票据 tbs
                val tbs = getTbs(cookie)
                if (tbs.isEmpty()) {
                    XposedBridge.log("$TAG: 获取tbs鉴权失败。")
                    if (force) toast(context, "自动签到失败：无法获取tbs鉴权参数")
                    return@thread
                }

                // 4. 获取关注贴吧列表，筛选未签到的
                val (followList, totalLiked) = getFollow(cookie)
                if (followList.isEmpty()) {
                    XposedBridge.log("$TAG: 已经全部签到过了，无待签任务。总关注数: $totalLiked")
                    // 如果全部签过了也标记今日已签完
                    prefs.edit().putString(prefKey, today).apply()
                    if (force) toast(context, "自动签到：当前 $totalLiked 个贴吧已全部签到")
                    return@thread
                }

                var successCount = 0
                val pendingCount = followList.size

                // 4. 开始单节点签到
                for (tieba in followList) {
                    val success = signSingle(tieba, tbs, cookie)
                    if (success) successCount++
                    // 必须加延迟防止风控（随机休眠 300~500 毫秒）
                    Thread.sleep((300..500).random().toLong())
                }

                XposedBridge.log("$TAG: 签到尝试完毕。共尝试补签 $pendingCount 个吧，成功 $successCount 个")
                
                // 5. 验证是否全部签到完成
                val (leftoverList, _) = getFollow(cookie)
                if (leftoverList.isEmpty()) {
                    // 只有验证完成所有可签到的吧，才能将完成记录写入日志
                    prefs.edit().putString(prefKey, today).apply()
                    if (successCount > 0) {
                        toast(context, "成功签到${successCount}个吧")
                    } else if (force) {
                        toast(context, "自动签到：已无待签吧")
                    }
                } else {
                    XposedBridge.log("$TAG: 尚有 ${leftoverList.size} 个吧未签到，不写入完成日志。")
                    if (force) {
                        toast(context, "完成部分签到，剩余 ${leftoverList.size} 个未签")
                    }
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: 签到线程异常: ${t.message}")
                if (force) toast(context, "自动签到异常：${t.message}")
            }
        }
    }

    private fun toast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getTbs(cookie: String): String {
        return try {
            val json = getRequest(TBS_URL, cookie) ?: return ""
            if (json.optString("is_login") == "1") json.optString("tbs") else ""
        } catch (_: Exception) { "" }
    }

    private fun getFollow(cookie: String): Pair<List<String>, Int> {
        val pendingList = mutableListOf<String>()
        var totalCount = 0
        try {
            val json = getRequest(LIKE_URL, cookie) ?: return Pair(pendingList, totalCount)
            val likeForum = json.optJSONObject("data")?.optJSONArray("like_forum") ?: return Pair(pendingList, totalCount)
            totalCount = likeForum.length()
            for (i in 0 until likeForum.length()) {
                val forum = likeForum.optJSONObject(i) ?: continue
                if (forum.optString("is_sign") == "0") {
                    val name = forum.optString("forum_name")
                    if (!name.isNullOrEmpty()) pendingList.add(name)
                }
            }
        } catch (_: Exception) {}
        return Pair(pendingList, totalCount)
    }

    private fun signSingle(tiebaName: String, tbs: String, cookie: String): Boolean {
        return try {
            val signStr = "kw=${tiebaName}tbs=${tbs}tiebaclient!!!"
            val md5Sign = getMd5(signStr)
            
            // 注意 body 中的 kw 因可能含中文需要 URL Encode，此处我们先简单替换下 `+`（参考历史脚本，这里转义一下避免报错即可）
            // 更标准的是使用 java.net.URLEncoder.encode(tiebaName, "UTF-8")
            val encodedKw = java.net.URLEncoder.encode(tiebaName, "UTF-8")
            val body = "kw=${encodedKw}&tbs=${tbs}&sign=${md5Sign}"
            
            val json = postRequest(SIGN_URL, body, cookie) ?: return false
            json.optString("error_code") == "0"
        } catch (_: Exception) { false }
    }

    private fun getMd5(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            var hex = java.math.BigInteger(1, bytes).toString(16)
            while (hex.length < 32) hex = "0$hex" // 前缀补零
            hex
        } catch (e: Exception) { "" }
    }

    private fun getRequest(urlString: String, cookie: String): JSONObject? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36")
        return if (conn.responseCode in 200..399) {
            JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).readText())
        } else null
    }

    private fun postRequest(urlString: String, body: String, cookie: String): JSONObject? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return if (conn.responseCode in 200..399) {
            JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).readText())
        } else null
    }

    private fun extractBduss(cookie: String): String {
        return cookie.split(";").map { it.trim() }.find { it.startsWith("BDUSS=") }?.removePrefix("BDUSS=") ?: ""
    }
}
