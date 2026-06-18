package com.forbidad4tieba.hook.symbol.model

data class ImageSymbols(
    val original: OriginalImageSymbolsGroup = OriginalImageSymbolsGroup(),
    val shareTrack: ShareTrackSymbolsGroup = ShareTrackSymbolsGroup(),
    val viewerShare: ImageViewerShareSymbolsGroup = ImageViewerShareSymbolsGroup(),
)

data class OriginalImageSymbolsGroup(
    val component: OriginalImageComponentSymbolsGroup = OriginalImageComponentSymbolsGroup(),
    val data: OriginalImageDataSymbolsGroup = OriginalImageDataSymbolsGroup(),
    val sharedPref: OriginalImageSharedPrefSymbolsGroup = OriginalImageSharedPrefSymbolsGroup(),
    val md5: OriginalImageMd5SymbolsGroup = OriginalImageMd5SymbolsGroup(),
    val trigger: OriginalImageTriggerSymbolsGroup = OriginalImageTriggerSymbolsGroup(),
)

data class OriginalImageComponentSymbolsGroup(
    val origImagePagerAdapterClass: String? = null,
    val origImageUrlDragImageViewClass: String? = null,
    val origImageDataClass: String? = null,
    val origImageSetPrimaryItemMethod: String? = null,
    val origImageSetAssistUrlMethod: String? = null,
)

data class OriginalImageDataSymbolsGroup(
    val origImageAssistDataMethod: String? = null,
    val origImageOriginTextMethod: String? = null,
    val origImageShowButtonField: String? = null,
    val origImageBlockedField: String? = null,
    val origImageOriginalProcessField: String? = null,
    val origImageOriginalUrlField: String? = null,
)

data class OriginalImageSharedPrefSymbolsGroup(
    val origImageSharedPrefHelperClass: String? = null,
    val origImageSharedPrefGetInstanceMethod: String? = null,
    val origImageSharedPrefPutBooleanMethod: String? = null,
)

data class OriginalImageMd5SymbolsGroup(
    val origImageMd5Class: String? = null,
    val origImageMd5Method: String? = null,
)

data class OriginalImageTriggerSymbolsGroup(
    val origImagePrimaryReadyMethod: String? = null,
    val origImageTriggerMethod: String? = null,
    val origImageDirectStartMethod: String? = null,
)

data class ShareTrackSymbolsGroup(
    val shareTrackBuilderClass: String? = null,
    val shareTrackBuildUrlMethod: String? = null,
    val shareTrackAppendQueryMethod: String? = null,
)

data class ImageViewerShareSymbolsGroup(
    val config: ImageViewerShareConfigSymbolsGroup = ImageViewerShareConfigSymbolsGroup(),
    val request: ImageViewerShareRequestSymbolsGroup = ImageViewerShareRequestSymbolsGroup(),
    val item: ImageViewerShareItemSymbolsGroup = ImageViewerShareItemSymbolsGroup(),
    val itemView: ImageViewerShareItemViewSymbolsGroup = ImageViewerShareItemViewSymbolsGroup(),
)

data class ImageViewerShareConfigSymbolsGroup(
    val imageViewerShareConfigClass: String? = null,
    val imageViewerShareIsDialogField: String? = null,
    val imageViewerShareItemField: String? = null,
    val imageViewerShareAddOutsideMethod: String? = null,
)

data class ImageViewerShareRequestSymbolsGroup(
    val imageViewerShareGetRequestDataMethod: String? = null,
    val imageViewerShareSetRequestDataMethod: String? = null,
    val imageViewerShareGetContextMethod: String? = null,
)

data class ImageViewerShareItemSymbolsGroup(
    val imageViewerShareItemClass: String? = null,
    val imageViewerShareItemTitleField: String? = null,
    val imageViewerShareItemContentField: String? = null,
    val imageViewerShareItemLinkUrlField: String? = null,
    val imageViewerShareItemImageUriField: String? = null,
    val imageViewerShareItemImageUrlField: String? = null,
)

data class ImageViewerShareItemViewSymbolsGroup(
    val imageViewerShareItemLocalFileField: String? = null,
    val imageViewerShareItemViewClass: String? = null,
    val imageViewerShareItemNameByResMethod: String? = null,
    val imageViewerShareItemNameByTextMethod: String? = null,
)

data class AiSymbols(
    val spriteMeme: AiSpriteMemeSymbolsGroup = AiSpriteMemeSymbolsGroup(),
    val pbInput: AiPbInputSymbolsGroup = AiPbInputSymbolsGroup(),
    val emojiCreation: AiEmojiCreationSymbolsGroup = AiEmojiCreationSymbolsGroup(),
    val imageViewerJumpButton: AiImageViewerJumpButtonSymbolsGroup = AiImageViewerJumpButtonSymbolsGroup(),
)

data class AiSpriteMemeSymbolsGroup(
    val aiSpriteMemePanControllerClass: String? = null,
    val aiSpriteMemeEnableMethod: String? = null,
)

data class AiPbInputSymbolsGroup(
    val aiPbNewInputContainerClass: String? = null,
    val aiPbNewInputContainerInitSpriteMemeMethod: String? = null,
    val aiPbNewInputContainerInitAiWriteMethod: String? = null,
)

data class AiEmojiCreationSymbolsGroup(
    val aiPbAiEmojiCreationViewBindMethod: String? = null,
    val aiPbPageBrowserAiEmojiCreationViewClass: String? = null,
    val aiPbPageBrowserAiEmojiCreationBindMethod: String? = null,
)

data class AiImageViewerJumpButtonSymbolsGroup(
    val aiImageViewerJumpButtonOwnerClass: String? = null,
    val aiImageViewerJumpButtonInitMethod: String? = null,
)
