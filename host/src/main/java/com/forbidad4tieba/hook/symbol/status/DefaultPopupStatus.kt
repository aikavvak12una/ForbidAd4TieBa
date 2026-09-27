package com.forbidad4tieba.hook.symbol.status

import com.forbidad4tieba.hook.symbol.model.DefaultPopupSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.HookFeatureState
import com.forbidad4tieba.hook.symbol.model.HookFeatureStatus

internal object DefaultPopupStatus {
    fun hookPoints(symbols: DefaultPopupSymbols): List<HookPointStatus> = listOf(
        buildHookPointStatus(
            "FirstLikePopupBlockHook",
            symbols.firstLikeResponseClass.orEmpty() + " -> " + symbols.firstLikeToastMethod.orEmpty(),
            listOf(
                "responseClass" to !symbols.firstLikeResponseClass.isNullOrBlank(),
                "toastParser" to !symbols.firstLikeToastMethod.isNullOrBlank(),
            ),
        ),
        buildHookPointStatus(
            "NotificationGuideBlockHook",
            symbols.notificationGuideClass.orEmpty() + "." + symbols.notificationGuideMethod.orEmpty(),
            listOf(
                "guideClass" to !symbols.notificationGuideClass.isNullOrBlank(),
                "tryShow" to !symbols.notificationGuideMethod.isNullOrBlank(),
            ),
        ),
    )

    fun features(symbols: DefaultPopupSymbols): Map<String, HookFeatureStatus> =
        listOf(HookFeatureKey.BLOCK_FIRST_LIKE_POPUP, HookFeatureKey.BLOCK_NOTIFICATION_GUIDE)
            .zip(hookPoints(symbols)).associate { (key, point) ->
                key to HookFeatureStatus(
                    state = if (point.state == HookPointState.FOUND) HookFeatureState.FULL else HookFeatureState.DISABLED,
                    missingCritical = point.missing,
                )
            }
}
