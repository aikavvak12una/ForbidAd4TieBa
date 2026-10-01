package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Field
import java.lang.reflect.Method

data class PbCommentBatchSymbols(
    val scroll: Method,
    val fragment: Field,
    val prefetch: Method,
    val request: Method,
    val loading: Method,
    val commit: Method,
    val items: Method,
    val pause: Method,
    val destroyView: Method,
    val appendMode: Any,
    val appendRequestKind: Any,
    val networkType: Any,
)
