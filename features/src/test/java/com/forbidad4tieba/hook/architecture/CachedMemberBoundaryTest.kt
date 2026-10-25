package com.forbidad4tieba.hook.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedMemberBoundaryTest {
    @Test fun featuresDoNotRedefineTheHostCachedMethodFormat() {
        val root = File(checkNotNull(System.getProperty("project.root")), "features/src/main")
        val declarations = Regex("\\b(?:class|fun)\\s+(?:CachedMethodSpec|parseCachedMethodSpec|findMethodByCachedSpec|resolveMethodByCachedSpec)\\b")
        val violations = root.walkTopDown().filter { it.extension == "kt" }
            .filter { declarations.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertEquals("Cached method formats belong to host/RestoredMembers", emptyList<String>(), violations)
    }
}
