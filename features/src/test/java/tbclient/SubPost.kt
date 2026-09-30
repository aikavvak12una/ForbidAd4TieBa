package tbclient

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class SubPost(b: Builder) {
    @JvmField val sub_post_list = b.sub_post_list
    class Builder() {
        @JvmField var sub_post_list: List<SubPostList>? = null
        constructor(v: SubPost) : this() {
            sub_post_list = v.sub_post_list
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): SubPost = SubPost(this)
    }
}
