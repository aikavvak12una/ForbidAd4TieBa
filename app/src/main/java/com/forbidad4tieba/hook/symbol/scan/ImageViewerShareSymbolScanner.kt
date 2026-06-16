package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.symbol.model.ImageViewerShareScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger

internal object ImageViewerShareSymbolScanner {
    private const val SHARE_ITEM_CLASS = "com.baidu.tbadk.coreExtra.share.ShareItem"
    private const val TIEBA_DRAWABLE_CLASS = "com.baidu.tieba.R\$drawable"

    fun scan(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ImageViewerShareScanSymbols {
        val shareTrack = scanSubStep("CleanShareTrackingParamsHook", logger, ImageViewerShareScanSymbols()) {
            scanShareTrack(candidates, cl, logger)
        }
        val item = scanSubStep("ImageViewerNativeShareHook.Item", logger, ImageViewerShareScanSymbols()) {
            scanItem(cl, logger)
        }
        val config = scanSubStep("ImageViewerNativeShareHook.Config", logger, ImageViewerShareScanSymbols()) {
            item.itemClass?.let { scanConfig(candidates, cl, logger, it) } ?: ImageViewerShareScanSymbols()
        }
        val itemView = scanSubStep("ImageViewerNativeShareHook.ItemView", logger, ImageViewerShareScanSymbols()) {
            scanItemView(candidates, cl, logger)
        }
        val icon = scanSubStep("ImageViewerNativeShareHook.Icon", logger, ImageViewerShareScanSymbols()) {
            scanIcon(context, candidates, cl, logger)
        }

        return ImageViewerShareScanSymbols(
            shareTrackBuilderClass = shareTrack.shareTrackBuilderClass,
            shareTrackBuildUrlMethod = shareTrack.shareTrackBuildUrlMethod,
            shareTrackAppendQueryMethod = shareTrack.shareTrackAppendQueryMethod,
            itemClass = item.itemClass,
            itemTitleField = item.itemTitleField,
            itemContentField = item.itemContentField,
            itemLinkUrlField = item.itemLinkUrlField,
            itemImageUriField = item.itemImageUriField,
            itemImageUrlField = item.itemImageUrlField,
            itemLocalFileField = item.itemLocalFileField,
            configClass = config.configClass,
            addOutsideMethod = config.addOutsideMethod,
            getRequestDataMethod = config.getRequestDataMethod,
            setRequestDataMethod = config.setRequestDataMethod,
            getContextMethod = config.getContextMethod,
            isDialogField = config.isDialogField,
            itemField = config.itemField,
            itemViewClass = itemView.itemViewClass,
            itemNameByResMethod = itemView.itemNameByResMethod,
            itemNameByTextMethod = itemView.itemNameByTextMethod,
            iconResId = icon.iconResId,
        )
    }

    private fun scanShareTrack(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ImageViewerShareScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(ShareTrackUrlBuilderRule()),
            logger,
            "shareTrack",
        ) ?: return ImageViewerShareScanSymbols()

        val parts = match.methodName.split(",")
        return ImageViewerShareScanSymbols(
            shareTrackBuilderClass = match.className,
            shareTrackBuildUrlMethod = parts.getOrNull(0)?.ifBlank { null },
            shareTrackAppendQueryMethod = parts.getOrNull(1)?.ifBlank { null },
        )
    }

    private fun scanItem(cl: ClassLoader, logger: ScanLogger?): ImageViewerShareScanSymbols {
        val match = ScanReflection.runRules(
            listOf(SHARE_ITEM_CLASS),
            cl,
            listOf(ImageViewerShareItemRule()),
            logger,
            "imageViewerShareItem",
        ) ?: return ImageViewerShareScanSymbols()

        val itemFields = unpackScanParts(match.fieldName, 6)
        return ImageViewerShareScanSymbols(
            itemClass = match.className,
            itemTitleField = itemFields[0],
            itemContentField = itemFields[1],
            itemLinkUrlField = itemFields[2],
            itemImageUriField = itemFields[3],
            itemImageUrlField = itemFields[4],
            itemLocalFileField = itemFields[5],
        )
    }

    private fun scanConfig(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
        shareItemClass: String,
    ): ImageViewerShareScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(ImageViewerShareConfigRule(shareItemClass)),
            logger,
            "imageViewerShareConfig",
        ) ?: return ImageViewerShareScanSymbols()

        val configMethods = unpackScanParts(match.methodName, 4)
        val configFields = unpackScanParts(match.fieldName, 2)
        return ImageViewerShareScanSymbols(
            configClass = match.className,
            addOutsideMethod = configMethods[0],
            getRequestDataMethod = configMethods[1],
            setRequestDataMethod = configMethods[2],
            getContextMethod = configMethods[3],
            isDialogField = configFields[0],
            itemField = configFields[1],
        )
    }

    private fun scanItemView(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ImageViewerShareScanSymbols {
        val match = ScanReflection.runRules(
            candidates,
            cl,
            listOf(ImageViewerShareItemViewRule()),
            logger,
            "imageViewerShareItemView",
        ) ?: return ImageViewerShareScanSymbols()

        val itemViewMethods = unpackScanParts(match.methodName, 2)
        return ImageViewerShareScanSymbols(
            itemViewClass = match.className,
            itemNameByResMethod = itemViewMethods[0],
            itemNameByTextMethod = itemViewMethods[1],
        )
    }

    private fun scanIcon(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): ImageViewerShareScanSymbols {
        val match = ScanReflection.runRules(
            listOf(TIEBA_DRAWABLE_CLASS),
            cl,
            listOf(ImageViewerShareIconResourceRule()),
            logger,
            "imageViewerShareIcon",
        )
        val iconResId = if (match != null) {
            match.fieldName.toIntOrNull()
        } else {
            ImageViewerShareIconSymbolScanner.scanFromDex(
                context = context,
                candidates = candidates,
                cl = cl,
                logger = logger,
            )
        }
        return ImageViewerShareScanSymbols(iconResId = iconResId)
    }

}
