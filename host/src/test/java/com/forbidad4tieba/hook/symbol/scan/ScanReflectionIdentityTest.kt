package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanReflectionIdentityTest {
    private val loader = javaClass.classLoader!!

    @Test fun renamedViewsRemainValidAndViewSuffixDoesNotImplyInheritance() {
        assertTrue(ScanReflection.isAssignableTo(Renamed::class.java.name, View::class.java, loader))
        assertTrue(ScanReflection.isAssignableTo(Renamed::class.java.name, TextView::class.java, loader))
        assertFalse(ScanReflection.isAssignableTo(ImpostorTextView::class.java.name, TextView::class.java, loader))
        assertFalse(ScanReflection.isAssignableTo(ImpostorLayout::class.java.name, View::class.java, loader))
        assertFalse(ScanReflection.isAssignableTo("missing.Type", View::class.java, loader))
    }

    @Test fun nestedOwnershipUsesMetadataInsteadOfBinaryNamePrefix() {
        assertTrue(ScanReflection.isDeclaredWithin(Owner::class.java, Owner::class.java))
        assertTrue(ScanReflection.isDeclaredWithin(Owner.Nested::class.java, Owner::class.java))
        assertFalse(ScanReflection.isDeclaredWithin(OwnerSuffix::class.java, Owner::class.java))
        assertFalse(ScanReflection.isDeclaredWithin(`Owner$Impostor`::class.java, Owner::class.java))
    }

    private class Renamed(context: Context) : TextView(context)
    private class ImpostorTextView
    private class ImpostorLayout
    private class Owner { class Nested }
    private class OwnerSuffix
    private class `Owner$Impostor`
}
