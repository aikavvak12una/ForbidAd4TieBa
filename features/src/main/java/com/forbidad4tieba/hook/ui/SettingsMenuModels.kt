package com.forbidad4tieba.hook.ui

import com.forbidad4tieba.hook.config.BooleanPreference
import com.forbidad4tieba.hook.config.SimpleToggle

internal data class SwitchItem(
    val preference: BooleanPreference,
    val actionIcon: String? = null,
    val actionContentDescription: String? = null,
    val onActionClick: (() -> Unit)? = null,
) {
    private val presentation = requireNotNull(preference.presentation)
    val label: String get() = presentation.label
    val description: String get() = presentation.description
    val prefKey: String get() = preference.key
    val supported: Boolean get() = true
    val defaultValue: Boolean get() = preference.defaultValue

    constructor(toggle: SimpleToggle) : this(toggle.preference)
}

internal data class SettingGroup(val name: String, val items: List<SwitchItem>)
