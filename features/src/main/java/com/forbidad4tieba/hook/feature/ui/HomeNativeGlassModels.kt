package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.HomeGlassPreferences
import android.animation.StateListAnimator
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference

internal data class BackgroundRequest(
    val path: String,
    val blurCachePath: String,
    val blurPercent: Int,
    val targetWidth: Int,
    val targetHeight: Int,
) {
    val cacheKey: String = listOf(
        path,
        blurCachePath,
        blurPercent.toString(),
        targetWidth.toString(),
        targetHeight.toString(),
    ).joinToString("|")
}

internal data class CachedBackgroundBitmap(
    val cacheKey: String,
    val path: String,
    val lastModified: Long,
    val length: Long,
    val blurCachePath: String,
    val blurCacheLastModified: Long,
    val blurCacheLength: Long,
    val blurPercent: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val bitmap: Bitmap?,
    val blurredBitmap: Bitmap?,
    val memoryBytes: Long,
    @Volatile var metadataCheckedAt: Long,
)

internal data class BackgroundFileMetadata(
    val lastModified: Long,
    val length: Long,
)

internal data class HomeNativeGlassRuntimeStyle(
    val darkMode: Boolean,
    val backgroundImagePath: String,
    val blurCacheImagePath: String,
    val tintColor: Int,
    val autoTintColor: Int,
    val tintAlphaPercent: Int,
    val cardBlurPercent: Int,
    val cardRadiusDp: Int,
    val strokeEnabled: Boolean,
    val shadowStrengthPercent: Int,
) {
    val hasBackgroundImage: Boolean get() = backgroundImagePath.isNotBlank()

    companion object {
        val EMPTY = HomeNativeGlassRuntimeStyle(
            darkMode = false,
            backgroundImagePath = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_BACKGROUND_IMAGE_PATH,
            blurCacheImagePath = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_BLUR_CACHE_IMAGE_PATH,
            tintColor = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_COLOR,
            autoTintColor = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_AUTO_TINT_COLOR,
            tintAlphaPercent = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_TINT_ALPHA_PERCENT,
            cardBlurPercent = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_CARD_BLUR_PERCENT,
            cardRadiusDp = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_CARD_RADIUS_DP,
            strokeEnabled = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_STROKE_ENABLED,
            shadowStrengthPercent = HomeGlassPreferences.DEFAULT_HOME_NATIVE_GLASS_SHADOW_STRENGTH_PERCENT,
        )
    }
}

internal data class RuntimeTargets(
    val homeTabItemTypeField: String?,
    val homeTabItemCodeField: String?,
    val searchBoxClass: String?,
    val homeSearchBoxOwnerClass: String?,
    val homeSearchBoxInitMethod: String?,
    val homeSearchBoxGetterMethod: String?,
    val subPbNextPageMoreViewId: Int?,
    val pbReplyTitleDividerViewId: Int?,
    val dynamicBackgroundColorIds: Set<Int>,
    val topChromeTabSelectedMethods: List<HomeTopChromeTabSelectedTarget>,
    val subPbSetNextPageTarget: HomeSubPbSetNextPageTarget?,
    val sortSwitchBackgroundPaintField: String?,
    val sortSwitchSlideDrawMethod: String?,
    val sortSwitchSlidePathField: String?,
    val enterForumCapsuleControllerClass: String?,
    val enterForumCapsuleInitMethod: String?,
    val enterForumCapsuleRefreshMethod: String?,
    val enterForumCapsuleViewField: String?,
    val enterForumCapsuleTitleField: String?,
    val pbCommonLayoutPreloaderGetOrDefaultMethod: String?,
)

internal data class HomeTopChromeTabSelectedTarget(
    val className: String,
    val methodName: String,
)

internal data class HomeSubPbSetNextPageTarget(
    val methodName: String,
    val parameterTypeName: String,
)

internal data class ChromeGlassOriginalState(
    val background: Drawable?,
    val foreground: Drawable?,
    val stateListAnimator: StateListAnimator?,
    val elevation: Float,
    val translationZ: Float,
)

internal data class PbCommentBackgroundState(
    val blurCachePath: String,
    val sourcePath: String,
    val tintAlphaPercent: Int,
)

internal enum class PbCommentBackgroundWriteRole {
    IGNORE,
    CLEAR,
    SORT_SWITCH,
    SUB_PB_LAYOUT,
    HOST,
    KEEP,
}

internal data class PbSortSwitchTintState(
    val sourcePath: String,
    val blurCachePath: String,
    val blurPercent: Int,
    val tintColor: Int,
    val autoTintColor: Int,
    val pinnedReplyTitle: Boolean,
    val backgroundColor: Int?,
    val selectedColor: Int?,
)

internal data class PbSubPbLayoutPaddingState(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class SubPbInputBarMatch(
    val root: ViewGroup,
    val capsule: View,
    val container: View?,
)

internal data class EnterForumCapsuleOriginalState(
    val background: Drawable?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal class PendingViewGroupApply(parent: ViewGroup?) {
    var parentRef: WeakReference<ViewGroup>? = parent?.let(::WeakReference)
}
