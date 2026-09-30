package tbclient.PbPage

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class DataRes(b: Builder) {
    @JvmField val post_list = b.post_list
    @JvmField val user_list = b.user_list
    @JvmField val first_floor = b.first_floor
    @JvmField val first_floor_post = b.first_floor_post
    @JvmField val top_answer = b.top_answer
    @JvmField val hot_post_list = b.hot_post_list
    @JvmField val top_agree_post_list = b.top_agree_post_list
    @JvmField val page = b.page
    class Builder() {
        @JvmField var post_list: List<Post>? = null
        @JvmField var user_list: List<User>? = null
        @JvmField var first_floor: Post? = null
        @JvmField var first_floor_post: Post? = null
        @JvmField var top_answer: Post? = null
        @JvmField var hot_post_list: PbHotPost? = null
        @JvmField var top_agree_post_list: PbTopAgreePost? = null
        @JvmField var page: Any? = null
        constructor(v: DataRes) : this() {
            post_list = v.post_list
            user_list = v.user_list
            first_floor = v.first_floor
            first_floor_post = v.first_floor_post
            top_answer = v.top_answer
            hot_post_list = v.hot_post_list
            top_agree_post_list = v.top_agree_post_list
            page = v.page
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): DataRes = DataRes(this)
    }
}
