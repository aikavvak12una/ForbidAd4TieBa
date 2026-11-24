package com.forbidad4tieba.hook.symbol.contract

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import org.junit.Assert.*
import org.junit.Test

/** Stable native relationships; actual host adaptation uses the current APK gate. */
@Suppress("UNUSED_PARAMETER")
class HostContractShapeTest {
    @Test fun activityOverrideAndReplyTypesAreRequired() {
        assertEquals(Page::class.java, LaunchSplashContract.validate(Page::class.java).declaringClass)
        assertThrows(NoSuchMethodException::class.java) { LaunchSplashContract.validate(MissingOwnOnCreate::class.java) }
        assertThrows(IllegalStateException::class.java) { LaunchSplashContract.validate(String::class.java) }
        assertEquals(Page::class.java, reply(Action::class.java).declaringClass)
        assertThrows(IllegalStateException::class.java) { reply(WrongAction::class.java) }
        assertThrows(IllegalStateException::class.java) { reply(Action::class.java, FrameLayout::class.java) }
    }

    @Test fun artworkAndReplyAccessorsKeepNativeReturnTypes() {
        assertEquals(Label::class.java, BottomTabArtworkContract.validate(Label::class.java).declaringClass)
        assertThrows(IllegalStateException::class.java) { BottomTabArtworkContract.validate(WrongLabel::class.java) }
        assertEquals(FrameLayout::class.java, PostReplyBarContract.validateCommentAccessor(Content::class.java).returnType)
        assertThrows(IllegalStateException::class.java) { PostReplyBarContract.validateCommentAccessor(WrongContent::class.java) }
        assertEquals(ImageView::class.java, PostReplyBarContract.validateAgreeIcon(Agree::class.java).returnType)
        assertThrows(IllegalStateException::class.java) { PostReplyBarContract.validateAgreeIcon(WrongAgree::class.java) }
    }

    @Test fun scrollDispatchRequiresTheActualPageHierarchy() {
        assertEquals(ScrollSource::class.java, PostReplyBarContract.validateScrollDispatch(
            ScrollSource::class.java, ScrollPage::class.java).declaringClass)
        assertThrows(IllegalStateException::class.java) {
            PostReplyBarContract.validateScrollDispatch(ScrollSource::class.java, FrameLayout::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            PostReplyBarContract.validateScrollDispatch(WrongScroll::class.java, WrongScroll::class.java)
        }
    }

    @Test fun homeContentRoutesAreIndependentAndRequireHomeAndPager() {
        val web = HomeTabContentContract.validate(ViewGroup::class.java, ViewGroup::class.java, FrameLayout::class.java, null)
        assertTrue(web.hasWebContent)
        assertFalse(web.hasLiveContent)
        val live = HomeTabContentContract.validate(ViewGroup::class.java, ViewGroup::class.java, View::class.java, FrameLayout::class.java)
        assertFalse(live.hasWebContent)
        assertTrue(live.hasLiveContent)
        assertThrows(IllegalStateException::class.java) {
            HomeTabContentContract.validate(View::class.java, ViewGroup::class.java, null, null)
        }
        assertThrows(IllegalStateException::class.java) {
            HomeTabContentContract.validate(ViewGroup::class.java, String::class.java, null, null)
        }
    }

    @Test fun publishReadersKeepPublicStringOverloads() {
        assertEquals(listOf("isHit", "isHitOnce"), PublishEntryContract.validate(Readers::class.java).map { it.name })
        assertThrows(NoSuchMethodException::class.java) { PublishEntryContract.validate(MissingReader::class.java) }
        assertThrows(IllegalStateException::class.java) { PublishEntryContract.validate(InstanceReader::class.java) }
    }

    @Test fun settingsSearchAndTabsKeepTheirSelectedTypeRelationships() {
        assertEquals(RelativeLayout::class.java, SettingsContract.restoreEntry(Settings::class.java, "bind", "container").containerField.type)
        assertThrows(IllegalStateException::class.java) { SettingsContract.restoreEntry(WrongSettings::class.java, "bind", "container") }
        val search = SearchBoxContract.restoreHome(Search::class.java, Box::class.java, Recycler::class.java,
            Container::class.java, "initialize", "box")
        assertEquals("initialize", search.ownerInitMethod.name)
        assertThrows(IllegalStateException::class.java) {
            SearchBoxContract.restoreHome(Search::class.java, Box::class.java, Recycler::class.java,
                Container::class.java, "initialize", "wrongBox")
        }
        assertTrue(HomeTabsContract.isValidParseMethod(Tabs::class.java.getDeclaredMethod("parse"), Tabs::class.java))
        assertFalse(HomeTabsContract.isValidParseMethod(Tabs::class.java.getDeclaredMethod("custom"), Tabs::class.java))
    }

    @Test fun redDotDrawIsDeclaredAndItsReaderReturnsText() {
        assertEquals(Badge::class.java, RedDotContract.messageTargets(Badge::class.java).draw.declaringClass)
        assertThrows(NoSuchMethodException::class.java) { RedDotContract.messageTargets(InheritedBadge::class.java) }
        assertThrows(IllegalStateException::class.java) { RedDotContract.messageTargets(WrongBadge::class.java) }
        assertEquals(listOf(Boolean::class.javaPrimitiveType), RedDotContract.visibilityMethod(Badge::class.java).parameterTypes.toList())
    }

    @Test fun resourcesAndNavigationUseSdkTypesAndRejectAliasedIds() {
        assertEquals(View::class.java, RedDotContract.resourceSetter(Skin::class.java, RedDotLayer.BACKGROUND, false).method.parameterTypes[0])
        assertEquals(3, RedDotContract.resourceSetter(Skin::class.java, RedDotLayer.IMAGE, true).method.parameterCount)
        assertEquals(setOf(1, 2), RedDotContract.resolveDrawableIds {
            when (it) { "icon_news_red_dot" -> 1; "icon_news_red_dot_stroke_2" -> 2; else -> null }
        })
        assertTrue(RedDotContract.resolveDrawableIds { 1 }.isEmpty())
        assertTrue(RedDotContract.resolveDrawableIds { 0 }.isEmpty())
        assertEquals("onBindViewHolder", RedDotContract.navigationTargets(Adapter::class.java, Holder::class.java, 40).bind.name)
        assertThrows(NoSuchFieldException::class.java) {
            RedDotContract.navigationTargets(MissingAdapter::class.java, MissingHolder::class.java, 40)
        }
    }

    private fun reply(action: Class<*>, editor: Class<*> = LinearLayout::class.java) =
        PostReplyBarContract.validate(Page::class.java, FrameLayout::class.java, action, FrameLayout::class.java, editor)

    class Page : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {}
    }
    class MissingOwnOnCreate : Activity()
    class Action(context: Context) : FrameLayout(context) {
        fun setCustomLayout(layout: Int) {}
        fun getPressState() = true
    }
    class WrongAction(context: Context) : FrameLayout(context) {
        fun setCustomLayout(layout: Int) = layout
        fun getPressState() = true
    }
    class Label(context: Context) : FrameLayout(context) { fun getContentTv(): View? = null }
    class WrongLabel(context: Context) : FrameLayout(context) { fun getContentTv() = "" }
    class Content(context: Context) : FrameLayout(context) { fun getCurrentCommentRecyclerView(): FrameLayout? = null }
    class WrongContent(context: Context) : FrameLayout(context) { fun getCurrentCommentRecyclerView() = "" }
    class Agree(context: Context) : LinearLayout(context) { fun getImgAgree(): ImageView? = null }
    class WrongAgree(context: Context) : LinearLayout(context) { fun getImgAgree() = "" }
    open class ScrollSource(context: Context) : FrameLayout(context) { fun dispatchOnScrolled(dx: Int, dy: Int) {} }
    class ScrollPage(context: Context) : ScrollSource(context)
    class WrongScroll(context: Context) : FrameLayout(context) { fun dispatchOnScrolled(dx: Int, dy: Int) = dy }
    class Readers {
        companion object {
            @JvmStatic fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: String?) = true
        }
    }
    class MissingReader {
        companion object {
            @JvmStatic fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: Int) = true
        }
    }
    class InstanceReader {
        fun isHit(key: String?) = true
        fun isHitOnce(key: String?) = true
    }
    class Settings {
        @JvmField var container: RelativeLayout? = null
        fun bind(view: View) {}
    }
    class WrongSettings {
        @JvmField var container: String? = null
        fun bind(view: View) {}
    }
    class Box
    class Recycler
    class Container
    class Search {
        fun initialize(recycler: Recycler, container: Container) {}
        fun box(): Box? = null
        fun wrongBox() = ""
    }
    class CustomList : ArrayList<Any>()
    class Tabs {
        fun parse(): List<Any> = emptyList()
        fun custom() = CustomList()
    }
    class Badge(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {}
        fun getRedNum(): String? = null
        fun setRedDotVisible(visible: Boolean) {}
    }
    class InheritedBadge(context: Context) : View(context) { fun getRedNum(): String? = null }
    class WrongBadge(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {}
        fun getRedNum() = 1
    }
    class Skin {
        companion object {
            @JvmStatic fun setBackgroundResource(view: View, id: Int) {}
            @JvmStatic fun setImageResource(view: ImageView, id: Int, skin: Int) {}
        }
    }
    class Holder { @JvmField val itemView: View? = null }
    class Adapter {
        fun onCreateViewHolder(group: ViewGroup, type: Int) = Holder()
        fun onBindViewHolder(holder: Holder, position: Int) {}
    }
    class MissingHolder
    class MissingAdapter {
        fun onCreateViewHolder(group: ViewGroup, type: Int) = MissingHolder()
        fun onBindViewHolder(holder: MissingHolder, position: Int) {}
    }
}
