package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Restored lifecycle suppression and initialization-cache members owned by AutoRefreshContract. */
data class HomeRefreshFlowSymbols(
    val methods: Map<HomeRefreshMethod, List<Method>>,
    val constructors: Map<HomeRefreshCarrier, List<Constructor<*>>>,
    val fields: Map<HomeRefreshField, Field>,
) {
    init {
        require(methods.keys == HomeRefreshMethod.entries.toSet())
        require(constructors.keys == HomeRefreshCarrier.entries.toSet())
        require(fields.keys == HomeRefreshField.entries.toSet())
        require(methods.values.all { it.isNotEmpty() })
        require(constructors.values.all { it.isNotEmpty() })
    }

    fun method(role: HomeRefreshMethod): Method = methods.getValue(role).single()
    fun field(role: HomeRefreshField): Field = fields.getValue(role)
}

enum class HomeRefreshMethod {
    APP_INITIALIZE, INITIAL_PREFETCH,
    PAGE_INITIALIZE, CACHE_INITIALIZE, PAGE_RESUME, PAGE_FOREGROUND, PAGE_TRIGGER,
    REFRESH, NEW_REFRESH, CACHE_LOAD, DISPATCH, NEW_DISPATCH,
    CACHE_BACKGROUND, CACHE_POST, CACHE_CONTINUE, IDLE_CONTINUE, REFRESH_INDICATOR,
}

enum class HomeRefreshCarrier {
    CACHE_TASK, CACHE_CALLBACK,
}

enum class HomeRefreshField {
    FEED_OWNER_MODEL, NEW_FEED_CACHE, NEW_FEED_CACHE_LISTENER,
    CACHE_CONTINUATION_TASK, IDLE_CONTINUATION_TASK,
}
