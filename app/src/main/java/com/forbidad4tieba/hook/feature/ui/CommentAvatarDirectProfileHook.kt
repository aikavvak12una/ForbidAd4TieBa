package com.forbidad4tieba.hook.feature.ui

import android.content.Context
import android.net.Uri
import android.view.View
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.GlobalDirectProfileSymbols
import org.json.JSONObject
import java.net.URLDecoder

/**
 * 头像直达主页：在宿主消息创建处把吧友名片配置替换为个人主页配置，
 * 并把吧页面信息流的 H5 名片路由改为同一条内部消息路径。
 */
object CommentAvatarDirectProfileHook {
    private const val TAG = "[CommentAvatarDirectProfileHook]"
    private const val PERSON_INFO_MSG_ID = 2002003
    private const val PERSON_PROFILE_MSG_ID = 2002001
    private const val CUSTOM_BUSINESS_CARD_PATH = "hybrid-main-frs/customBusinessCard"
    private const val PORTAL_ROUTE_PREFIX = "tiebaapp://router/portal"
    private const val USER_CENTER_ROUTE_PREFIX = "com.baidu.tieba://unidispatch/usercenter?portrait="

    @Volatile
    private var hooked = false

    @Volatile
    private var runtimeDisabled = false

    @Volatile
    private var runtimeTargets: GlobalDirectProfileSymbols? = null

    private val directHeaderClickListener = View.OnClickListener { view ->
        val targets = runtimeTargets ?: return@OnClickListener
        val userId = runCatching {
            targets.clickableHeaderGetUserIdMethod.invoke(view) as? String
        }.getOrNull()
        openUserId(view.context, userId)
    }

    internal fun hook(targets: GlobalDirectProfileSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked(targets)) return
        try {
            mod.hook(targets.clickableHeaderSetDataMethod).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.isCommentAvatarDirectProfileEnabled) {
                    (chain.thisObject as? View)?.setOnClickListener(directHeaderClickListener)
                }
                result
            }
            targets.urlManagerDealOneLinkMethods.forEach { method ->
                mod.hook(method).intercept { chain ->
                    if (!ConfigManager.isCommentAvatarDirectProfileEnabled) {
                        return@intercept chain.proceed()
                    }
                    val portrait = chain.args.firstNotNullOfOrNull(::extractBusinessCardPortrait)
                        ?: return@intercept chain.proceed()
                    if (openPortrait(portrait)) {
                        XposedCompat.logD { "$TAG handled customBusinessCard internally" }
                        return@intercept handledResult(method.returnType)
                    }
                    chain.proceed()
                }
            }
            mod.hook(targets.customMessageConstructor).intercept { chain ->
                if (!ConfigManager.isCommentAvatarDirectProfileEnabled || runtimeDisabled) {
                    return@intercept chain.proceed()
                }
                val replacementArgs = try {
                    rewritePersonInfoMessage(targets, chain.args)
                } catch (t: Throwable) {
                    runtimeDisabled = true
                    XposedCompat.log("$TAG card message rewrite failed, disabled: ${t.message}")
                    XposedCompat.log(t)
                    null
                }
                if (replacementArgs == null) chain.proceed() else chain.proceed(replacementArgs)
            }
            XposedCompat.log(
                "$TAG hook INSTALLED: header=ClickableHeaderImageView.setData " +
                    "message=CustomMessage(int,Object) " +
                    "routes=${targets.urlManagerDealOneLinkMethods.size}",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun openUserId(context: Context?, rawUserId: String?): Boolean {
        if (!ConfigManager.isCommentAvatarDirectProfileEnabled || runtimeDisabled) return false
        val targets = runtimeTargets ?: return false
        val userId = parsePositiveUserId(rawUserId) ?: return false
        val targetContext = context ?: return false
        return try {
            val isSelf = targets.currentAccountMethod?.let { method ->
                runCatching { method.invoke(null) as? String }.getOrNull() == userId.toString()
            } ?: false
            val config = targets.personPolymericConfigConstructor.newInstance(targetContext)
            targets.personPolymericCreateNormalConfigMethod.invoke(config, userId, isSelf, false)
            sendProfileMessage(targets, config)
            XposedCompat.logD { "$TAG direct profile jump: uid=$userId" }
            true
        } catch (t: Throwable) {
            runtimeDisabled = true
            XposedCompat.log("$TAG profile jump FAILED, disabled for this process: ${t.message}")
            XposedCompat.log(t)
            false
        }
    }

    private fun rewritePersonInfoMessage(
        targets: GlobalDirectProfileSymbols,
        args: List<Any?>,
    ): Array<Any?>? {
        if ((args.firstOrNull() as? Int) != PERSON_INFO_MSG_ID) return null
        val info = args.getOrNull(1)?.takeIf { targets.personInfoConfigClass.isInstance(it) } ?: return null
        val intent = runCatching {
            targets.personInfoGetIntentMethod.invoke(info) as? android.content.Intent
        }.getOrNull() ?: return null
        val userId = intent.getStringExtra("user_id") ?: return null
        val context = runCatching {
            targets.personInfoGetContextMethod.invoke(info) as? Context
        }.getOrNull() ?: return null
        val userIdLong = parsePositiveUserId(userId) ?: return null
        val isSelf = targets.currentAccountMethod?.let { method ->
            runCatching { method.invoke(null) as? String }.getOrNull() == userIdLong.toString()
        } ?: false
        val config = targets.personPolymericConfigConstructor.newInstance(context)
        targets.personPolymericCreateNormalConfigMethod.invoke(config, userIdLong, isSelf, false)
        copySourceExtras(targets, intent, config)
        XposedCompat.logD { "$TAG replaced person card message: uid=$userIdLong" }
        return arrayOf(PERSON_PROFILE_MSG_ID, config)
    }

    private fun sendProfileMessage(targets: GlobalDirectProfileSymbols, config: Any) {
        val message = targets.customMessageConstructor.newInstance(PERSON_PROFILE_MSG_ID, config)
        val manager = targets.messageManagerGetInstanceMethod.invoke(null)
            ?: error("MessageManager.getInstance returned null")
        targets.messageManagerSendMethod.invoke(manager, message)
    }

    private fun openPortrait(rawPortrait: String): Boolean {
        val targets = runtimeTargets ?: return false
        return try {
            val context = targets.applicationGetInstMethod.invoke(null) as? Context ?: return false
            val config = targets.personPolymericConfigConstructor.newInstance(context)
            targets.personPolymericSetUriMethod.invoke(
                config,
                Uri.parse(USER_CENTER_ROUTE_PREFIX + Uri.encode(rawPortrait)),
            )
            sendProfileMessage(targets, config)
            true
        } catch (t: Throwable) {
            XposedCompat.logW("$TAG portrait route failed: ${t.message}")
            false
        }
    }

    private fun copySourceExtras(
        targets: GlobalDirectProfileSymbols,
        source: android.content.Intent,
        config: Any,
    ) {
        val targetIntent = runCatching {
            targets.personPolymericGetIntentMethod.invoke(config) as? android.content.Intent
        }.getOrNull() ?: return
        listOf("thread_id", "nid", "video_person_from", "portrait").forEach { key ->
            source.getStringExtra(key)?.let { value -> targetIntent.putExtra(key, value) }
        }
        if (source.hasExtra("is_video_thread")) {
            targetIntent.putExtra("is_video_thread", source.getBooleanExtra("is_video_thread", false))
        }
    }

    private fun extractBusinessCardPortrait(argument: Any?): String? {
        return when (argument) {
            is String -> extractBusinessCardPortraitFromRoute(argument)
            is Array<*> -> argument.asSequence()
                .filterIsInstance<String>()
                .firstNotNullOfOrNull(::extractBusinessCardPortraitFromRoute)
            else -> null
        }
    }

    private fun extractBusinessCardPortraitFromRoute(rawRoute: String): String? {
        val trimmed = rawRoute.trim()
        if (!trimmed.startsWith(PORTAL_ROUTE_PREFIX, ignoreCase = true)) return null
        val decodedRoute = runCatching { URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }.getOrNull()
            ?: trimmed
        if (!decodedRoute.contains(CUSTOM_BUSINESS_CARD_PATH, ignoreCase = true)) return null
        val paramsValue = queryParameter(trimmed, "params") ?: return null
        val paramsJson = runCatching { URLDecoder.decode(paramsValue, Charsets.UTF_8.name()) }.getOrNull()
            ?: return null
        val pageParams = runCatching { JSONObject(paramsJson).optJSONObject("pageParams") }.getOrNull()
            ?: return null
        if (!pageParams.optString("url").contains(CUSTOM_BUSINESS_CARD_PATH, ignoreCase = true)) return null
        return pageParams.optJSONObject("initData")
            ?.optString("friendPortrait")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun queryParameter(route: String, key: String): String? {
        val query = route.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0 || part.substring(0, separator) != key) null else part.substring(separator + 1)
        }
    }

    private fun handledResult(returnType: Class<*>): Any? {
        return when (returnType) {
            Boolean::class.javaPrimitiveType -> true
            Int::class.javaPrimitiveType -> 1
            else -> null
        }
    }

    private fun parsePositiveUserId(rawUserId: String?): Long? {
        val normalized = rawUserId?.trim()?.takeIf { value ->
            value.isNotEmpty() && value.all(Char::isDigit)
        } ?: return null
        return normalized.toLongOrNull()?.takeIf { it > 0L }
    }

    internal fun extractBusinessCardPortraitForTest(rawRoute: String): String? =
        extractBusinessCardPortraitFromRoute(rawRoute)

    internal fun parsePositiveUserIdForTest(rawUserId: String?): Long? = parsePositiveUserId(rawUserId)

    private fun tryMarkHooked(targets: GlobalDirectProfileSymbols): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            runtimeDisabled = false
            runtimeTargets = targets
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) {
            hooked = false
            runtimeTargets = null
        }
    }
}
