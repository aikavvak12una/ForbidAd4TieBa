package com.forbidad4tieba.hook.symbol.contract

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PostReplyBarContractTest {
    class PostActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) = Unit
    }
    class InheritedActivity : Activity()
    class Action(context: Context) : FrameLayout(context) {
        fun setCustomLayout(layout: Int) = Unit
        fun getPressState(): Boolean = true
    }
    class InvalidAction(context: Context) : FrameLayout(context) {
        fun setCustomLayout(layout: Int): Int = layout
        fun getPressState(): Boolean = true
    }
    class Content(context: Context) : FrameLayout(context) {
        fun getCurrentCommentRecyclerView(): FrameLayout? = null
    }
    class InvalidContent(context: Context) : FrameLayout(context) {
        fun getCurrentCommentRecyclerView(): String = ""
    }
    class Agree(context: Context) : LinearLayout(context) {
        fun getImgAgree(): ImageView? = null
    }
    class InvalidAgree(context: Context) : LinearLayout(context) {
        fun getImgAgree(): String = ""
    }
    open class ScrollSource(context: Context) : FrameLayout(context) {
        fun dispatchOnScrolled(dx: Int, dy: Int) = Unit
    }
    class ScrollPage(context: Context) : ScrollSource(context)
    class InvalidScrollSource(context: Context) : FrameLayout(context) {
        fun dispatchOnScrolled(dx: Int, dy: Int): Int = dy
    }

    private fun validate(activity: Class<*> = PostActivity::class.java,
        action: Class<*> = Action::class.java, editor: Class<*> = LinearLayout::class.java) =
        PostReplyBarContract.validate(activity, FrameLayout::class.java, action, FrameLayout::class.java, editor)

    @Test fun validatesPublicLifecycleAndReplyActionSemantics() {
        assertEquals(PostActivity::class.java, validate().declaringClass)
    }

    @Test fun missingLifecycleAndMissingActionApiFailClosed() {
        assertThrows(NoSuchMethodException::class.java) { validate(activity = InheritedActivity::class.java) }
        assertThrows(NoSuchMethodException::class.java) { validate(action = FrameLayout::class.java) }
    }

    @Test fun wrongReturnTypeAndUnrelatedEditorFailClosed() {
        assertThrows(IllegalStateException::class.java) { validate(action = InvalidAction::class.java) }
        assertThrows(IllegalStateException::class.java) { validate(editor = FrameLayout::class.java) }
        assertThrows(IllegalStateException::class.java) { validate(activity = String::class.java) }
    }

    @Test fun activeCommentAccessorMustReturnAViewGroup() {
        assertEquals(FrameLayout::class.java,
            PostReplyBarContract.validateCommentAccessor(Content::class.java).returnType)
        assertThrows(NoSuchMethodException::class.java) {
            PostReplyBarContract.validateCommentAccessor(FrameLayout::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            PostReplyBarContract.validateCommentAccessor(InvalidContent::class.java)
        }
    }

    @Test fun likeArtworkAccessorMustReturnAnImageView() {
        assertEquals(ImageView::class.java, PostReplyBarContract.validateAgreeIcon(Agree::class.java).returnType)
        assertThrows(NoSuchMethodException::class.java) { PostReplyBarContract.validateAgreeIcon(LinearLayout::class.java) }
        assertThrows(IllegalStateException::class.java) { PostReplyBarContract.validateAgreeIcon(InvalidAgree::class.java) }
        assertThrows(IllegalStateException::class.java) { PostReplyBarContract.validateAgreeIcon(FrameLayout::class.java) }
    }

    @Test fun scrollCollapseRequiresNativeDeltasAndTheValidatedPageHierarchy() {
        val method = PostReplyBarContract.validateScrollDispatch(ScrollSource::class.java, ScrollPage::class.java)
        assertEquals(ScrollSource::class.java, method.declaringClass)
        assertEquals(Void.TYPE, method.returnType)
        assertThrows(NoSuchMethodException::class.java) {
            PostReplyBarContract.validateScrollDispatch(FrameLayout::class.java, FrameLayout::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            PostReplyBarContract.validateScrollDispatch(InvalidScrollSource::class.java, InvalidScrollSource::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            PostReplyBarContract.validateScrollDispatch(ScrollSource::class.java, FrameLayout::class.java)
        }
    }
}
