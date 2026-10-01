package com.forbidad4tieba.hook

import com.forbidad4tieba.hook.config.SettingsSnapshot
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import org.junit.Assert.*
import org.junit.Test

class CommentShortcutFeatureTest {
    private val filterFields = listOf("commentFilterPageNative", "commentFilterPageJson", "commentFilterFloorNative",
        "commentFilterFloorJson", "commentFilterProtocol")
    private fun symbols(fields: List<String>): HookSymbols = requireNotNull(HookSymbols.fromJson(
        fields.joinToString(prefix = "{", postfix = "}") { "\"$it\":\"fixture\"" },
    ))
    private fun planned(name: String, fields: List<String>, enabled: Boolean = false, process: String = "com.baidu.tieba", shortcut: Boolean = true): Boolean {
        val baseline = SettingsSnapshot.bootstrap()
        val settings = baseline.copy(commentLevelFilter = baseline.commentLevelFilter.copy(enabled = enabled), isCommentShortcutEnabled = shortcut)
        return FeatureCatalog.definitions.single { it.id == name }.entries(HookInstallContext(process, symbols(fields)), settings).isNotEmpty()
    }
    @Test fun offAtStartupStillPreparesFilterForShortcutAndMissingUiDoesNotDisableNormalFilter() {
        val all = filterFields + "commentShortcutTargets"
        assertTrue(planned("CommentShortcut", all))
        assertTrue(planned("CommentLevelFilter", all))
        assertFalse(planned("CommentShortcut", filterFields))
        assertFalse(planned("CommentLevelFilter", filterFields))
        assertTrue(planned("CommentLevelFilter", filterFields, enabled = true))
        all.forEach { missing -> assertFalse(planned("CommentShortcut", all - missing)) }
        assertFalse(planned("CommentShortcut", all, process = "com.baidu.tieba:extra"))
        assertFalse(planned("CommentShortcut", all, shortcut = false))
        assertFalse(planned("CommentLevelFilter", all, shortcut = false))
        assertTrue(planned("CommentLevelFilter", all, enabled = true, shortcut = false))
    }
}
