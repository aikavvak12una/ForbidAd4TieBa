package com.forbidad4tieba.hook.ui

internal data class SwitchItem(
    val label: String,
    val description: String,
    val prefKey: String,
    val supported: Boolean,
    val defaultValue: Boolean = false,
    val actionIcon: String? = null,
    val linkedPrefKeys: List<String> = emptyList(),
    val onActionClick: (() -> Unit)? = null,
)

internal data class SettingGroup(
    val name: String,
    val items: List<SwitchItem>,
)
