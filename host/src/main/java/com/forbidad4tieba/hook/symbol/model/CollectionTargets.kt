package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

data class CollectionSearchSymbols(
    val activityClass: Class<*>,
    val fragmentClass: Class<*>,
    val presenterField: Field,
    val presenterListSetterMethod: Method,
    val modelField: Field,
    val modelListGetterMethod: Method,
    val modelParseMethod: Method,
    val modelListField: Field,
    val fragmentDisplayListField: Field,
    val activityNavControllerField: Field?,
    val navBarField: Field,
    val presenterAdapterField: Field?,
    val editModeMethod: Method?,
    val currentAccountMethod: Method?,
    val mark: CollectionMarkTargets,
    val adapter: CollectionAdapterTargets?,
    val network: CollectionNetworkTargets?,
    val page: CollectionPageTargets,
)

data class CollectionMarkTargets(
    val titleMethod: Method?, val authorMethod: Method?, val forumMethod: Method?,
    val threadIdMethod: Method?, val postIdMethod: Method?, val idMethod: Method?,
)

data class CollectionAdapterTargets(
    val showFooterMethod: Method?, val loadingMethod: Method?, val hasMoreMethod: Method?,
    val countMethod: Method?, val enabledMethod: Method?, val viewMethod: Method?, val notifyMethod: Method?,
)

data class CollectionNetworkTargets(
    val netCtor: Constructor<*>,
    val addPostDataMethod: Method,
    val postNetDataMethod: Method,
    val serverAddressField: Field,
    val markGetStoreField: Field,
)

data class CollectionPageTargets(
    val supportManager: Method?, val childManager: Method?, val fragments: Method?,
    val isAdded: Method?, val activity: Method?,
)
