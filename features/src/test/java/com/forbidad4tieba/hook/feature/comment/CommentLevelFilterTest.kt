package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.config.CommentLevelFilterSettings
import com.forbidad4tieba.hook.symbol.model.CommentProtocol
import org.junit.Assert.*
import org.junit.Test
import tbclient.*
import tbclient.PbPage.DataRes
import java.util.Collections

class CommentLevelFilterTest {
    private val filter = CommentLevelFilter(CommentProtocol(javaClass.classLoader!!))
    private val settings = CommentLevelFilterSettings(true, 5, false, false)
    private fun user(id: Long, level: Int?) = User.Builder().apply { this.id = id; level_id = level }.build(false)
    private fun child(level: Int?) = SubPostList.Builder().apply { author = user(2, level); author_id = 2 }.build(false)
    private fun post(level: Int?, count: Int = 0, children: List<SubPostList> = emptyList()) = Post.Builder().apply {
        id = 10; floor = 2; author = user(1, level); author_id = 1; sub_post_number = count
        sub_post_list = SubPost.Builder().apply { sub_post_list = Collections.unmodifiableList(children) }.build(false)
    }.build(false)
    private fun page(vararg posts: Post) = DataRes.Builder().apply {
        post_list = Collections.unmodifiableList(posts.toList()); page = Any()
    }.build(false)
    private fun apply(data: DataRes, s: CommentLevelFilterSettings = settings) = filter.filter(data, false, s) as DataRes

    @Test fun everyThresholdFiltersNullAndLowerLevelsButKeepsEquality() {
        for (minimum in 1..18) {
            val data = page(post(null), *(1..18).map { post(it) }.toTypedArray())
            assertEquals((minimum..18).toList(), apply(data, settings.copy(minimumLevel = minimum))
                .post_list!!.map { it.author!!.level_id })
        }
    }

    @Test fun skipNestedPreservesInlineAndExpandedRepliesWhileMainCommentsStillFilter() {
        val replies = listOf(child(null), child(1), child(5))
        val low = post(null, replies.size, replies)
        val high = post(5, replies.size, replies)
        val skip = settings.copy(skipNested = true)
        val result = apply(page(low, high), skip)
        assertEquals(listOf(high), result.post_list)
        assertSame(high, result.post_list!!.single())
        val body = Post.Builder(low).apply { floor = 1 }.build(false)
        assertSame(body, apply(page(body), skip).post_list!!.single())
        val exempt = page(low, high)
        assertSame(exempt, apply(exempt, skip.copy(keepWithReplies = true)))
        val expanded = tbclient.PbFloor.DataRes.Builder().apply {
            post = low; subpost_list = replies; subpost_num = 40; page = Any()
        }.build(false)
        assertSame(expanded, filter.filter(expanded, true, skip))
        assertEquals(listOf(5), (filter.filter(expanded, true, settings) as tbclient.PbFloor.DataRes)
            .subpost_list!!.map { it.author!!.level_id })
        for (level in listOf(null, 1, 5)) assertFalse(skip.hides(level, true, false))
    }

    @Test fun equalitySurvivesUnknownLevelsAreHiddenAndDisabledPassKeepsIdentity() {
        val data = page(post(4), post(5), post(6), post(null), post(0), post(-1))
        val result = apply(data)
        assertEquals(listOf(5, 6), result.post_list!!.map { it.author!!.level_id })
        assertSame(data, apply(data, settings.copy(enabled = false)))
        assertSame(data.page, result.page)
        assertEquals(6, data.post_list!!.size)
        assertSame(result, apply(result))
    }

    @Test fun minimumOneFiltersUngradedAuthorsAcrossMainInlineAndExpandedComments() {
        val minimumOne = settings.copy(minimumLevel = 1)
        val replies = listOf(child(null), child(0), child(-1), child(1), child(18),
            SubPostList.Builder().apply { author_id = 2 }.build(false))
        val graded = post(1, replies.size, replies)
        val noAuthor = Post.Builder().apply { id = 11; floor = 2; author_id = 3 }.build(false)
        val result = apply(page(post(null), post(0), post(-1), noAuthor, graded, post(18)), minimumOne)
        assertEquals(listOf(1, 18), result.post_list!!.map { it.author!!.level_id })
        assertEquals(listOf(1, 18), result.post_list!![0].sub_post_list!!.sub_post_list!!.map { it.author!!.level_id })

        val parent = post(null, replies.size, replies)
        val exempt = apply(page(parent), minimumOne.copy(keepWithReplies = true)).post_list!!.single()
        assertNull(exempt.author!!.level_id)
        assertEquals(listOf(1, 18), exempt.sub_post_list!!.sub_post_list!!.map { it.author!!.level_id })
        val floor = tbclient.PbFloor.DataRes.Builder().apply {
            post = parent; subpost_list = replies
        }.build(false)
        val expanded = filter.filter(floor, true, minimumOne) as tbclient.PbFloor.DataRes
        assertEquals(parent.id, expanded.post!!.id)
        assertEquals(listOf(1, 18), expanded.subpost_list!!.map { it.author!!.level_id })
    }

    @Test fun exemptionUsesOriginalCountEvenWhenEveryLoadedChildIsRemoved() {
        val parent = post(2, 3, listOf(child(1), child(4)))
        val data = page(parent, post(2, 1), post(2, 0, listOf(child(1))), post(2))
        val result = apply(data, settings.copy(keepWithReplies = true))
        assertEquals(3, result.post_list!!.size)
        assertTrue(result.post_list!![0].sub_post_list!!.sub_post_list!!.isEmpty())
        assertEquals(3, result.post_list!![0].sub_post_number)
        assertEquals(2, parent.sub_post_list!!.sub_post_list!!.size)
        assertTrue(apply(data).post_list!!.isEmpty())
    }

    @Test fun firstPostIsRetainedAndChildrenStillFilteredUsingAuthorTablePrecedence() {
        val body = Post.Builder(post(1, 2, listOf(child(4), child(5)))).apply { floor = 1 }.build(false)
        val unknownFloorBody = Post.Builder(post(1)).apply { id = 99; floor = 0 }.build(false)
        val data = DataRes.Builder(page(body, unknownFloorBody, post(12))).apply {
            first_floor_post = unknownFloorBody
            user_list = listOf(user(1, 3))
        }.build(false)
        val result = apply(data)
        assertEquals(listOf(10L, 99L), result.post_list!!.map { it.id })
        assertEquals(5, result.post_list!![0].sub_post_list!!.sub_post_list!!.single().author!!.level_id)
        assertSame(unknownFloorBody, result.first_floor_post)
    }

    @Test fun hotAndTopListsAndAnswerUseTheSamePolicyWithoutChangingContext() {
        val low = post(2); val high = post(6)
        val data = DataRes.Builder(page(high)).apply {
            hot_post_list = PbHotPost.Builder().apply { post_list = listOf(low, high); hot_post_list = listOf(low) }.build(false)
            top_agree_post_list = PbTopAgreePost.Builder().apply { post_list = listOf(low, high) }.build(false)
            top_answer = low
        }.build(false)
        val result = apply(data)
        assertEquals(listOf(high), result.hot_post_list!!.post_list)
        assertTrue(result.hot_post_list!!.hot_post_list!!.isEmpty())
        assertEquals(listOf(high), result.top_agree_post_list!!.post_list)
        assertNull(result.top_answer)
        assertSame(data.post_list, result.post_list)
    }

    @Test fun expandedFloorRetainsContextAndPaginationButFiltersAllReplySurfaces() {
        val parent = post(1, 4, listOf(child(2)))
        val data = tbclient.PbFloor.DataRes.Builder().apply {
            post = parent; subpost_list = listOf(child(4), child(5), child(null)); subpost_num = 40; page = Any()
        }.build(false)
        val result = filter.filter(data, true, settings.copy(keepWithReplies = true)) as tbclient.PbFloor.DataRes
        assertEquals(listOf(5), result.subpost_list!!.map { it.author!!.level_id })
        assertEquals(parent.id, result.post!!.id)
        assertTrue(result.post!!.sub_post_list!!.sub_post_list!!.isEmpty())
        assertEquals(40, result.subpost_num)
        assertSame(data.page, result.page)
        assertEquals(3, data.subpost_list!!.size)
    }
}
