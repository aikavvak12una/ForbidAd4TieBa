package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRestorationTest {
    @Test fun restoresTheScannerSelectedViewEntryAndInstanceContainer() {
        val target = SettingsContract.restoreEntry(Valid::class.java, "bind", "container")
        assertEquals(listOf(View::class.java), target.initMethod.parameterTypes.toList())
        assertEquals(RelativeLayout::class.java, target.containerField.type)
        assertTrue(target.initMethod.isAccessible)
        assertTrue(target.containerField.isAccessible)
    }

    @Test fun rejectsAnOldSignatureOrInvalidContainerWithoutPickingAnotherMember() {
        assertTrue(runCatching { SettingsContract.restoreEntry(OldSignature::class.java, "bind", "container") }.isFailure)
        assertTrue(runCatching { SettingsContract.restoreEntry(WrongContainer::class.java, "bind", "container") }.isFailure)
        assertTrue(runCatching { SettingsContract.restoreEntry(Valid::class.java, "missing", "container") }.isFailure)
        assertTrue(runCatching { SettingsContract.restoreEntry(Valid::class.java, "bind", "missing") }.isFailure)
    }

    @Test fun rejectsStaticOrNonVoidInitializers() {
        assertTrue(runCatching { SettingsContract.restoreEntry(Static::class.java, "bind", "container") }.isFailure)
        assertTrue(runCatching { SettingsContract.restoreEntry(NonVoid::class.java, "bind", "container") }.isFailure)
        assertFalse(runCatching { SettingsContract.restoreEntry(Valid::class.java, "bind", "container") }.isFailure)
    }

    private class Valid {
        @JvmField var container: RelativeLayout? = null
        fun bind(@Suppress("UNUSED_PARAMETER") view: View) = Unit
    }
    private class OldSignature {
        @JvmField var container: RelativeLayout? = null
        fun bind(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") view: View) = Unit
    }
    private class WrongContainer {
        @JvmField var container: String? = null
        fun bind(@Suppress("UNUSED_PARAMETER") view: View) = Unit
    }
    private class Static {
        @JvmField var container: RelativeLayout? = null
        companion object { @JvmStatic fun bind(@Suppress("UNUSED_PARAMETER") view: View) = Unit }
    }
    private class NonVoid {
        @JvmField var container: RelativeLayout? = null
        fun bind(@Suppress("UNUSED_PARAMETER") view: View): Boolean = false
    }
}
