package com.forbidad4tieba.hook.symbol.model

data class WebSymbols(
    val enterForum: EnterForumWebSymbolsGroup = EnterForumWebSymbolsGroup(),
    val plainUrl: PlainUrlSymbolsGroup = PlainUrlSymbolsGroup(),
    val mountCard: MountCardLinkSymbolsGroup = MountCardLinkSymbolsGroup(),
)

data class EnterForumWebSymbolsGroup(
    val enterForumWebControllerClass: String? = null,
    val enterForumWebLoadMethod: String? = null,
    val enterForumInitInfoDataClass: String? = null,
    val enterForumInitInfoGetUrlMethod: String? = null,
)

data class PlainUrlSymbolsGroup(
    val clickableSpan: PlainUrlClickableSpanSymbolsGroup = PlainUrlClickableSpanSymbolsGroup(),
    val message: PlainUrlMessageSymbolsGroup = PlainUrlMessageSymbolsGroup(),
)

data class PlainUrlClickableSpanSymbolsGroup(
    val plainUrlClickableSpanClass: String? = null,
    val plainUrlClickableSpanOnClickMethod: String? = null,
    val plainUrlClickableSpanOnClickOwnerClasses: List<String>? = null,
    val plainUrlClickableSpanTypeField: String? = null,
    val plainUrlClickableSpanUrlField: String? = null,
    val plainUrlClickableSpanTextField: String? = null,
)

data class PlainUrlMessageSymbolsGroup(
    val manager: PlainUrlMessageManagerSymbolsGroup = PlainUrlMessageManagerSymbolsGroup(),
    val response: PlainUrlMessageResponseSymbolsGroup = PlainUrlMessageResponseSymbolsGroup(),
    val application: PlainUrlApplicationSymbolsGroup = PlainUrlApplicationSymbolsGroup(),
)

data class PlainUrlMessageManagerSymbolsGroup(
    val plainUrlMessageManagerClass: String? = null,
    val plainUrlMessageDispatchMethod: String? = null,
)

data class PlainUrlMessageResponseSymbolsGroup(
    val plainUrlResponsedMessageClass: String? = null,
    val plainUrlResponsedMessageGetCmdMethod: String? = null,
    val plainUrlCustomResponsedMessageClass: String? = null,
    val plainUrlCustomResponsedMessageGetDataMethod: String? = null,
)

data class PlainUrlApplicationSymbolsGroup(
    val plainUrlApplicationClass: String? = null,
    val plainUrlApplicationGetInstMethod: String? = null,
)

data class MountCardLinkSymbolsGroup(
    val mountCardLinkLayoutClass: String? = null,
    val mountCardLinkLayoutOnClickMethod: String? = null,
    val mountCardLinkLayoutDataField: String? = null,
    val mountCardLinkInfoDataClass: String? = null,
    val mountCardLinkInfoGetUrlMethod: String? = null,
)
