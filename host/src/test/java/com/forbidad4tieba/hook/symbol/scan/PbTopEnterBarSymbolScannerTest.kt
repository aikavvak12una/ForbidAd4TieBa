package com.forbidad4tieba.hook.symbol.scan

import android.widget.LinearLayout
import com.baidu.tbadk.core.view.NavigationBar
import com.baidu.tbadk.core.view.PbBarImageView
import com.forbidad4tieba.hook.symbol.model.PbTopEnterBarScanSymbols
import org.junit.Assert.*
import org.junit.Test

class PbTopEnterBarSymbolScannerTest {
    private val loader = javaClass.classLoader!!
    private val candidate = PbTopEnterBarScanSymbols(
        RenamedController::class.java.name, "initializeForum", "forum", "navigation",
    )

    @Test fun restoresSemanticSelectionWithoutDependingOnSampleNames() {
        val result = select(candidate)
        assertEquals(candidate, result)
        val targets = PbTopEnterBarSymbolScanner.restore(result, loader)!!
        assertEquals("initializeForum", targets.initializeMethod.name)
        assertEquals("forum", targets.containerField.name)
        assertEquals("navigation", targets.navigationBarField.name)
    }

    @Test fun missingSemanticEvidenceAndAmbiguousContainersFailClosed() {
        assertNull(select().controllerClass)
        assertNull(select(candidate, candidate.copy(containerField = "breadcrumb")).controllerClass)
        assertNull(select(candidate, candidate.copy(initializeMethod = "otherInitializer")).controllerClass)
    }

    @Test fun invalidCachedMethodAndFieldShapesAreRejected() {
        val invalid = listOf(
            candidate.copy(initializeMethod = "hasResult"),
            candidate.copy(initializeMethod = "withParameter"),
            candidate.copy(initializeMethod = "staticInitializer"),
            candidate.copy(containerField = "wrongContainer"),
            candidate.copy(containerField = "staticContainer"),
            candidate.copy(navigationBarField = "forum"),
            candidate.copy(controllerClass = MissingForumIcon::class.java.name),
        )
        invalid.forEach { value ->
            assertNull("Rejected $value", select(value).controllerClass)
            assertEquals("Invalid distractor cannot replace the selected target", candidate, select(value, candidate))
        }
    }

    private fun select(vararg candidates: PbTopEnterBarScanSymbols): PbTopEnterBarScanSymbols =
        PbTopEnterBarSymbolScanner.selectValidatedCandidate(candidates.toList(), loader, null)

    class RenamedController {
        @JvmField var forum: LinearLayout? = null
        @JvmField var breadcrumb: LinearLayout? = null
        @JvmField var navigation: NavigationBar? = null
        @JvmField var icon: PbBarImageView? = null
        @JvmField var wrongContainer: Any? = null
        fun initializeForum() = Unit
        fun otherInitializer() = Unit
        fun hasResult() = true
        fun withParameter(visible: Boolean) = visible
        companion object {
            @JvmField var staticContainer: LinearLayout? = null
            @JvmStatic fun staticInitializer() = Unit
        }
    }

    class MissingForumIcon {
        @JvmField var forum: LinearLayout? = null
        @JvmField var navigation: NavigationBar? = null
        fun initializeForum() = Unit
    }
}
