package tbclient

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class PbHotPost(b: Builder) {
    @JvmField val hot_post_list = b.hot_post_list
    @JvmField val post_list = b.post_list
    class Builder() {
        @JvmField var hot_post_list: List<Post>? = null
        @JvmField var post_list: List<Post>? = null
        constructor(v: PbHotPost) : this() {
            hot_post_list = v.hot_post_list
            post_list = v.post_list
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): PbHotPost = PbHotPost(this)
    }
}
