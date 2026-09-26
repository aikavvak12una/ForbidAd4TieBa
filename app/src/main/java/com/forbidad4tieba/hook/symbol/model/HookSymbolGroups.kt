package com.forbidad4tieba.hook.symbol.model

import com.forbidad4tieba.hook.symbol.lowend.LowEndConfigSymbols

data class HookPointSymbols(
    val primary: PrimaryHookPointSymbols = PrimaryHookPointSymbols(),
    val web: WebSymbols = WebSymbols(),
    val signIn: SignInSymbols = SignInSymbols(),
    val privateMessage: PrivateMessageSymbols = PrivateMessageSymbols(),
    val collectionHistory: CollectionHistorySymbols = CollectionHistorySymbols(),
    val media: MediaHookPointSymbols = MediaHookPointSymbols(),
    val performanceAbMethods: List<String>? = null,
    val trackingMethods: List<String>? = null,
    val defaultPopups: DefaultPopupSymbols = DefaultPopupSymbols(),
    val lowEndConfig: LowEndConfigSymbols = LowEndConfigSymbols(),
)

data class PrimaryHookPointSymbols(
    val settings: SettingsSymbols = SettingsSymbols(),
    val home: HomeSymbols = HomeSymbols(),
    val ad: AdSymbols = AdSymbols(),
    val pb: PbSymbols = PbSymbols(),
)

data class MediaHookPointSymbols(
    val image: ImageSymbols = ImageSymbols(),
    val ai: AiSymbols = AiSymbols(),
)

data class SettingsSymbols(
    val settingsClass: String? = null,
    val settingsInitMethod: String? = null,
    val settingsContainerField: String? = null,
)
