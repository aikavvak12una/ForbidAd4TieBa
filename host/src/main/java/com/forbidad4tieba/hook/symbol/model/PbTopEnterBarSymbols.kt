package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Field
import java.lang.reflect.Method

internal data class PbTopEnterBarScanSymbols(
    val controllerClass: String? = null,
    val initializeMethod: String? = null,
    val containerField: String? = null,
    val navigationBarField: String? = null,
)

data class PbTopEnterBarSymbols(
    val initializeMethod: Method,
    val containerField: Field,
    val navigationBarField: Field,
)
