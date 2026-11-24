package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import com.baidu.tbadk.core.dialog.RoundLinearLayout
import com.baidu.tbadk.core.elementsMaven.view.EMTextView
import com.forbidad4tieba.hook.symbol.model.DexOriginalImageMethodsMatch
import java.lang.reflect.Method
import org.junit.Assert.*
import org.junit.Test

@Suppress("UNUSED_PARAMETER")
class HostSelectionRulesTest {
    private val loader = javaClass.classLoader!!

    @Test fun feedBindUsesSemanticEvidenceAndValidCardData() {
        evidenceCases { evidence -> FeedCardBindRule { method, _ -> evidence(method) }
            .match(Bind::class.java, loader, null)?.methodName }
    }

    @Test fun feedLoadUsesSemanticEvidenceAndListInput() {
        evidenceCases { evidence -> FeedTemplateLoadMoreRule { method, _ -> evidence(method) }
            .match(Load::class.java, loader, null)?.methodName }
    }

    @Test fun crashReportRequiresAHandlerAndAUniqueCalledMethod() {
        evidenceCases { evidence -> CrashReportSymbolScanner.selectReportMethod(Handler::class.java, null, evidence)?.name }
        assertNull(CrashReportSymbolScanner.selectReportMethod(NotHandler::class.java, null) { true })
    }

    @Test fun popupRequiresUniqueContentEvidenceAndUniqueLayoutFields() {
        evidenceCases { evidence -> FreeCopyPopupRule { method, _ -> evidence(method) }
            .match(Popup::class.java, loader, null)?.methodName }
        assertNull(FreeCopyPopupRule { _, _ -> true }.match(AmbiguousPopup::class.java, loader, null))
        assertEquals("text", FreeCopyPopupRule { method, _ -> method.name == "renamed" }
            .match(Popup::class.java, loader, null)?.fieldName)
    }

    @Test fun memeIgnoresLegacyShapeAndRejectsMissingOrAmbiguousCurrentShape() {
        fun scan(owner: Class<*>) = InputMemeBarSymbolScanner.scanResolvedClass(owner, InputType::class.java, null)
        assertEquals("renamed", scan(Meme::class.java).enableMethod)
        assertNull(scan(String::class.java).enableMethod)
        assertNull(scan(AmbiguousMeme::class.java).enableMethod)
    }

    @Test fun firstFloorInsertionRequiresUniqueDexEvidenceAndItsFiveParameterShape() {
        fun scan(names: Set<String>) = PbFirstFloorRecommendInsertSymbolScanner.scanResolvedClass(
            Insert::class.java, Post::class.java, names, null)
        assertEquals("renamed", scan(setOf("renamed")).methodName)
        assertNull(scan(emptySet()).methodName)
        assertNull(scan(setOf("a", "renamed")).methodName)
        assertNull(scan(setOf("wrong")).methodName)
    }

    @Test fun originalImageRestoresTheDexSelectedMethodWithoutSignatureFallback() {
        val match = DexOriginalImageMethodsMatch("prepare", "trigger", "start", "test")
        val result = OriginalImageSymbolScanner.validateDexMethodMatch(Original::class.java, match, null)
        assertEquals("trigger", result?.triggerMethod)
        assertNull(OriginalImageSymbolScanner.validateDexMethodMatch(Original::class.java,
            match.copy(triggerMethod = "wrong"), null))
    }

    @Test fun postAdaptersKeepTheirIndependentGenericItemTypesWhenOnePathIsMissing() {
        fun scan(adapter: Class<*>?) = PostAdDataFilterSymbolScanner.scanResolvedClasses(
            adapter, RecyclerAdapter::class.java, UniqueId::class.java, null)
        val both = scan(Adapter::class.java)
        assertEquals("replaceItems", both.typeAdapterSetDataMethod)
        assertEquals("replaceRecyclerItems", both.recyclerViewTypeAdapterSetDataMethod)
        assertEquals(Item::class.java.name, both.dataItemClass)
        val one = scan(null)
        assertNull(one.typeAdapterSetDataMethod)
        assertEquals("replaceRecyclerItems", one.recyclerViewTypeAdapterSetDataMethod)
        assertEquals(Item::class.java.name, one.dataItemClass)
    }

    private fun evidenceCases(select: ((Method) -> Boolean) -> String?) {
        assertEquals("renamed", select { it.name == "renamed" })
        assertNull(select { false })
        assertNull(select { true })
        assertNull(select { it.name == "wrong" })
    }

    class CardData {
        @JvmField val items: List<Any> = emptyList()
        @JvmField val first = ""
        @JvmField val second = ""
        @JvmField val enabled = false
        @JvmField val selected = false
        @JvmField val attributes: Map<String, Any> = emptyMap()
    }
    class Bind {
        fun w(data: CardData) {}
        fun renamed(data: CardData) {}
        fun wrong(data: String) {}
    }
    class Load {
        fun L(items: List<Any>) {}
        fun renamed(items: ArrayList<Any>) {}
        fun wrong(count: Int) {}
    }
    class Handler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {}
        fun b(error: Throwable) {}
        fun renamed(error: Throwable) {}
        fun wrong(error: Error) {}
    }
    class NotHandler { fun renamed(error: Throwable) {} }
    class Popup {
        @JvmField var round: RoundLinearLayout? = null
        @JvmField var text: EMTextView? = null
        @JvmField var context: Context? = null
        @JvmField var items: List<Any> = emptyList()
        fun c(): View? = null
        fun renamed(): View? = null
    }
    class AmbiguousPopup {
        @JvmField var b: RoundLinearLayout? = null
        @JvmField var otherRound: RoundLinearLayout? = null
        @JvmField var text: EMTextView? = null
        @JvmField var context: Context? = null
        @JvmField var items: List<Any> = emptyList()
        fun renamed(): View? = null
    }
    class InputType
    class Meme {
        companion object {
            @JvmStatic fun legacy(context: Context, type: InputType) = true
            @JvmStatic fun renamed(context: Context, type: InputType, force: Boolean) = true
        }
    }
    class AmbiguousMeme {
        companion object {
            @JvmStatic fun a(context: Context, type: InputType, force: Boolean) = true
            @JvmStatic fun renamed(context: Context, type: InputType, force: Boolean) = true
        }
    }
    class Data
    class Post
    class Insert {
        companion object {
            @JvmStatic fun renamed(data: Data, post: Post, items: List<Any>, index: Int, cache: Post) = true
            @JvmStatic fun a(data: Data, post: Post, items: List<Any>, index: Int, cache: Post) = true
            @JvmStatic fun wrong(data: Data, post: Post, items: List<Any>, index: Int, cache: Post, extra: Any) = true
        }
    }
    class Original {
        fun prepare() {}
        fun trigger() {}
        fun start(name: String) {}
        fun wrong(force: Boolean) {}
    }
    class UniqueId
    interface Item { fun getType(): UniqueId }
    class Adapter { fun replaceItems(items: List<Item>) {} }
    class RecyclerAdapter { fun replaceRecyclerItems(items: List<Item>) {} }
}
