package tbclient.Personalized

class DataRes : com.squareup.wire.Message() {
    @JvmField var page_data: PageData? = null
    @JvmField var thread_list: List<Any?>? = null
    class Builder
}

class PageData {
    @JvmField var feed_list: List<Any?>? = null
}
