package com.forbidad4tieba.hook.symbol.model

import org.json.JSONArray
import org.json.JSONObject

internal interface FeatureSymbols {
    val featureKey: String
    fun missingFields(): List<String>
    fun isComplete(): Boolean = missingFields().isEmpty()
    fun toJson(): JSONObject
}

internal data class HookFeatureSymbols(
    val homeTab: HomeTabSymbols,
    val mainTab: MainTabSymbols,
    val share: ShareSymbols,
    val originalImage: OriginalImageSymbols,
    val freeCopy: FreeCopySymbols,
    val performance: PerformanceSymbols,
) {
    fun all(): List<FeatureSymbols> = listOf(
        homeTab,
        mainTab,
        share,
        originalImage,
        freeCopy,
        performance,
    )
}

internal data class HomeTabSymbols(
    val homeTabClass: String?,
    val homeTabRebuildMethod: String?,
    val homeTabListField: String?,
    val homeTabItemTypeField: String?,
    val homeTabItemCodeField: String?,
    val homeTabItemNameField: String?,
    val homeTabItemUrlField: String?,
    val homeTabItemMainSetterMethod: String?,
    val homeTabItemMainIntField: String?,
    val homeTabItemMainBooleanField: String?,
) : FeatureSymbols {
    override val featureKey: String = HookFeatureKey.SIMPLIFY_HOME_TOP_TABS

    override fun missingFields(): List<String> = missingRequired(
        "homeTabClass" to homeTabClass,
        "homeTabRebuildMethod" to homeTabRebuildMethod,
        "homeTabListField" to homeTabListField,
        "homeTabItemTypeField" to homeTabItemTypeField,
        "homeTabItemCodeField" to homeTabItemCodeField,
        "homeTabItemNameField" to homeTabItemNameField,
        "homeTabItemUrlField" to homeTabItemUrlField,
        "homeTabItemMainSetterMethod" to homeTabItemMainSetterMethod,
        "homeTabItemMainIntField" to homeTabItemMainIntField,
        "homeTabItemMainBooleanField" to homeTabItemMainBooleanField,
    )

    override fun toJson(): JSONObject = JSONObject()
        .putFeatureMeta(this)
        .putNullable("homeTabClass", homeTabClass)
        .putNullable("homeTabRebuildMethod", homeTabRebuildMethod)
        .putNullable("homeTabListField", homeTabListField)
        .putNullable("homeTabItemTypeField", homeTabItemTypeField)
        .putNullable("homeTabItemCodeField", homeTabItemCodeField)
        .putNullable("homeTabItemNameField", homeTabItemNameField)
        .putNullable("homeTabItemUrlField", homeTabItemUrlField)
        .putNullable("homeTabItemMainSetterMethod", homeTabItemMainSetterMethod)
        .putNullable("homeTabItemMainIntField", homeTabItemMainIntField)
        .putNullable("homeTabItemMainBooleanField", homeTabItemMainBooleanField)
}

internal data class MainTabSymbols(
    val mainTabDataClass: String?,
    val mainTabAddMethod: String?,
    val mainTabGetListMethod: String?,
    val mainTabDelegateGetStructureMethod: String?,
    val mainTabStructureTypeField: String?,
    val mainTabStructureDynamicIconField: String?,
    val mainTabStructureFragmentField: String?,
) : FeatureSymbols {
    override val featureKey: String = HookFeatureKey.SIMPLIFY_BOTTOM_TABS

    override fun missingFields(): List<String> = missingRequired(
        "mainTabDataClass" to mainTabDataClass,
        "mainTabAddMethod" to mainTabAddMethod,
        "mainTabGetListMethod" to mainTabGetListMethod,
        "mainTabDelegateGetStructureMethod" to mainTabDelegateGetStructureMethod,
        "mainTabStructureTypeField" to mainTabStructureTypeField,
        "mainTabStructureDynamicIconField" to mainTabStructureDynamicIconField,
        "mainTabStructureFragmentField" to mainTabStructureFragmentField,
    )

    override fun toJson(): JSONObject = JSONObject()
        .putFeatureMeta(this)
        .putNullable("mainTabDataClass", mainTabDataClass)
        .putNullable("mainTabAddMethod", mainTabAddMethod)
        .putNullable("mainTabGetListMethod", mainTabGetListMethod)
        .putNullable("mainTabDelegateGetStructureMethod", mainTabDelegateGetStructureMethod)
        .putNullable("mainTabStructureTypeField", mainTabStructureTypeField)
        .putNullable("mainTabStructureDynamicIconField", mainTabStructureDynamicIconField)
        .putNullable("mainTabStructureFragmentField", mainTabStructureFragmentField)
}

internal data class ShareSymbols(
    val shareTrackBuilderClass: String?,
    val shareTrackBuildUrlMethod: String?,
    val shareTrackAppendQueryMethod: String?,
    val imageViewerShareConfigClass: String?,
    val imageViewerShareIsDialogField: String?,
    val imageViewerShareItemField: String?,
    val imageViewerShareAddOutsideMethod: String?,
    val imageViewerShareGetRequestDataMethod: String?,
    val imageViewerShareSetRequestDataMethod: String?,
    val imageViewerShareGetContextMethod: String?,
    val imageViewerShareItemClass: String?,
    val imageViewerShareItemTitleField: String?,
    val imageViewerShareItemContentField: String?,
    val imageViewerShareItemLinkUrlField: String?,
    val imageViewerShareItemImageUriField: String?,
    val imageViewerShareItemImageUrlField: String?,
    val imageViewerShareItemLocalFileField: String?,
    val imageViewerShareItemViewClass: String?,
    val imageViewerShareItemNameByResMethod: String?,
    val imageViewerShareItemNameByTextMethod: String?,
    val imageViewerShareIconResId: Int?,
) : FeatureSymbols {
    override val featureKey: String = HookFeatureKey.CLEAN_SHARE_TRACKING_PARAMS

    override fun missingFields(): List<String> = missingRequired(
        "shareTrackBuilderClass" to shareTrackBuilderClass,
        "shareTrackBuildUrlMethod" to shareTrackBuildUrlMethod,
        "shareTrackAppendQueryMethod" to shareTrackAppendQueryMethod,
        "imageViewerShareConfigClass" to imageViewerShareConfigClass,
        "imageViewerShareItemClass" to imageViewerShareItemClass,
        "imageViewerShareItemViewClass" to imageViewerShareItemViewClass,
        "imageViewerShareIconResId" to imageViewerShareIconResId,
    )

    fun nativeShareMissingFields(): List<String> = missingRequired(
        "imageViewerShareConfigClass" to imageViewerShareConfigClass,
        "imageViewerShareItemClass" to imageViewerShareItemClass,
        "imageViewerShareItemViewClass" to imageViewerShareItemViewClass,
        "imageViewerShareIconResId" to imageViewerShareIconResId,
    )

    fun isNativeShareComplete(): Boolean = nativeShareMissingFields().isEmpty()

    override fun toJson(): JSONObject = JSONObject()
        .putFeatureMeta(this)
        .putNullable("shareTrackBuilderClass", shareTrackBuilderClass)
        .putNullable("shareTrackBuildUrlMethod", shareTrackBuildUrlMethod)
        .putNullable("shareTrackAppendQueryMethod", shareTrackAppendQueryMethod)
        .putNullable("imageViewerShareConfigClass", imageViewerShareConfigClass)
        .putNullable("imageViewerShareIsDialogField", imageViewerShareIsDialogField)
        .putNullable("imageViewerShareItemField", imageViewerShareItemField)
        .putNullable("imageViewerShareAddOutsideMethod", imageViewerShareAddOutsideMethod)
        .putNullable("imageViewerShareGetRequestDataMethod", imageViewerShareGetRequestDataMethod)
        .putNullable("imageViewerShareSetRequestDataMethod", imageViewerShareSetRequestDataMethod)
        .putNullable("imageViewerShareGetContextMethod", imageViewerShareGetContextMethod)
        .putNullable("imageViewerShareItemClass", imageViewerShareItemClass)
        .putNullable("imageViewerShareItemTitleField", imageViewerShareItemTitleField)
        .putNullable("imageViewerShareItemContentField", imageViewerShareItemContentField)
        .putNullable("imageViewerShareItemLinkUrlField", imageViewerShareItemLinkUrlField)
        .putNullable("imageViewerShareItemImageUriField", imageViewerShareItemImageUriField)
        .putNullable("imageViewerShareItemImageUrlField", imageViewerShareItemImageUrlField)
        .putNullable("imageViewerShareItemLocalFileField", imageViewerShareItemLocalFileField)
        .putNullable("imageViewerShareItemViewClass", imageViewerShareItemViewClass)
        .putNullable("imageViewerShareItemNameByResMethod", imageViewerShareItemNameByResMethod)
        .putNullable("imageViewerShareItemNameByTextMethod", imageViewerShareItemNameByTextMethod)
        .putNullable("imageViewerShareIconResId", imageViewerShareIconResId)
}

internal data class OriginalImageSymbols(
    val origImagePagerAdapterClass: String?,
    val origImageUrlDragImageViewClass: String?,
    val origImageDataClass: String?,
    val origImageSetPrimaryItemMethod: String?,
    val origImageSetAssistUrlMethod: String?,
    val origImageAssistDataMethod: String?,
    val origImageOriginTextMethod: String?,
    val origImageShowButtonField: String?,
    val origImageBlockedField: String?,
    val origImageOriginalProcessField: String?,
    val origImageOriginalUrlField: String?,
    val origImageSharedPrefHelperClass: String?,
    val origImageSharedPrefGetInstanceMethod: String?,
    val origImageSharedPrefPutBooleanMethod: String?,
    val origImageMd5Class: String?,
    val origImageMd5Method: String?,
    val origImagePrimaryReadyMethod: String?,
    val origImageTriggerMethod: String?,
    val origImageDirectStartMethod: String?,
) : FeatureSymbols {
    override val featureKey: String = HookFeatureKey.DEFAULT_ORIGINAL_IMAGE

    override fun missingFields(): List<String> = missingRequired(
        "origImageUrlDragImageViewClass" to origImageUrlDragImageViewClass,
        "origImageDataClass" to origImageDataClass,
        "origImageAssistDataMethod" to origImageAssistDataMethod,
        "origImageShowButtonField" to origImageShowButtonField,
        "origImageBlockedField" to origImageBlockedField,
        "origImageOriginalProcessField" to origImageOriginalProcessField,
        "origImageOriginalUrlField" to origImageOriginalUrlField,
        "origImageTriggerMethod" to origImageTriggerMethod,
    )

    override fun toJson(): JSONObject = JSONObject()
        .putFeatureMeta(this)
        .putNullable("origImagePagerAdapterClass", origImagePagerAdapterClass)
        .putNullable("origImageUrlDragImageViewClass", origImageUrlDragImageViewClass)
        .putNullable("origImageDataClass", origImageDataClass)
        .putNullable("origImageSetPrimaryItemMethod", origImageSetPrimaryItemMethod)
        .putNullable("origImageSetAssistUrlMethod", origImageSetAssistUrlMethod)
        .putNullable("origImageAssistDataMethod", origImageAssistDataMethod)
        .putNullable("origImageOriginTextMethod", origImageOriginTextMethod)
        .putNullable("origImageShowButtonField", origImageShowButtonField)
        .putNullable("origImageBlockedField", origImageBlockedField)
        .putNullable("origImageOriginalProcessField", origImageOriginalProcessField)
        .putNullable("origImageOriginalUrlField", origImageOriginalUrlField)
        .putNullable("origImageSharedPrefHelperClass", origImageSharedPrefHelperClass)
        .putNullable("origImageSharedPrefGetInstanceMethod", origImageSharedPrefGetInstanceMethod)
        .putNullable("origImageSharedPrefPutBooleanMethod", origImageSharedPrefPutBooleanMethod)
        .putNullable("origImageMd5Class", origImageMd5Class)
        .putNullable("origImageMd5Method", origImageMd5Method)
        .putNullable("origImagePrimaryReadyMethod", origImagePrimaryReadyMethod)
        .putNullable("origImageTriggerMethod", origImageTriggerMethod)
        .putNullable("origImageDirectStartMethod", origImageDirectStartMethod)
}

internal data class FreeCopySymbols(
    val freeCopyPopupMenuClass: String?,
    val freeCopyPopupContentViewMethod: String?,
    val freeCopyPopupTextField: String?,
) : FeatureSymbols {
    override val featureKey: String = HookFeatureKey.FREE_COPY

    override fun missingFields(): List<String> = missingRequired(
        "freeCopyPopupMenuClass" to freeCopyPopupMenuClass,
        "freeCopyPopupContentViewMethod" to freeCopyPopupContentViewMethod,
        "freeCopyPopupTextField" to freeCopyPopupTextField,
    )

    override fun toJson(): JSONObject = JSONObject()
        .putFeatureMeta(this)
        .putNullable("freeCopyPopupMenuClass", freeCopyPopupMenuClass)
        .putNullable("freeCopyPopupContentViewMethod", freeCopyPopupContentViewMethod)
        .putNullable("freeCopyPopupTextField", freeCopyPopupTextField)
}

internal data class PerformanceSymbols(
    val aiSpriteMemePanControllerClass: String?,
    val aiSpriteMemeEnableMethod: String?,
    val aiPbNewInputContainerClass: String?,
    val aiPbNewInputContainerInitSpriteMemeMethod: String?,
    val aiPbNewInputContainerInitAiWriteMethod: String?,
    val aiPbAiEmojiCreationViewBindMethod: String?,
    val aiPbPageBrowserAiEmojiCreationBindMethod: String?,
    val aiImageViewerJumpButtonOwnerClass: String?,
    val aiImageViewerJumpButtonInitMethod: String?,
) : FeatureSymbols {
    override val featureKey: String = HookFeatureKey.DISABLE_AI_COMPONENTS

    override fun missingFields(): List<String> = missingRequired(
        "aiSpriteMemePanControllerClass" to aiSpriteMemePanControllerClass,
        "aiSpriteMemeEnableMethod" to aiSpriteMemeEnableMethod,
        "aiPbNewInputContainerClass" to aiPbNewInputContainerClass,
        "aiPbNewInputContainerInitSpriteMemeMethod" to aiPbNewInputContainerInitSpriteMemeMethod,
        "aiPbNewInputContainerInitAiWriteMethod" to aiPbNewInputContainerInitAiWriteMethod,
    )

    fun imageViewerJumpButtonMissingFields(): List<String> = missingRequired(
        "aiImageViewerJumpButtonOwnerClass" to aiImageViewerJumpButtonOwnerClass,
        "aiImageViewerJumpButtonInitMethod" to aiImageViewerJumpButtonInitMethod,
    )

    fun isImageViewerJumpButtonComplete(): Boolean = imageViewerJumpButtonMissingFields().isEmpty()

    override fun toJson(): JSONObject = JSONObject()
        .putFeatureMeta(this)
        .putNullable("aiSpriteMemePanControllerClass", aiSpriteMemePanControllerClass)
        .putNullable("aiSpriteMemeEnableMethod", aiSpriteMemeEnableMethod)
        .putNullable("aiPbNewInputContainerClass", aiPbNewInputContainerClass)
        .putNullable("aiPbNewInputContainerInitSpriteMemeMethod", aiPbNewInputContainerInitSpriteMemeMethod)
        .putNullable("aiPbNewInputContainerInitAiWriteMethod", aiPbNewInputContainerInitAiWriteMethod)
        .putNullable("aiPbAiEmojiCreationViewBindMethod", aiPbAiEmojiCreationViewBindMethod)
        .putNullable("aiPbPageBrowserAiEmojiCreationBindMethod", aiPbPageBrowserAiEmojiCreationBindMethod)
        .putNullable("aiImageViewerJumpButtonOwnerClass", aiImageViewerJumpButtonOwnerClass)
        .putNullable("aiImageViewerJumpButtonInitMethod", aiImageViewerJumpButtonInitMethod)
}

internal fun HookSymbols.toFeatureSymbols(): HookFeatureSymbols {
    return HookFeatureSymbols(
        homeTab = HomeTabSymbols(
            homeTabClass = homeTabClass,
            homeTabRebuildMethod = homeTabRebuildMethod,
            homeTabListField = homeTabListField,
            homeTabItemTypeField = homeTabItemTypeField,
            homeTabItemCodeField = homeTabItemCodeField,
            homeTabItemNameField = homeTabItemNameField,
            homeTabItemUrlField = homeTabItemUrlField,
            homeTabItemMainSetterMethod = homeTabItemMainSetterMethod,
            homeTabItemMainIntField = homeTabItemMainIntField,
            homeTabItemMainBooleanField = homeTabItemMainBooleanField,
        ),
        mainTab = MainTabSymbols(
            mainTabDataClass = mainTabDataClass,
            mainTabAddMethod = mainTabAddMethod,
            mainTabGetListMethod = mainTabGetListMethod,
            mainTabDelegateGetStructureMethod = mainTabDelegateGetStructureMethod,
            mainTabStructureTypeField = mainTabStructureTypeField,
            mainTabStructureDynamicIconField = mainTabStructureDynamicIconField,
            mainTabStructureFragmentField = mainTabStructureFragmentField,
        ),
        share = ShareSymbols(
            shareTrackBuilderClass = shareTrackBuilderClass,
            shareTrackBuildUrlMethod = shareTrackBuildUrlMethod,
            shareTrackAppendQueryMethod = shareTrackAppendQueryMethod,
            imageViewerShareConfigClass = imageViewerShareConfigClass,
            imageViewerShareIsDialogField = imageViewerShareIsDialogField,
            imageViewerShareItemField = imageViewerShareItemField,
            imageViewerShareAddOutsideMethod = imageViewerShareAddOutsideMethod,
            imageViewerShareGetRequestDataMethod = imageViewerShareGetRequestDataMethod,
            imageViewerShareSetRequestDataMethod = imageViewerShareSetRequestDataMethod,
            imageViewerShareGetContextMethod = imageViewerShareGetContextMethod,
            imageViewerShareItemClass = imageViewerShareItemClass,
            imageViewerShareItemTitleField = imageViewerShareItemTitleField,
            imageViewerShareItemContentField = imageViewerShareItemContentField,
            imageViewerShareItemLinkUrlField = imageViewerShareItemLinkUrlField,
            imageViewerShareItemImageUriField = imageViewerShareItemImageUriField,
            imageViewerShareItemImageUrlField = imageViewerShareItemImageUrlField,
            imageViewerShareItemLocalFileField = imageViewerShareItemLocalFileField,
            imageViewerShareItemViewClass = imageViewerShareItemViewClass,
            imageViewerShareItemNameByResMethod = imageViewerShareItemNameByResMethod,
            imageViewerShareItemNameByTextMethod = imageViewerShareItemNameByTextMethod,
            imageViewerShareIconResId = imageViewerShareIconResId,
        ),
        originalImage = OriginalImageSymbols(
            origImagePagerAdapterClass = origImagePagerAdapterClass,
            origImageUrlDragImageViewClass = origImageUrlDragImageViewClass,
            origImageDataClass = origImageDataClass,
            origImageSetPrimaryItemMethod = origImageSetPrimaryItemMethod,
            origImageSetAssistUrlMethod = origImageSetAssistUrlMethod,
            origImageAssistDataMethod = origImageAssistDataMethod,
            origImageOriginTextMethod = origImageOriginTextMethod,
            origImageShowButtonField = origImageShowButtonField,
            origImageBlockedField = origImageBlockedField,
            origImageOriginalProcessField = origImageOriginalProcessField,
            origImageOriginalUrlField = origImageOriginalUrlField,
            origImageSharedPrefHelperClass = origImageSharedPrefHelperClass,
            origImageSharedPrefGetInstanceMethod = origImageSharedPrefGetInstanceMethod,
            origImageSharedPrefPutBooleanMethod = origImageSharedPrefPutBooleanMethod,
            origImageMd5Class = origImageMd5Class,
            origImageMd5Method = origImageMd5Method,
            origImagePrimaryReadyMethod = origImagePrimaryReadyMethod,
            origImageTriggerMethod = origImageTriggerMethod,
            origImageDirectStartMethod = origImageDirectStartMethod,
        ),
        freeCopy = FreeCopySymbols(
            freeCopyPopupMenuClass = freeCopyPopupMenuClass,
            freeCopyPopupContentViewMethod = freeCopyPopupContentViewMethod,
            freeCopyPopupTextField = freeCopyPopupTextField,
        ),
        performance = PerformanceSymbols(
            aiSpriteMemePanControllerClass = aiSpriteMemePanControllerClass,
            aiSpriteMemeEnableMethod = aiSpriteMemeEnableMethod,
            aiPbNewInputContainerClass = aiPbNewInputContainerClass,
            aiPbNewInputContainerInitSpriteMemeMethod = aiPbNewInputContainerInitSpriteMemeMethod,
            aiPbNewInputContainerInitAiWriteMethod = aiPbNewInputContainerInitAiWriteMethod,
            aiPbAiEmojiCreationViewBindMethod = aiPbAiEmojiCreationViewBindMethod,
            aiPbPageBrowserAiEmojiCreationBindMethod = aiPbPageBrowserAiEmojiCreationBindMethod,
            aiImageViewerJumpButtonOwnerClass = aiImageViewerJumpButtonOwnerClass,
            aiImageViewerJumpButtonInitMethod = aiImageViewerJumpButtonInitMethod,
        ),
    )
}

private fun missingRequired(vararg fields: Pair<String, Any?>): List<String> {
    return fields.asSequence()
        .filter { (_, value) ->
            when (value) {
                null -> true
                is String -> value.isBlank()
                is Int -> value == 0
                else -> false
            }
        }
        .map { it.first }
        .toList()
}

private fun JSONObject.putFeatureMeta(symbols: FeatureSymbols): JSONObject {
    put("featureKey", symbols.featureKey)
    val missing = symbols.missingFields()
    put("complete", missing.isEmpty())
    put("missingFields", JSONArray(missing))
    return this
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject {
    put(name, value ?: JSONObject.NULL)
    return this
}

private fun JSONObject.putNullable(name: String, value: Int?): JSONObject {
    put(name, value ?: JSONObject.NULL)
    return this
}
