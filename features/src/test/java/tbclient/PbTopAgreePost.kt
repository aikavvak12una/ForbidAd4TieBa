package tbclient

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class PbTopAgreePost(b: Builder) {
    @JvmField val post_list = b.post_list
    class Builder() {
        @JvmField var post_list: List<Post>? = null
        constructor(v: PbTopAgreePost) : this() {
            post_list = v.post_list
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): PbTopAgreePost = PbTopAgreePost(this)
    }
}
