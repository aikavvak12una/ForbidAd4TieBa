package com.forbidad4tieba.hook.symbol.contract

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test

class RedDotContractTest {
    @Test fun messageTargetRequiresItsOwnDrawAndAStringReader() {
        val target = RedDotContract.messageTargets(Badge::class.java)
        assertEquals(Badge::class.java, target.draw.declaringClass)
        assertEquals(listOf(Canvas::class.java), target.draw.parameterTypes.toList())
        assertEquals(String::class.java, target.text.returnType)
        for (type in listOf(WrongReader::class.java, StaticReader::class.java, NotAView::class.java)) {
            assertThrows(IllegalStateException::class.java) { RedDotContract.messageTargets(type) }
        }
        assertThrows(NoSuchMethodException::class.java) {
            RedDotContract.messageTargets(InheritedDraw::class.java)
        }
    }

    @Test fun visibilityAndResourceTargetsKeepTheirExactInstanceAndStaticContracts() {
        val visible = RedDotContract.visibilityMethod(Badge::class.java)
        assertEquals(listOf(Boolean::class.javaPrimitiveType), visible.parameterTypes.toList())
        for (layer in RedDotLayer.entries) for (skin in listOf(false, true)) {
            val target = RedDotContract.resourceSetter(Skin::class.java, layer, skin)
            assertEquals(layer, target.layer)
            assertEquals(if (skin) 3 else 2, target.method.parameterCount)
            assertEquals(if (layer == RedDotLayer.IMAGE) ImageView::class.java else View::class.java,
                target.method.parameterTypes[0])
        }
        assertThrows(IllegalStateException::class.java) {
            RedDotContract.resourceSetter(WrongSkin::class.java, RedDotLayer.BACKGROUND, false)
        }
    }

    @Test fun unavailableArtworkDoesNotRemoveIndependentResourcesAndAliasesAreRejected() {
        val names = mutableListOf<String>()
        val ids = RedDotContract.resolveDrawableIds { name ->
            names.add(name)
            when (name) {
                "icon_news_red_dot" -> 1
                "icon_news_red_dot_stroke_2" -> 2
                else -> null
            }
        }
        assertEquals(setOf(1, 2), ids)
        assertEquals(4, names.size)
        assertFalse(names.any { "number" in it })
        assertTrue(RedDotContract.resolveDrawableIds { 0 }.isEmpty())
        assertTrue(RedDotContract.resolveDrawableIds { 1 }.isEmpty())
    }

    @Test fun navigationValidatesBothCallbacksAndTheSdkHolderField() {
        val targets = RedDotContract.navigationTargets(Adapter::class.java, Holder::class.java, 40)
        assertEquals("onCreateViewHolder", targets.create.name)
        assertEquals("onBindViewHolder", targets.bind.name)
        assertThrows(NoSuchFieldException::class.java) {
            RedDotContract.navigationTargets(MissingFieldAdapter::class.java, MissingFieldHolder::class.java, 40)
        }
    }

    class Badge : View(null) {
        override fun onDraw(canvas: Canvas) {}
        fun getRedNum(): String? = null
        fun setRedDotVisible(visible: Boolean) {}
    }
    class WrongReader : View(null) {
        override fun onDraw(canvas: Canvas) {}
        fun getRedNum() = 1
    }
    class StaticReader : View(null) {
        override fun onDraw(canvas: Canvas) {}
        companion object { @JvmStatic fun getRedNum() = "1" }
    }
    class InheritedDraw : View(null) { fun getRedNum(): String? = null }
    class NotAView {
        fun onDraw(canvas: Canvas) {}
        fun getRedNum(): String? = null
    }
    class Skin {
        companion object {
            @JvmStatic fun setBackgroundResource(view: View, id: Int) {}
            @JvmStatic fun setBackgroundResource(view: View, id: Int, skin: Int) {}
            @JvmStatic fun setImageResource(view: ImageView, id: Int) {}
            @JvmStatic fun setImageResource(view: ImageView, id: Int, skin: Int) {}
        }
    }
    class WrongSkin { companion object { @JvmStatic fun setBackgroundResource(view: View, id: Int) = 1 } }
    class Holder { @JvmField val itemView: View? = null }
    class Adapter {
        fun onCreateViewHolder(group: ViewGroup, type: Int) = Holder()
        fun onBindViewHolder(holder: Holder, position: Int) {}
    }
    class MissingFieldHolder
    class MissingFieldAdapter {
        fun onCreateViewHolder(group: ViewGroup, type: Int) = MissingFieldHolder()
        fun onBindViewHolder(holder: MissingFieldHolder, position: Int) {}
    }
}

