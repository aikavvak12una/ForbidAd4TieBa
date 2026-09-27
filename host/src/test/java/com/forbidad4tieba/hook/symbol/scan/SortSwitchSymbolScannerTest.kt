package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassSortSwitchSymbols
import org.junit.Assert.assertEquals
import org.junit.Test

class SortSwitchSymbolScannerTest {
    private val symbols = HomeNativeGlassSortSwitchSymbols("paint", "drawSlide", "path")

    @Test fun scanPublishesOnlyReflectionValidatedTargets() {
        assertEquals(symbols, validate(symbols))
    }

    @Test fun badBackgroundTypePreservesValidSlideGroup() {
        assertEquals(symbols.copy(backgroundPaintField = null), validate(symbols.copy(backgroundPaintField = "wrongType")))
    }

    @Test fun staticOrWrongSignatureDrawDisablesOnlyTheSlideGroup() {
        for (method in listOf("staticDraw", "wrongSignature")) {
            assertEquals(symbols.copy(slideDrawMethod = null, slidePathField = null), validate(symbols.copy(slideDrawMethod = method)))
        }
    }

    @Test fun invalidOrMissingPathDisablesOnlyTheSlideGroup() {
        for (field in listOf("wrongType", "missing", null)) {
            assertEquals(symbols.copy(slideDrawMethod = null, slidePathField = null), validate(symbols.copy(slidePathField = field)))
        }
    }

    private fun validate(value: HomeNativeGlassSortSwitchSymbols) =
        SortSwitchSymbolScanner.validateScanSymbols(Widget::class.java, value, null)

    private class Widget(context: Context) : View(context) {
        @JvmField var paint: Paint? = null
        @JvmField var path: Path? = null
        @JvmField var wrongType: String? = null
        fun drawSlide(canvas: Canvas) = Unit
        fun wrongSignature(canvas: Canvas): Boolean = false
        companion object { @JvmStatic fun staticDraw(canvas: Canvas) = Unit }
    }
}
