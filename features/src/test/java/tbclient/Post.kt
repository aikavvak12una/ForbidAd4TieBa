package tbclient

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class Post(b: Builder) {
    @JvmField val id = b.id
    @JvmField val floor = b.floor
    @JvmField val author = b.author
    @JvmField val author_id = b.author_id
    @JvmField val sub_post_number = b.sub_post_number
    @JvmField val sub_post_list = b.sub_post_list
    class Builder() {
        @JvmField var id: Long? = null
        @JvmField var floor: Int? = null
        @JvmField var author: User? = null
        @JvmField var author_id: Long? = null
        @JvmField var sub_post_number: Int? = null
        @JvmField var sub_post_list: SubPost? = null
        constructor(v: Post) : this() {
            id = v.id
            floor = v.floor
            author = v.author
            author_id = v.author_id
            sub_post_number = v.sub_post_number
            sub_post_list = v.sub_post_list
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): Post = Post(this)
    }
}
