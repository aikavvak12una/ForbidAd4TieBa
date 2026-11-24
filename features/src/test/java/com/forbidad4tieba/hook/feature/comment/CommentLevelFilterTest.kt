package com.forbidad4tieba.hook.feature.comment

import com.forbidad4tieba.hook.config.CommentLevelFilterSettings
import com.forbidad4tieba.hook.symbol.model.CommentProtocol
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tbclient.PbPage.DataRes
import tbclient.Post
import tbclient.SubPost
import tbclient.SubPostList
import tbclient.User

class CommentLevelFilterTest {
    private val filter = CommentLevelFilter(CommentProtocol(javaClass.classLoader!!))
    private val settings = CommentLevelFilterSettings(true, 5, false, false)

    @Test
    fun thresholdKeepsEqualityHidesUnknownLevelsAndDisabledPassesIdentity() {
        val data = page(post(4), post(5), post(6), post(null), post(0), post(-1))
        val result = apply(data)

        assertEquals(listOf(5, 6), result.post_list!!.map { it.author!!.level_id })
        assertSame(data, apply(data, settings.copy(enabled = false)))
        assertSame(data.page, result.page)
        assertSame(result, apply(result))

        val minimumOne = settings.copy(minimumLevel = 1)
        val ungraded = page(post(null), post(0), post(-1), post(1), post(18))
        assertEquals(listOf(1, 18), apply(ungraded, minimumOne).post_list!!.map { it.author!!.level_id })
    }

    @Test
    fun nestedSkipAndKeepWithRepliesRespectTheOriginalCardContext() {
        val replies = listOf(child(null), child(1), child(5))
        val low = post(null, replies.size, replies)
        val high = post(5, replies.size, replies)
        val skip = settings.copy(skipNested = true)

        val result = apply(page(low, high), skip)
        assertEquals(listOf(high), result.post_list)
        assertSame(high, result.post_list!!.single())
        assertSame(high, apply(page(low, high), skip.copy(keepWithReplies = true)).post_list!!.last())

        val expanded = tbclient.PbFloor.DataRes.Builder().apply {
            post = low
            subpost_list = replies
            subpost_num = 40
            page = Any()
        }.build(false)
        assertSame(expanded, filter.filter(expanded, true, skip))
        assertEquals(
            listOf(5),
            (filter.filter(expanded, true, settings) as tbclient.PbFloor.DataRes)
                .subpost_list!!.map { it.author!!.level_id },
        )
    }

    @Test
    fun everyCommentSurfaceUsesThePolicyAndPreservesItsContext() {
        val low = post(2, id = 20)
        val high = post(6, id = 21)
        val data = DataRes.Builder(page(high)).apply {
            first_floor_post = high
            hot_post_list = tbclient.PbHotPost.Builder().apply {
                post_list = listOf(low, high)
                hot_post_list = listOf(low)
            }.build(false)
            top_agree_post_list = tbclient.PbTopAgreePost.Builder().apply {
                post_list = listOf(low, high)
            }.build(false)
            top_answer = low
        }.build(false)
        val result = apply(data)

        assertSame(high, result.first_floor_post)
        assertEquals(listOf(high), result.hot_post_list!!.post_list)
        assertTrue(result.hot_post_list!!.hot_post_list!!.isEmpty())
        assertEquals(listOf(high), result.top_agree_post_list!!.post_list)
        assertNull(result.top_answer)
        assertSame(data.post_list, result.post_list)

        val expanded = tbclient.PbFloor.DataRes.Builder().apply {
            post = post(1, 3, listOf(child(2)))
            subpost_list = listOf(child(4), child(5), child(null))
            subpost_num = 40
            page = Any()
        }.build(false)
        val expandedResult = filter.filter(
            expanded,
            true,
            settings.copy(keepWithReplies = true),
        ) as tbclient.PbFloor.DataRes
        assertEquals(listOf(5), expandedResult.subpost_list!!.map { it.author!!.level_id })
        assertEquals(expanded.post!!.id, expandedResult.post!!.id)
        assertEquals(40, expandedResult.subpost_num)
        assertSame(expanded.page, expandedResult.page)
    }

    private fun user(id: Long, level: Int?) = User.Builder().apply {
        this.id = id
        level_id = level
    }.build(false)

    private fun child(level: Int?) = SubPostList.Builder().apply {
        author = user(2, level)
        author_id = 2
    }.build(false)

    private fun post(
        level: Int?,
        count: Int = 0,
        children: List<SubPostList> = emptyList(),
        id: Long = 10,
    ) = Post.Builder().apply {
        this.id = id
        floor = 2
        author = user(1, level)
        author_id = 1
        sub_post_number = count
        sub_post_list = SubPost.Builder().apply {
            sub_post_list = Collections.unmodifiableList(children)
        }.build(false)
    }.build(false)

    private fun page(vararg posts: Post) = DataRes.Builder().apply {
        post_list = Collections.unmodifiableList(posts.toList())
        page = Any()
    }.build(false)

    private fun apply(data: DataRes, value: CommentLevelFilterSettings = settings) =
        filter.filter(data, false, value) as DataRes
}
