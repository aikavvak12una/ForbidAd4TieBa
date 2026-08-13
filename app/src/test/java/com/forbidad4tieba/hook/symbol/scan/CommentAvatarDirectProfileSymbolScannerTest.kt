package com.forbidad4tieba.hook.symbol.scan

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentAvatarDirectProfileSymbolScannerTest {

    private val testClassLoader: ClassLoader = requireNotNull(javaClass.classLoader)

    @Test
    fun matchesCommentFloorWireMethodSignature() {
        val holderClass = WireFixtureHolder::class.java
        val postDataClass = WireFixturePostData::class.java
        val method = BaseWireAdapter::class.java
            .declaredMethods
            .firstOrNull { it.name == "wire" }
            ?: error("wire method missing")

        assertTrue(CommentAvatarDirectProfileSymbolScanner.isWireMethod(method, holderClass, postDataClass))
    }

    @Test
    fun rejectsNonWireMethodSignatures() {
        val holderClass = WireFixtureHolder::class.java
        val postDataClass = WireFixturePostData::class.java
        val staticMethod = BaseWireAdapter::class.java
            .declaredMethods
            .firstOrNull { it.name == "staticWire" }
            ?: error("staticWire method missing")
        val twoParamMethod = BaseWireAdapter::class.java
            .declaredMethods
            .firstOrNull { it.name == "twoParams" }
            ?: error("twoParams method missing")

        assertTrue(!CommentAvatarDirectProfileSymbolScanner.isWireMethod(staticMethod, holderClass, postDataClass))
        assertTrue(!CommentAvatarDirectProfileSymbolScanner.isWireMethod(twoParamMethod, holderClass, postDataClass))
    }

    @Test
    fun resolvesUniqueHolderAvatarFields() {
        val headField = CommentAvatarDirectProfileSymbolScanner.resolveHolderField(
            holderClass = WireFixtureHolder::class.java,
            fieldTypeName = FakeHeadImage::class.java.name,
            cl = testClassLoader,
            label = "head",
            logger = null,
        )
        val pendantField = CommentAvatarDirectProfileSymbolScanner.resolveHolderField(
            holderClass = WireFixtureHolder::class.java,
            fieldTypeName = FakeHeadPendant::class.java.name,
            cl = testClassLoader,
            label = "pendant",
            logger = null,
        )

        assertEquals("p", headField)
        assertEquals("D", pendantField)
    }

    @Test
    fun failsClosedWhenHolderFieldAmbiguous() {
        val result = CommentAvatarDirectProfileSymbolScanner.resolveHolderField(
            holderClass = AmbiguousHeadHolder::class.java,
            fieldTypeName = FakeHeadImage::class.java.name,
            cl = testClassLoader,
            label = "head",
            logger = null,
        )

        assertNull(result)
    }

    @Test
    fun resolvesUniquePostDataUserGetter() {
        val result = CommentAvatarDirectProfileSymbolScanner.resolvePostDataUserMethod(
            postDataClass = WireFixturePostData::class.java,
            metaDataClass = FakeMetaData::class.java,
            cl = testClassLoader,
            logger = null,
        )

        assertEquals("user", result)
    }

    @Test
    fun failsClosedWhenPostDataUserGetterAmbiguous() {
        val result = CommentAvatarDirectProfileSymbolScanner.resolvePostDataUserMethod(
            postDataClass = AmbiguousPostData::class.java,
            metaDataClass = FakeMetaData::class.java,
            cl = testClassLoader,
            logger = null,
        )

        assertNull(result)
    }

    @Test
    fun reflectionFallbackSelectsBaseAdapter() {
        val result = CommentAvatarDirectProfileSymbolScanner.selectReflectionBase(
            candidates = listOf(
                BaseWireAdapter::class.java.name to "wire",
                ChildWireAdapter::class.java.name to "wire",
            ),
            cl = testClassLoader,
            logger = null,
        )

        assertEquals(BaseWireAdapter::class.java.name, result?.first)
        assertEquals("wire", result?.second)
    }

    @Test
    fun reflectionFallbackFailsClosedWhenNoSingleBase() {
        val result = CommentAvatarDirectProfileSymbolScanner.selectReflectionBase(
            candidates = listOf(
                BaseWireAdapter::class.java.name to "wire",
                UnrelatedWireAdapter::class.java.name to "wire",
            ),
            cl = testClassLoader,
            logger = null,
        )

        assertNull(result)
    }

    // ---- fixture types mirroring the host shapes ----

    private open class FakeHeadImage
    private open class FakeHeadPendant
    private open class FakeMetaData

    private class WireFixturePostData {
        fun user(): FakeMetaData = FakeMetaData()
    }

    private class AmbiguousPostData {
        fun user(): FakeMetaData = FakeMetaData()
        fun author(): FakeMetaData = FakeMetaData()
    }

    private open class WireFixtureHolder {
        var p: FakeHeadImage? = null
        var D: FakeHeadPendant? = null
    }

    private class AmbiguousHeadHolder {
        var p: FakeHeadImage? = null
        var second: FakeHeadImage? = null
    }

    private open class BaseWireAdapter {
        @Suppress("UNUSED_PARAMETER")
        fun wire(holder: WireFixtureHolder, position: Int, postData: WireFixturePostData, view: View) {
        }

        companion object {
            @JvmStatic
            @Suppress("UNUSED_PARAMETER")
            fun staticWire(holder: WireFixtureHolder, position: Int, postData: WireFixturePostData, view: View) {
            }

            @JvmStatic
            @Suppress("UNUSED_PARAMETER")
            fun twoParams(holder: WireFixtureHolder, position: Int) {
            }
        }
    }

    private class ChildWireAdapter : BaseWireAdapter()

    private class UnrelatedWireAdapter {
        @Suppress("UNUSED_PARAMETER")
        fun wire(holder: WireFixtureHolder, position: Int, postData: WireFixturePostData, view: View) {
        }
    }

}
