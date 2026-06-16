package com.forbidad4tieba.hook.symbol.model

data class CollectionHistorySymbols(
    val collection: CollectionSymbolsGroup = CollectionSymbolsGroup(),
    val history: HistorySymbolsGroup = HistorySymbolsGroup(),
    val freeCopy: FreeCopySymbolsGroup = FreeCopySymbolsGroup(),
)

data class CollectionSymbolsGroup(
    val presenter: CollectionPresenterSymbolsGroup = CollectionPresenterSymbolsGroup(),
    val model: CollectionModelSymbolsGroup = CollectionModelSymbolsGroup(),
    val fragment: CollectionFragmentSymbolsGroup = CollectionFragmentSymbolsGroup(),
    val adapter: CollectionAdapterSymbolsGroup = CollectionAdapterSymbolsGroup(),
) {
    fun isSearchComplete(): Boolean = hasRequired(
        presenter.collectionPresenterField,
        presenter.collectionPresenterListSetterMethod,
        model.collectionModelField,
        model.collectionModelListGetterMethod,
        model.collectionModelParseMethod,
        model.collectionModelListField,
        fragment.collectionFragmentDisplayListField,
        fragment.collectionNavBarField,
    )
}

data class CollectionPresenterSymbolsGroup(
    val collectionPresenterField: String? = null,
    val collectionPresenterListSetterMethod: String? = null,
    val collectionPresenterAdapterField: String? = null,
)

data class CollectionModelSymbolsGroup(
    val collectionModelField: String? = null,
    val collectionModelListGetterMethod: String? = null,
    val collectionModelParseMethod: String? = null,
    val collectionModelListField: String? = null,
)

data class CollectionFragmentSymbolsGroup(
    val collectionFragmentDisplayListField: String? = null,
    val collectionActivityNavControllerField: String? = null,
    val collectionNavBarField: String? = null,
)

data class CollectionAdapterSymbolsGroup(
    val collectionAdapterShowFooterMethod: String? = null,
    val collectionAdapterLoadingMethod: String? = null,
    val collectionAdapterHasMoreMethod: String? = null,
    val collectionEditModeMethod: String? = null,
)

data class HistorySymbolsGroup(
    val activity: HistoryActivitySymbolsGroup = HistoryActivitySymbolsGroup(),
    val threadData: HistoryThreadDataSymbolsGroup = HistoryThreadDataSymbolsGroup(),
) {
    fun isSearchComplete(): Boolean = hasRequired(
        activity.historyAdapterField,
        activity.historyAdapterSetListMethod,
        activity.historyListField,
        activity.historyActivityNavBarField,
        threadData.historyThreadNameMethod,
        threadData.historyForumNameMethod,
        threadData.historyUserNameMethod,
        threadData.historyDescriptionMethod,
        threadData.historyThreadIdMethod,
        threadData.historyPostIdMethod,
        threadData.historyLiveIdMethod,
    )
}

data class HistoryActivitySymbolsGroup(
    val historyAdapterField: String? = null,
    val historyAdapterSetListMethod: String? = null,
    val historyListField: String? = null,
    val historyActivityListUpdateMethod: String? = null,
    val historyActivityNavBarField: String? = null,
)

data class HistoryThreadDataSymbolsGroup(
    val historyThreadNameMethod: String? = null,
    val historyForumNameMethod: String? = null,
    val historyUserNameMethod: String? = null,
    val historyDescriptionMethod: String? = null,
    val historyThreadIdMethod: String? = null,
    val historyPostIdMethod: String? = null,
    val historyLiveIdMethod: String? = null,
)

data class FreeCopySymbolsGroup(
    val freeCopyPopupMenuClass: String? = null,
    val freeCopyPopupContentViewMethod: String? = null,
    val freeCopyPopupTextField: String? = null,
)

private fun hasRequired(vararg values: String?): Boolean {
    return values.all { !it.isNullOrBlank() }
}
