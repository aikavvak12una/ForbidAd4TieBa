package tbclient

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class SubPostList(b: Builder) {
    @JvmField val author = b.author
    @JvmField val author_id = b.author_id
    @JvmField val id = b.id
    class Builder() {
        @JvmField var author: User? = null
        @JvmField var author_id: Long? = null
        @JvmField var id: Long? = null
        constructor(v: SubPostList) : this() {
            author = v.author
            author_id = v.author_id
            id = v.id
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): SubPostList = SubPostList(this)
    }
}
