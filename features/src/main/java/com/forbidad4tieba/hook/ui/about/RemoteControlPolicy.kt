package com.forbidad4tieba.hook.ui.about

import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class RemoteControls(
    val environmentLevels: Map<Int, EnvironmentLevelControls>,
    val rules: List<RemoteRule> = emptyList(),
) {
    companion object {
        val DEFAULT = RemoteControls(mapOf(
            0 to EnvironmentLevelControls(showWarningDialog = false, lockHiddenFeatures = false),
            1 to EnvironmentLevelControls(showWarningDialog = true, lockHiddenFeatures = false),
            2 to EnvironmentLevelControls(showWarningDialog = true, lockHiddenFeatures = false),
        ))
    }

    fun forLevel(level: Int): EnvironmentLevelControls {
        return environmentLevels[level] ?: DEFAULT.environmentLevels[level] ?: EnvironmentLevelControls()
    }
}

internal data class EnvironmentLevelControls(
    val showWarningDialog: Boolean = false,
    val lockHiddenFeatures: Boolean = false,
)

internal data class RemoteRule(
    val id: String,
    val enabled: Boolean,
    val condition: JSONObject?,
    val actions: List<RemoteAction>,
)

internal sealed class RemoteAction {
    object ShowWarningDialog : RemoteAction()
    object LockHiddenFeatures : RemoteAction()
    data class CustomDialog(val dialog: RemoteCustomDialog) : RemoteAction()
}

internal data class EvaluatedRemoteControls(
    val showWarningDialog: Boolean,
    val lockHiddenFeatures: Boolean,
    val customDialogs: List<RemoteCustomDialog>,
    val matchedRuleCount: Int,
)

internal object RemoteControlPolicy {
    private val ENVIRONMENT_RATING_LEVELS = intArrayOf(0, 1, 2)

    data class RemoteConditionContext(
        val environment: RuntimeEnvironment,
        val accountId: String?,
        val moduleVersionCode: Int,
    )

    private data class RemoteConditionValue(
        val text: String,
        val number: Long?,
    )

    private data class RemoteFieldLookup(
        val known: Boolean,
        val value: RemoteConditionValue?,
    )

    private enum class RemoteConditionResult {
        MATCH,
        NO_MATCH,
        IGNORED,
    }

    fun parse(root: JSONObject): RemoteControls {
        val defaultControls = RemoteControls.DEFAULT
        val controls = root.optJSONObject("controls") ?: return defaultControls
        val rules = parseRemoteRules(controls)
        val levels = controls.optJSONObject("environmentLevels")
            ?: return defaultControls.copy(rules = rules)

        val parsedLevels = LinkedHashMap<Int, EnvironmentLevelControls>()
        for (level in ENVIRONMENT_RATING_LEVELS) {
            val defaultLevelControls = defaultControls.forLevel(level)
            val levelControls = levels.optJSONObject(level.toString())
            parsedLevels[level] = if (levelControls == null) {
                defaultLevelControls
            } else {
                EnvironmentLevelControls(
                    showWarningDialog = optRemoteBoolean(
                        source = levelControls,
                        key = "showWarningDialog",
                        defaultValue = defaultLevelControls.showWarningDialog,
                    ),
                    lockHiddenFeatures = optRemoteBoolean(
                        source = levelControls,
                        key = "lockHiddenFeatures",
                        defaultValue = defaultLevelControls.lockHiddenFeatures,
                    ),
                )
            }
        }
        return RemoteControls(parsedLevels, rules)
    }

    private fun parseRemoteRules(controls: JSONObject): List<RemoteRule> {
        val rules = controls.optJSONArray("rules") ?: return emptyList()
        val out = ArrayList<RemoteRule>(rules.length())
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i)
            if (rule == null) {
                XposedCompat.logD("[AboutInfo] controls.rules[$i] ignored: not object")
                continue
            }
            parseRemoteRule(rule, i)?.let(out::add)
        }
        return out
    }

    private fun parseRemoteRule(rule: JSONObject, index: Int): RemoteRule? {
        return try {
            val enabled = optRemoteBoolean(rule, "enabled", true)
            val id = rule.optString("id", "rule_${index + 1}").trim().ifEmpty { "rule_${index + 1}" }
            val condition = rule.optJSONObject("when")
            val actions = parseRemoteActions(rule.optJSONArray("actions"), id)
            if (actions.isEmpty()) {
                XposedCompat.logD("[AboutInfo] controls.rules[$index] ignored: actions empty id=$id")
                return null
            }
            RemoteRule(
                id = id,
                enabled = enabled,
                condition = condition,
                actions = actions,
            )
        } catch (t: Throwable) {
            XposedCompat.logW("[AboutInfo] controls.rules[$index] ignored: ${t.message}")
            null
        }
    }

    private fun parseRemoteActions(actions: JSONArray?, ruleId: String): List<RemoteAction> {
        if (actions == null) return emptyList()
        val out = ArrayList<RemoteAction>(actions.length())
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i)
            if (action == null) {
                XposedCompat.logD("[AboutInfo] controls.rules[$ruleId].actions[$i] ignored: not object")
                continue
            }
            when (action.optString("type", "").trim()) {
                "showWarningDialog" -> out.add(RemoteAction.ShowWarningDialog)
                "lockHiddenFeatures" -> out.add(RemoteAction.LockHiddenFeatures)
                "customDialog" -> parseRemoteCustomDialog(action, ruleId)?.let {
                    out.add(RemoteAction.CustomDialog(it))
                }
                else -> {
                    XposedCompat.logD(
                        "[AboutInfo] controls.rules[$ruleId].actions[$i] ignored: unknown type"
                    )
                }
            }
        }
        return out
    }

    private fun parseRemoteCustomDialog(action: JSONObject, ruleId: String): RemoteCustomDialog? {
        val id = action.optString("id", "").trim()
        val revision = action.optInt("revision", 0)
        val title = action.optString("title", "").trim()
        val message = action.optString("message", "").trim()
        if (id.isEmpty() || revision <= 0 || title.isEmpty() || message.isEmpty()) {
            XposedCompat.logD("[AboutInfo] controls.rules[$ruleId].customDialog ignored: required field missing")
            return null
        }

        val urlButton = action.optJSONObject("urlButton")?.let { button ->
            val text = button.optString("text", "").trim()
            val url = button.optString("url", "").trim()
            if (text.isNotEmpty() && isHttpOrHttpsUrl(url)) {
                RemoteCustomDialogUrlButton(text = text, url = url)
            } else {
                XposedCompat.logD("[AboutInfo] controls.rules[$ruleId].customDialog urlButton ignored")
                null
            }
        }

        return RemoteCustomDialog(
            id = id,
            revision = revision,
            title = title,
            message = message,
            urlButton = urlButton,
        )
    }

    private fun optRemoteBoolean(
        source: JSONObject,
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        val value = if (source.has(key)) source.opt(key) else null
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase(Locale.ROOT)) {
                "1", "true", "yes", "on", "enabled" -> true
                "0", "false", "no", "off", "disabled" -> false
                else -> defaultValue
            }
            else -> defaultValue
        }
    }

    fun dependsOnAccountId(controls: RemoteControls): Boolean {
        return controls.rules.any { rule ->
            rule.enabled && remoteConditionReferencesField(rule.condition, "account_id")
        }
    }

    private fun remoteConditionReferencesField(condition: JSONObject?, fieldName: String): Boolean {
        if (condition == null) return false
        if (condition.optString("field", "").trim() == fieldName) return true
        condition.optJSONArray("all")?.let { conditions ->
            for (i in 0 until conditions.length()) {
                if (remoteConditionReferencesField(conditions.optJSONObject(i), fieldName)) return true
            }
        }
        condition.optJSONArray("any")?.let { conditions ->
            for (i in 0 until conditions.length()) {
                if (remoteConditionReferencesField(conditions.optJSONObject(i), fieldName)) return true
            }
        }
        if (remoteConditionReferencesField(condition.optJSONObject("not"), fieldName)) return true
        return false
    }

    fun evaluate(
        rules: List<RemoteRule>,
        conditionContext: RemoteConditionContext,
    ): EvaluatedRemoteControls {
        var showWarningDialog = false
        var lockHiddenFeatures = false
        val customDialogs = ArrayList<RemoteCustomDialog>()
        var matchedRuleCount = 0

        for (rule in rules) {
            if (!rule.enabled) continue
            if (evaluateRemoteCondition(rule.condition, conditionContext) != RemoteConditionResult.MATCH) {
                continue
            }
            matchedRuleCount += 1
            for (action in rule.actions) {
                when (action) {
                    RemoteAction.ShowWarningDialog -> showWarningDialog = true
                    RemoteAction.LockHiddenFeatures -> lockHiddenFeatures = true
                    is RemoteAction.CustomDialog -> {
                        customDialogs.add(action.dialog)
                    }
                }
            }
        }

        return EvaluatedRemoteControls(
            showWarningDialog = showWarningDialog,
            lockHiddenFeatures = lockHiddenFeatures,
            customDialogs = customDialogs,
            matchedRuleCount = matchedRuleCount,
        )
    }

    private fun evaluateRemoteCondition(
        condition: JSONObject?,
        context: RemoteConditionContext,
    ): RemoteConditionResult {
        if (condition == null) return RemoteConditionResult.NO_MATCH

        condition.optJSONArray("all")?.let { conditions ->
            var hasRecognizedCondition = false
            for (i in 0 until conditions.length()) {
                when (evaluateRemoteCondition(conditions.optJSONObject(i), context)) {
                    RemoteConditionResult.NO_MATCH -> return RemoteConditionResult.NO_MATCH
                    RemoteConditionResult.MATCH -> hasRecognizedCondition = true
                    RemoteConditionResult.IGNORED -> Unit
                }
            }
            return if (hasRecognizedCondition) {
                RemoteConditionResult.MATCH
            } else {
                RemoteConditionResult.IGNORED
            }
        }

        condition.optJSONArray("any")?.let { conditions ->
            var hasRecognizedCondition = false
            for (i in 0 until conditions.length()) {
                when (evaluateRemoteCondition(conditions.optJSONObject(i), context)) {
                    RemoteConditionResult.MATCH -> return RemoteConditionResult.MATCH
                    RemoteConditionResult.NO_MATCH -> hasRecognizedCondition = true
                    RemoteConditionResult.IGNORED -> Unit
                }
            }
            return if (hasRecognizedCondition) {
                RemoteConditionResult.NO_MATCH
            } else {
                RemoteConditionResult.IGNORED
            }
        }

        condition.optJSONObject("not")?.let { nested ->
            return when (evaluateRemoteCondition(nested, context)) {
                RemoteConditionResult.MATCH -> RemoteConditionResult.NO_MATCH
                RemoteConditionResult.NO_MATCH -> RemoteConditionResult.MATCH
                RemoteConditionResult.IGNORED -> RemoteConditionResult.IGNORED
            }
        }

        return evaluateRemoteConditionLeaf(condition, context)
    }

    private fun evaluateRemoteConditionLeaf(
        condition: JSONObject,
        context: RemoteConditionContext,
    ): RemoteConditionResult {
        val field = condition.optString("field", "").trim()
        val op = condition.optString("op", "").trim()
        if (field.isEmpty() || op.isEmpty() || !condition.has("value")) {
            return RemoteConditionResult.IGNORED
        }

        val lookup = remoteFieldValue(field, context)
        if (!lookup.known) return RemoteConditionResult.IGNORED
        val actual = lookup.value ?: return RemoteConditionResult.NO_MATCH
        val expected = condition.opt("value")

        val matched = when (op) {
            "eq" -> remoteValueEquals(actual, expected)
            "neq" -> !remoteValueEquals(actual, expected)
            "in" -> remoteValueList(expected).any { remoteValueEquals(actual, it) }
            "not_in" -> remoteValueList(expected).none { remoteValueEquals(actual, it) }
            "lt" -> compareRemoteNumber(actual, expected) { a, b -> a < b }
            "lte" -> compareRemoteNumber(actual, expected) { a, b -> a <= b }
            "gt" -> compareRemoteNumber(actual, expected) { a, b -> a > b }
            "gte" -> compareRemoteNumber(actual, expected) { a, b -> a >= b }
            "matches" -> remoteValueMatches(actual, expected)
            else -> return RemoteConditionResult.IGNORED
        }
        return if (matched) RemoteConditionResult.MATCH else RemoteConditionResult.NO_MATCH
    }

    private fun remoteFieldValue(
        field: String,
        context: RemoteConditionContext,
    ): RemoteFieldLookup {
        val environment = context.environment
        return when (field) {
            "module_version_code" -> RemoteFieldLookup(
                known = true,
                value = remoteNumberValue(context.moduleVersionCode.toLong()),
            )
            "account_id" -> RemoteFieldLookup(
                known = true,
                value = context.accountId?.let(::remoteStringValue),
            )
            "environment_level" -> RemoteFieldLookup(
                known = true,
                value = remoteNumberValue(environment.environmentRatingLevel.toLong()),
            )
            "xposed_framework_name" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.xposedFrameworkName),
            )
            "patch_mode" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.patchMode),
            )
            "runtime_kind" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.runtimeKind),
            )
            "xposed_framework_version_code" -> RemoteFieldLookup(
                known = true,
                value = remoteStringValue(environment.xposedFrameworkVersionCode),
            )
            else -> RemoteFieldLookup(known = false, value = null)
        }
    }

    private fun remoteStringValue(value: String): RemoteConditionValue {
        return RemoteConditionValue(
            text = value,
            number = value.toLongOrNull(),
        )
    }

    private fun remoteNumberValue(value: Long): RemoteConditionValue {
        return RemoteConditionValue(
            text = value.toString(),
            number = value,
        )
    }

    private fun remoteValueEquals(actual: RemoteConditionValue, expected: Any?): Boolean {
        val expectedNumber = expected?.remoteLongOrNull()
        if (actual.number != null && expectedNumber != null) {
            return actual.number == expectedNumber
        }
        return actual.text == expected?.toString().orEmpty()
    }

    private fun remoteValueList(value: Any?): List<Any?> {
        if (value == null || value == JSONObject.NULL) return emptyList()
        if (value !is JSONArray) return listOf(value)
        val out = ArrayList<Any?>(value.length())
        for (i in 0 until value.length()) {
            out.add(value.opt(i))
        }
        return out
    }

    private fun compareRemoteNumber(
        actual: RemoteConditionValue,
        expected: Any?,
        predicate: (Long, Long) -> Boolean,
    ): Boolean {
        val actualNumber = actual.number ?: return false
        val expectedNumber = expected.remoteLongOrNull() ?: return false
        return predicate(actualNumber, expectedNumber)
    }

    private fun remoteValueMatches(actual: RemoteConditionValue, expected: Any?): Boolean {
        val pattern = expected?.toString()?.takeIf { it.isNotBlank() } ?: return false
        return try {
            Regex(pattern).containsMatchIn(actual.text)
        } catch (t: Throwable) {
            XposedCompat.logD("[AboutInfo] remote condition regex ignored: ${t.message}")
            false
        }
    }

    private fun Any?.remoteLongOrNull(): Long? {
        return when (this) {
            is Number -> toLong()
            is String -> trim().toLongOrNull()
            else -> null
        }
    }
}
