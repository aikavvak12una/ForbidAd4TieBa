package com.forbidad4tieba.hook.feature.ad

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal enum class CloudDialogMode {
    BLACKLIST,
    WHITELIST,
}

internal data class CloudDialogPolicyInput(
    val mode: CloudDialogMode,
    val dialogName: String?,
    val cachedUrl: String?,
    val strategyTypes: List<String>,
    val fields: Map<String, String>,
    val contextTime: String? = null,
)

internal data class CloudDialogDecision(
    val block: Boolean,
    val reason: String,
    val matchedRule: String? = null,
)

internal object CloudDialogPolicy {
    private const val MAX_URL_CHARS = 8_192
    private const val MAX_QUERY_PAIRS = 64
    private const val MAX_FIELD_ENTRIES = 64
    private const val MAX_KEY_CHARS = 128
    private const val MAX_QUERY_VALUE_CHARS = 8_192
    private const val MAX_FIELD_VALUE_CHARS = 16_384
    private const val MAX_DYNAMIC_JSON_CHARS = 16_384
    private const val MAX_JSON_DEPTH = 64
    private const val MAX_STRATEGIES = 32
    private const val MAX_STRATEGY_CHARS = 128

    private const val COMMERCIAL_PUSH = "NA_COMMERCIAL_PUSH_SIGN_POP_STRATEGY"
    private const val LIGHTHOUSE = "NA_FRS_LIGHTHOUSE_GUIDE_STRATEGY"
    private const val EASTER_EGG = "NA_EASTER_EGG_STRATEGY"

    private val internalStrategies = setOf(
        "UNIQUE_STRATEGY",
        "PRECHECK_RENDER_STRATEGY",
        "UN_UNIQUE_STRATEGY",
    )

    private val knownContextTimes = setOf(
        "default",
        "defalut",
        "main_page_recommend",
        "main_page_common_tab",
        "main_page_live",
        "main_page_person_info",
        "main_page_load_finish",
        "splash_close",
        "sync_finish",
        "frs_page",
        "frs_load_finish",
        "pb_page",
        "pb_load_finish",
    )

    private val nativeTransportParameters = setOf(
        "client_preload",
        "time",
    )

    private val webTransportParameters = setOf(
        "client_preload",
        "customfullscreen",
        "loadingSignal",
        "nonavigationbar",
        "noshare",
        "skin",
        "time",
    )

    private data class NativeRule(
        val id: String,
        val dialogName: String,
        val rawUrl: String,
        val requiredStrategies: Set<String>,
        val allowedParameters: Set<String> = nativeTransportParameters,
    )

    private data class WebRule(
        val id: String,
        val dialogName: String,
        val scheme: String,
        val host: String,
        val path: String,
        val requiredStrategies: Set<String>,
        val requiredParameters: Map<String, String> = emptyMap(),
        val allowedParameters: Set<String> = webTransportParameters,
    )

    private data class ParsedDialog(
        val uri: URI,
        val parameters: Map<String, String>,
    )

    private val whiteNativeRules = listOf(
        NativeRule(
            "white:1",
            "frsWishingLights",
            "dialoginternal://frsWishingLights",
            setOf("NA_FRS_WISHING_LIGHTS_STRATEGY"),
        ),
        NativeRule(
            "white:3",
            "adDownloadConfirmDialog",
            "dialoginternal://adDownloadConfirmDialog",
            setOf("NEW_FREQUENCE_STRATEGY"),
            nativeTransportParameters + setOf("dialogUrl", "dialog_data"),
        ),
        NativeRule(
            "white:10",
            "frsCopyLinkGuide",
            "dialoginternal://frsCopyLinkGuide",
            setOf("NA_FRS_COPY_LINK_GUIDE_STRATEGY"),
        ),
        NativeRule(
            "white:12",
            "frsNewUserGuide",
            "dialoginternal://frsNewUserGuide",
            setOf("NA_FRS_NEWUSER_STRATEGY"),
        ),
        NativeRule(
            "white:13",
            "frsTopicInspirationGuide",
            "dialoginternal://frsTopicInspirationGuide",
            setOf("NA_FRS_TOPIC_INSPIRATION_GUIDE_STRATEGY"),
        ),
        NativeRule(
            "white:15",
            "userIcon",
            "dialoginternal://userIcon",
            setOf("NA_USER_ICON_STRATEGY"),
        ),
        NativeRule(
            "white:16",
            "userGrowth",
            "dialoginternal://userGrowth",
            setOf("NA_USER_GROWTH_STRATEGY"),
        ),
        NativeRule(
            "white:17",
            "newGod",
            "dialoginternal://newGod",
            setOf("NA_NEW_GOD_STRATEGY"),
        ),
        NativeRule(
            "white:21",
            "frsExam",
            "dialoginternal://frsExam",
            setOf("NA_FRS_EXAM_STRATEGY"),
        ),
        NativeRule(
            "white:22",
            "frsGuide",
            "dialoginternal://frsGuide",
            setOf("NA_FRS_GUIDE_STRATEGY"),
        ),
        NativeRule(
            "white:23",
            "frsGroupChatGuide",
            "dialoginternal://frsGroupChatGuide",
            setOf("NA_FRS_GROUP_CHAT_GUIDE_STRATEGY"),
        ),
        NativeRule(
            "white:24",
            "frsUpgrade",
            "dialoginternal://frsUpgrade",
            setOf("NA_FRS_UPGRADE_STRATEGY"),
        ),
        NativeRule(
            "white:25",
            "updateDialog",
            "dialoginternal://updateDialog",
            setOf("NA_UPDATE_STRATEGY"),
        ),
        NativeRule(
            "white:26",
            "lcUpdateDialog",
            "dialoginternal://lcUpdateDialog",
            setOf("FREQUENCE_STRATEGY"),
            nativeTransportParameters + setOf(
                "dialogUrl",
                "is_force_update",
                "status",
                "reverson",
                "content_url",
                "version_code",
                "version_name",
                "download_url",
                "change_log",
                "size",
                "package_name",
                "sign",
                "prod_line",
                "sign_md5",
                "apk_md5",
                "patch_download_url",
                "patch_size",
                "icon_url",
                "sname",
                "update_time",
                "apk_md5_rsa",
            ),
        ),
        NativeRule(
            "white:30",
            "frsFollowGuide",
            "dialoginternal://frsFollowGuide",
            setOf("NA_FRS_FOLLOW_GUIDE_STRATEGY"),
        ),
    ).associateBy(NativeRule::dialogName)

    private val whiteWebRules = buildList {
        add(
            WebRule(
                "white:2",
                "PublishGuideDialog",
                "https",
                "tieba.baidu.com",
                "/mo/q/hybrid-main-dialog/postGuideMask",
                setOf("PUBLISH_GUIDE_STRATEGY", "PAGE_STRATEGY"),
            ),
        )
        add(
            WebRule(
                "white:9",
                "common_sprite_dialog",
                "https",
                "tieba.baidu.com",
                "/mo/q/hybrid-main-chatgroup/ai-bot-claim",
                setOf("NEW_FREQUENCE_STRATEGY"),
            ),
        )
        add(
            WebRule(
                "white:29",
                "manager_invitation_frs",
                "https",
                "tieba.baidu.com",
                "/mo/q/wise-bawu-core/bazhu-invitation-newapply",
                setOf("NA_FRS_MANAGER_INVITATION_STRATEGY", "PAGE_STRATEGY"),
                mapOf("dialog_name" to "manager_invitation_frs"),
                webTransportParameters + "dialog_name",
            ),
        )
        for ((number, level, name) in levelRules()) {
            add(
                WebRule(
                    "white:$number",
                    name,
                    "https",
                    "tieba.baidu.com",
                    "/mo/q/hybrid-main-user/levelUpPopup",
                    setOf("NEW_FREQUENCE_STRATEGY"),
                    mapOf("level" to level.toString()),
                    webTransportParameters + "level",
                ),
            )
        }
    }.associateBy(WebRule::dialogName)

    fun evaluate(input: CloudDialogPolicyInput): CloudDialogDecision {
        return try {
            when (input.mode) {
                CloudDialogMode.BLACKLIST -> evaluateBlacklist(input)
                CloudDialogMode.WHITELIST -> evaluateWhitelist(input)
            }
        } catch (_: RuntimeException) {
            evaluationFailure(input.mode)
        }
    }

    private fun evaluateBlacklist(input: CloudDialogPolicyInput): CloudDialogDecision {
        val name = input.dialogName ?: return allowUnknown()
        val parsed = parseDialog(input) ?: return allowUnknown()
        val strategies = readStrategies(input.strategyTypes) ?: return allowUnknown()

        return when (name) {
            "commercial_push_sign_pop" -> {
                if (
                    webMatches(parsed.uri, "http", "tieba.baidu.com",
                        "/mo/q/hybrid-main-user/signTaskDialog", allowHttps = true) &&
                    COMMERCIAL_PUSH in strategies
                ) {
                    block("black:4")
                } else {
                    allowUnknown()
                }
            }

            "frsLightHouseGuide" -> {
                if (
                    nativeMatches(parsed.uri, name) &&
                    LIGHTHOUSE in strategies
                ) {
                    block("black:6")
                } else {
                    allowUnknown()
                }
            }

            "easterEgg" -> {
                if (
                    nativeMatches(parsed.uri, name) &&
                    EASTER_EGG in strategies
                ) {
                    block("black:8")
                } else {
                    allowUnknown()
                }
            }

            "frs_shop_master_popup" -> {
                if (
                    webMatches(parsed.uri, "https", "tieba.baidu.com",
                        "/mo/q/hybrid-main-dialog/shopOwnerInvite")
                ) {
                    block("black:48")
                } else {
                    allowUnknown()
                }
            }

            "frs_shop_open_popup" -> {
                if (
                    webMatches(parsed.uri, "https", "tieba.baidu.com",
                        "/mo/q/hybrid-main-frs/shopOpenPopup")
                ) {
                    block("black:49")
                } else {
                    allowUnknown()
                }
            }

            "gta6" -> {
                if (
                    webMatches(parsed.uri, "https", "tieba.baidu.com",
                        "/mo/q/hybrid-usergrow-activity/reinvitationCard") &&
                    parsed.parameters["dataUrl"] == "simpleDialog" &&
                    parsed.parameters["dialog_name"] == "gta6"
                ) {
                    block("black:54")
                } else {
                    allowUnknown()
                }
            }

            else -> allowUnknown()
        }
    }

    private fun evaluateWhitelist(input: CloudDialogPolicyInput): CloudDialogDecision {
        val name = input.dialogName
            ?.takeIf { it.isNotEmpty() && it == it.trim() }
            ?: return strictBlock("strict_missing_or_invalid_name")
        val contextTime = input.contextTime
            ?.takeIf { it.isNotEmpty() && it == it.trim() }
            ?: return strictBlock("strict_missing_context")
        if (contextTime !in knownContextTimes) {
            return strictBlock("strict_unknown_context")
        }

        val nativeRule = whiteNativeRules[name]
        val webRule = whiteWebRules[name]
        if (nativeRule == null && webRule == null) {
            return strictBlock("strict_unknown_dialog")
        }

        val parsed = parseDialog(input)
            ?: return strictBlock("strict_invalid_url_or_parameter_conflict")
        val strategies = readStrategies(input.strategyTypes)
            ?: return strictBlock("strict_invalid_strategies")

        if (nativeRule != null) {
            if (!nativeMatches(parsed.uri, nativeRule.dialogName)) {
                return strictBlock("strict_native_authority_mismatch", nativeRule.id)
            }
            if (!strictStrategiesMatch(strategies, nativeRule.requiredStrategies)) {
                return strictBlock("strict_strategy_mismatch", nativeRule.id)
            }
            if (!parametersAllowed(parsed.parameters, nativeRule.allowedParameters)) {
                return strictBlock("strict_unknown_parameter", nativeRule.id)
            }
            if (!nativePayloadValid(nativeRule, parsed.parameters)) {
                return strictBlock("strict_invalid_native_payload", nativeRule.id)
            }
            return allow("strict_whitelist_match", nativeRule.id)
        }

        requireNotNull(webRule)
        if (!webMatches(parsed.uri, webRule.scheme, webRule.host, webRule.path)) {
            return strictBlock("strict_web_authority_mismatch", webRule.id)
        }
        if (!strictStrategiesMatch(strategies, webRule.requiredStrategies)) {
            return strictBlock("strict_strategy_mismatch", webRule.id)
        }
        if (!parametersAllowed(parsed.parameters, webRule.allowedParameters)) {
            return strictBlock("strict_unknown_parameter", webRule.id)
        }
        for ((key, value) in webRule.requiredParameters) {
            if (parsed.parameters[key] != value) {
                return strictBlock("strict_required_parameter_mismatch", webRule.id)
            }
        }
        return allow("strict_whitelist_match", webRule.id)
    }

    private fun parseDialog(input: CloudDialogPolicyInput): ParsedDialog? {
        val rawUrl = input.cachedUrl ?: return null
        if (rawUrl.isEmpty() || rawUrl.length > MAX_URL_CHARS) return null
        val uri = URI(rawUrl)
        if (uri.rawFragment != null || uri.rawUserInfo != null) return null

        val parameters = LinkedHashMap<String, String>()
        val rawQuery = uri.rawQuery
        if (rawQuery != null) {
            if (rawQuery.length > MAX_URL_CHARS) return null
            val pairs = rawQuery.split('&')
            if (pairs.size > MAX_QUERY_PAIRS) return null
            for (pair in pairs) {
                if (pair.isEmpty()) continue
                val separator = pair.indexOf('=')
                val rawKey = if (separator < 0) pair else pair.substring(0, separator)
                val rawValue = if (separator < 0) "" else pair.substring(separator + 1)
                val key = decodeQuery(rawKey) ?: return null
                val value = decodeQuery(rawValue) ?: return null
                if (!validKey(key) || value.length > MAX_QUERY_VALUE_CHARS) return null
                if (!mergeParameter(parameters, key, value)) return null
            }
        }

        var fieldCount = 0
        for ((key, value) in input.fields) {
            fieldCount += 1
            if (
                fieldCount > MAX_FIELD_ENTRIES ||
                !validKey(key) ||
                value.length > MAX_FIELD_VALUE_CHARS
            ) {
                return null
            }
            if (!mergeParameter(parameters, key, value)) return null
        }
        // The host adds its caller time before appending the third-argument fields.
        // Include it in conflict detection instead of assuming which duplicate wins.
        if (input.mode == CloudDialogMode.WHITELIST) {
            val time = input.contextTime ?: return null
            if (!mergeParameter(parameters, "time", time)) return null
        }
        return ParsedDialog(uri, parameters.toMap())
    }

    private fun decodeQuery(raw: String): String? {
        if (raw.length > MAX_QUERY_VALUE_CHARS * 3) return null
        return try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun mergeParameter(
        target: MutableMap<String, String>,
        key: String,
        value: String,
    ): Boolean {
        val previous = target[key]
        if (previous != null && previous != value) return false
        target[key] = value
        return true
    }

    private fun validKey(key: String): Boolean {
        return key.isNotEmpty() && key == key.trim() && key.length <= MAX_KEY_CHARS
    }

    private fun readStrategies(raw: List<String>): Set<String>? {
        if (raw.size > MAX_STRATEGIES) return null
        val result = LinkedHashSet<String>()
        for (type in raw) {
            if (
                type.isEmpty() ||
                type != type.trim() ||
                type.length > MAX_STRATEGY_CHARS
            ) {
                return null
            }
            result.add(type)
        }
        return result
    }

    private fun strictStrategiesMatch(
        actual: Set<String>,
        required: Set<String>,
    ): Boolean {
        if (!actual.containsAll(required)) return false
        val allowed = required + internalStrategies
        return actual.all { it in allowed }
    }

    private fun nativeMatches(uri: URI, expectedName: String): Boolean {
        return uri.scheme == "dialoginternal" &&
            uri.rawAuthority == expectedName &&
            uri.rawPath.isNullOrEmpty() &&
            uri.port == -1
    }

    private fun webMatches(
        uri: URI,
        expectedScheme: String,
        expectedHost: String,
        expectedPath: String,
        allowHttps: Boolean = false,
    ): Boolean {
        val schemeMatches = uri.scheme == expectedScheme ||
            (allowHttps && expectedScheme == "http" && uri.scheme == "https")
        return schemeMatches &&
            uri.host == expectedHost &&
            uri.port == -1 &&
            uri.rawPath == expectedPath &&
            uri.rawAuthority == expectedHost
    }

    private fun parametersAllowed(
        parameters: Map<String, String>,
        allowed: Set<String>,
    ): Boolean {
        return parameters.keys.all { it in allowed }
    }

    private fun nativePayloadValid(
        rule: NativeRule,
        parameters: Map<String, String>,
    ): Boolean {
        val dialogUrl = parameters["dialogUrl"]
        if (
            rule.dialogName == "lcUpdateDialog" &&
            dialogUrl != null &&
            dialogUrl != rule.rawUrl
        ) {
            return false
        }
        if (rule.dialogName != "adDownloadConfirmDialog") return true
        val dialogData = parameters["dialog_data"] ?: return true
        if (dialogData.length > MAX_DYNAMIC_JSON_CHARS) return false
        if (!jsonNestingBounded(dialogData)) return false
        return try {
            JSONObject(dialogData)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun jsonNestingBounded(raw: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (character in raw) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{', '[' -> {
                    depth += 1
                    if (depth > MAX_JSON_DEPTH) return false
                }
                '}', ']' -> {
                    depth -= 1
                    if (depth < 0) return false
                }
            }
        }
        return !inString && depth == 0
    }

    private fun levelRules(): List<Triple<Int, Int, String>> {
        return listOf(
            Triple(37, 10, "levelUpPopuplevel10"),
            Triple(38, 2, "levelUpPopuplevel2"),
            Triple(39, 3, "levelUpPopuplevel3"),
            Triple(40, 4, "levelUpPopuplevel4"),
            Triple(41, 5, "levelUpPopuplevel5"),
            Triple(42, 6, "levelUpPopuplevel6"),
            Triple(43, 7, "levelUpPopuplevel7"),
            Triple(44, 8, "levelUpPopuplevel8"),
            Triple(45, 9, "levelUpPopuplevel9"),
            Triple(46, 1, "levelUpPopuplevel1"),
        )
    }

    private fun block(rule: String): CloudDialogDecision {
        return CloudDialogDecision(
            block = true,
            reason = "blacklist_match",
            matchedRule = rule,
        )
    }

    private fun strictBlock(reason: String, rule: String? = null): CloudDialogDecision {
        return CloudDialogDecision(
            block = true,
            reason = reason,
            matchedRule = rule,
        )
    }

    private fun allow(reason: String, rule: String? = null): CloudDialogDecision {
        return CloudDialogDecision(
            block = false,
            reason = reason,
            matchedRule = rule,
        )
    }

    private fun allowUnknown(): CloudDialogDecision {
        return allow("blacklist_unknown_or_incomplete")
    }

    private fun evaluationFailure(mode: CloudDialogMode): CloudDialogDecision {
        return when (mode) {
            CloudDialogMode.BLACKLIST -> allow("policy_failure_blacklist_allow")
            CloudDialogMode.WHITELIST -> strictBlock("policy_failure_strict")
        }
    }
}
