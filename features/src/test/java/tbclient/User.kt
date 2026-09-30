package tbclient

import tbclient.*

/** Minimal immutable protocol fixture; all untouched values must survive builder copying. */
class User(b: Builder) {
    @JvmField val id = b.id
    @JvmField val level_id = b.level_id
    class Builder() {
        @JvmField var id: Long? = null
        @JvmField var level_id: Int? = null
        constructor(v: User) : this() {
            id = v.id
            level_id = v.level_id
        }
        @Suppress("UNUSED_PARAMETER")
        fun build(fillDefaults: Boolean): User = User(this)
    }
}
