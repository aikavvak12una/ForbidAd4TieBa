package com.forbidad4tieba.hook.symbol.model

import com.forbidad4tieba.hook.symbol.contract.*

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
            symbols[ForumPageAdContract.forumBusinessPromotSetterMethod],
            symbols[ForumPageAdContract.forumPrivatePopSetterMethod],
            symbols[ForumPageAdContract.forumSpriteBubbleSetterMethod],
            symbols[ForumPageAdContract.forumMaskPopSetterMethod],
        ).count(::has)
        return ForumPageAdReadiness(
            response = has(symbols[ForumPageAdContract.forumResponseDataClass]) &&
                has(symbols[ForumPageAdContract.forumResponseParserMethod]) &&
                symbols[ForumPageAdContract.forumResponseAdFields].orEmpty().count { it.isNotBlank() } >= 4,
            bottom = has(symbols[ForumPageAdContract.forumPageMapperClass]) &&
                has(symbols[ForumPageAdContract.forumBottomDataMapperMethod]) &&
                has(symbols[ForumPageAdContract.forumBottomDataClass]) &&
                bottomSetterCount >= 3,
            gameBar = has(symbols[ForumPageAdContract.forumPageMapperClass]) &&
                has(symbols[ForumPageAdContract.forumBottomGameBarMapperMethod]),
            rain = has(symbols[ForumPageAdContract.forumPageMapperClass]) &&
                has(symbols[ForumPageAdContract.forumHeaderDataMapperMethod]) &&
                has(symbols[ForumPageAdContract.forumHeaderDataClass]) &&
                has(symbols[ForumPageAdContract.forumRainDataClass]) &&
                has(symbols[ForumPageAdContract.forumRainSetterMethod]),
            dialog = has(symbols[ForumPageAdContract.forumDialogControllerClass]) &&
                has(symbols[ForumPageAdContract.forumBusinessPromotShowMethod]),
            floating = has(symbols[ForumPageAdContract.forumGameFloatingBarControllerClass]) &&
                has(symbols[ForumPageAdContract.forumGameFloatingBarShowMethod]),
            biz = has(symbols[ForumPageAdContract.forumBusinessPromotBizClass]) &&
                has(symbols[ForumPageAdContract.forumBusinessPromotJumpMethod]),
        )
    }

    private fun has(value: String?): Boolean = !value.isNullOrBlank()
}
