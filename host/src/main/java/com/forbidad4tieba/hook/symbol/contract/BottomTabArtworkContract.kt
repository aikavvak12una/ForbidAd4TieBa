package com.forbidad4tieba.hook.symbol.contract

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The native label identifies the icon/text layer; its sibling badges retain their own colors. */
object BottomTabArtworkContract {
    class Artwork(val content: View, val label: TextView)

    class Targets internal constructor(private val type: Class<*>, private val labelGetter: Method) {
        fun find(row: ViewGroup): List<Artwork>? {
            val result = ArrayList<Artwork>(row.childCount)
            for (index in 0 until row.childCount) {
                val tab = row.getChildAt(index) as? FrameLayout ?: return null
                if (!type.isInstance(tab) || tab.background != null || tab.foreground != null) return null
                val label = labelGetter.invoke(tab) as? TextView ?: return null
                var content: View = label
                var depth = 0
                while (content.parent !== tab && depth++ < 4) {
                    content = content.parent as? View ?: return null
                }
                if (content.parent !== tab) return null
                result.add(Artwork(content, label))
            }
            return result.takeIf { it.isNotEmpty() }
        }
    }

    fun resolve(classLoader: ClassLoader): Targets {
        val type = Class.forName("com.baidu.tbadk.mainTab.MainTabBottomOptIndicator", false, classLoader)
        return Targets(type, validate(type))
    }

    internal fun validate(type: Class<*>): Method {
        check(FrameLayout::class.java.isAssignableFrom(type)) { "Invalid Tab artwork container" }
        return type.getMethod("getContentTv").apply {
            check(!Modifier.isStatic(modifiers) && View::class.java.isAssignableFrom(returnType)) {
                "Invalid Tab label accessor"
            }
        }
    }
}
