package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.DexEnterForumCapsuleMethodKind
import com.forbidad4tieba.hook.symbol.model.DexEnterForumCapsuleMethodMatch

internal data class EnterForumCapsuleMethodPair(
    val init: DexEnterForumCapsuleMethodMatch,
    val refresh: DexEnterForumCapsuleMethodMatch,
) {
    val score: Int get() = init.score + refresh.score
}

internal fun enterForumCapsulePairs(
    methods: List<DexEnterForumCapsuleMethodMatch>,
): List<EnterForumCapsuleMethodPair> {
    val initializers = methods.filter { it.kind == DexEnterForumCapsuleMethodKind.INIT }
    val refreshers = methods.filter { it.kind == DexEnterForumCapsuleMethodKind.REFRESH }
    return initializers.flatMap { init ->
        refreshers.filter { refresh ->
            !init.viewFieldName.isNullOrBlank() && init.viewFieldName == refresh.viewFieldName &&
                !refresh.titleFieldName.isNullOrBlank() && init.ownerMethodName != refresh.ownerMethodName
        }.map { refresh -> EnterForumCapsuleMethodPair(init, refresh) }
    }.distinctBy { listOf(it.init.ownerMethodName, it.refresh.ownerMethodName, it.init.viewFieldName, it.refresh.titleFieldName) }
}
