package com.forbidad4tieba.hook.symbol.contract

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.TypedValue

/** Native defaults are decoded once at installation; pages only clone drawable state. */
class PostReplyLoadingArtwork private constructor(
    private val icons: List<Drawable.ConstantState>,
    private val iconDimension: Int,
    private val labelDimension: Int,
    private val lightColor: Int,
    private val darkColor: Int,
) {
    fun newIcons(): List<Drawable> = icons.map { it.newDrawable().mutate() }

    // Match the host controls' resource sizing, including its resource-density bucket.
    fun iconSize(metrics: DisplayMetrics): Int = TypedValue.complexToDimensionPixelSize(iconDimension, metrics)
    fun labelSize(metrics: DisplayMetrics): Int = TypedValue.complexToDimensionPixelSize(labelDimension, metrics)
    fun color(night: Boolean): Int = if (night) darkColor else lightColor

    companion object {
        // The standard reply row is comment, agree, collect; no account state or count is implied.
        private val iconNames = listOf(
            "icon_pure_interact_comment", "icon_pure_interact_agree", "icon_pure_interact_collect",
        )
        val actionCount: Int get() = iconNames.size

        internal fun resolve(resources: Resources): PostReplyLoadingArtwork {
            fun resource(name: String, type: String): Int {
                val id = resources.getIdentifier(name, type, "com.baidu.tieba")
                check(id != 0 && resources.getResourceTypeName(id) == type &&
                    resources.getResourceEntryName(id) == name) { "Missing reply loading $type/$name" }
                return id
            }
            fun dimension(name: String): Int {
                val value = TypedValue()
                resources.getValue(resource(name, "dimen"), value, true)
                check(value.type == TypedValue.TYPE_DIMENSION && TypedValue.complexToFloat(value.data) > 0f) {
                    "Invalid reply loading dimension/$name"
                }
                return value.data
            }
            val ids = iconNames.map { resource(it, "drawable") }
            check(ids.distinct().size == ids.size) { "Ambiguous reply loading artwork" }
            val icons = ids.map { id ->
                val drawable = resources.getDrawable(id, null)
                check(drawable.intrinsicWidth > 0 && drawable.intrinsicHeight == drawable.intrinsicWidth) {
                    "Invalid reply loading icon bounds"
                }
                checkNotNull(drawable.constantState) { "Reply loading icon cannot be copied" }
            }
            return PostReplyLoadingArtwork(icons, dimension("tbds57"), dimension("T_X10"),
                resources.getColor(resource("CAM_X0105", "color"), null),
                resources.getColor(resource("CAM_X0105_2", "color"), null))
        }
    }
}
