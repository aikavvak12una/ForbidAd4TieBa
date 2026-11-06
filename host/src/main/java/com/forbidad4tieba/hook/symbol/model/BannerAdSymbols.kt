package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Field
import java.lang.reflect.Method

data class BannerAdSymbols(
    val conversionMethod: Method,
    val layoutField: Field,
    val bannerCardField: Field,
    val componentsField: Field,
    val componentField: Field,
    val feedBannerField: Field,
    val logParamField: Field,
    val logKeyField: Field,
    val logValueField: Field,
)
