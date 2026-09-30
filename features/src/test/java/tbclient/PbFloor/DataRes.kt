package tbclient.PbFloor

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class DataRes(b: Builder) {
    @JvmField val post = b.post
    @JvmField val subpost_list = b.subpost_list
    @JvmField val page = b.page
    @JvmField val subpost_num = b.subpost_num
    class Builder() {
        @JvmField var post: Post? = null
        @JvmField var subpost_list: List<SubPostList>? = null
        @JvmField var page: Any? = null
        @JvmField var subpost_num: Int? = null
        constructor(v: DataRes) : this() {
            post = v.post
            subpost_list = v.subpost_list
            page = v.page
            subpost_num = v.subpost_num
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): DataRes = DataRes(this)
    }
}
