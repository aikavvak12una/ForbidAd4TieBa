package com.forbidad4tieba.hook.symbol.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PbFirstFloorRecommendInsertSymbolScannerTest {
    @Test
    fun resolvesUniqueFiveParameterInsertMethod() {
        val result = PbFirstFloorRecommendInsertSymbolScanner.scanResolvedClass(
            targetClass = UniqueInsertFixture::class.java,
            postDataClass = FakePostData::class.java,
            dexValidatedMethodNames = setOf("insertRecommend"),
            logger = null,
        )

        assertEquals(UniqueInsertFixture::class.java.name, result.className)
        assertEquals("insertRecommend", result.methodName)
    }

    @Test
    fun failsClosedWhenInsertMethodIsAmbiguous() {
        val result = PbFirstFloorRecommendInsertSymbolScanner.scanResolvedClass(
            targetClass = AmbiguousInsertFixture::class.java,
            postDataClass = FakePostData::class.java,
            dexValidatedMethodNames = setOf("first", "second"),
            logger = null,
        )

        assertNull(result.className)
        assertNull(result.methodName)
    }

    @Test
    fun failsClosedWithoutDexSemanticEvidence() {
        val result = PbFirstFloorRecommendInsertSymbolScanner.scanResolvedClass(
            targetClass = UniqueInsertFixture::class.java,
            postDataClass = FakePostData::class.java,
            dexValidatedMethodNames = emptySet(),
            logger = null,
        )

        assertNull(result.className)
        assertNull(result.methodName)
    }

    private class FakePbData
    private class FakePostData

    private class UniqueInsertFixture {
        companion object {
            @JvmStatic
            fun insertRecommend(
                data: FakePbData,
                firstFloorData: FakePostData,
                list: List<Any>,
                index: Int,
                firstFloorDataCache: FakePostData,
            ): Boolean {
                return data.hashCode() + firstFloorData.hashCode() + list.size + index +
                    firstFloorDataCache.hashCode() > 0
            }

            @JvmStatic
            fun insertOther(
                data: FakePbData,
                firstFloorData: FakePostData,
                list: List<Any>,
                index: Int,
                firstFloorDataCache: FakePostData,
                extra: Any,
            ): Boolean {
                return insertRecommend(data, firstFloorData, list, index, firstFloorDataCache) &&
                    extra.hashCode() > 0
            }
        }
    }

    private class AmbiguousInsertFixture {
        companion object {
            @JvmStatic
            fun first(
                data: FakePbData,
                firstFloorData: FakePostData,
                list: List<Any>,
                index: Int,
                firstFloorDataCache: FakePostData,
            ): Boolean {
                return data.hashCode() + firstFloorData.hashCode() + list.size + index +
                    firstFloorDataCache.hashCode() > 0
            }

            @JvmStatic
            fun second(
                data: FakePbData,
                firstFloorData: FakePostData,
                list: List<Any>,
                index: Int,
                firstFloorDataCache: FakePostData,
            ): Boolean {
                return first(data, firstFloorData, list, index, firstFloorDataCache)
            }
        }
    }
}
