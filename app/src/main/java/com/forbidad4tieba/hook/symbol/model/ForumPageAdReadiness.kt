package com.forbidad4tieba.hook.symbol.model

internal data class ForumPageAdReadiness(
    val response: Boolean,
    val bottom: Boolean,
    val gameBar: Boolean,
    val rain: Boolean,
    val dialog: Boolean,
    val floating: Boolean,
    val biz: Boolean,
) {
    val any: Boolean
        get() = response || bottom || gameBar || rain || dialog || floating || biz

    val readyLabels: List<String>
        get() = listOfNotNull(
            "response".takeIf { response },
            "bottom".takeIf { bottom },
            "gameBar".takeIf { gameBar },
            "rain".takeIf { rain },
            "dialog".takeIf { dialog },
            "floating".takeIf { floating },
            "biz".takeIf { biz },
        )
}

internal object ForumPageAdSymbolReadiness {
    fun evaluate(symbols: HookSymbols): ForumPageAdReadiness {
        val bottomSetterCount = listOf(
            symbols.forumBusinessPromotSetterMethod,
            symbols.forumPrivatePopSetterMethod,
            symbols.forumSpriteBubbleSetterMethod,
            symbols.forumMaskPopSetterMethod,
        ).count(::has)
        return ForumPageAdReadiness(
            response = has(symbols.forumResponseDataClass) &&
                has(symbols.forumResponseParserMethod) &&
                symbols.forumResponseAdFields.orEmpty().count { it.isNotBlank() } >= 4,
            bottom = has(symbols.forumPageMapperClass) &&
                has(symbols.forumBottomDataMapperMethod) &&
                has(symbols.forumBottomDataClass) &&
                bottomSetterCount >= 3,
            gameBar = has(symbols.forumPageMapperClass) &&
                has(symbols.forumBottomGameBarMapperMethod),
            rain = has(symbols.forumPageMapperClass) &&
                has(symbols.forumHeaderDataMapperMethod) &&
                has(symbols.forumHeaderDataClass) &&
                has(symbols.forumRainDataClass) &&
                has(symbols.forumRainSetterMethod),
            dialog = has(symbols.forumDialogControllerClass) &&
                (
                    has(symbols.forumBusinessPromotShowMethod) ||
                        has(symbols.forumAnimationShowMethod)
                    ),
            floating = has(symbols.forumGameFloatingBarControllerClass) &&
                has(symbols.forumGameFloatingBarShowMethod),
            biz = has(symbols.forumBusinessPromotBizClass) &&
                has(symbols.forumBusinessPromotJumpMethod),
        )
    }

    private fun has(value: String?): Boolean = !value.isNullOrBlank()
}
