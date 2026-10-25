package com.forbidad4tieba.hook.symbol.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoredMembersTest {
    private open class Parent {
        @Suppress("unused")
        private fun count(items: List<*>): Int = items.size
    }

    private class Child : Parent() {
        @Suppress("unused")
        private fun enabled(value: Boolean): Boolean = value
    }

    @Test fun restoresInheritedPrivateMethodsWithoutChangingTheirSignature() {
        val method = requireNotNull(RestoredMembers.resolveMethodByCachedSpec(
            Child::class.java, "count|int|java.util.List", "count"))
        assertEquals(Parent::class.java, method.declaringClass)
        assertEquals(2, method.invoke(Child(), listOf("first", "second")))
    }

    @Test fun restoresPrimitiveParametersWithExistingWhitespaceRules() {
        val method = requireNotNull(RestoredMembers.resolveMethodByCachedSpec(
            Child::class.java, "enabled|boolean| boolean ", "enabled"))
        assertEquals(true, method.invoke(Child(), true))
    }

    @Test fun incompatibleNamesReturnTypesAndParameterClassesFailClosed() {
        listOf(
            "count|long|java.util.List",
            "count|int|java.lang.String",
            "count|int|missing.host.Type",
            "enabled|boolean|boolean",
        ).forEach { spec ->
            assertNull(spec, RestoredMembers.resolveMethodByCachedSpec(Child::class.java, spec, "count"))
        }
    }

    @Test fun incompleteDescriptorsDoNotRestoreAMethod() {
        listOf("", "count", "count|int", "|int|java.util.List", "count||java.util.List")
            .forEach { spec ->
                assertNull(spec, RestoredMembers.resolveMethodByCachedSpec(Child::class.java, spec, "count"))
            }
    }
}
