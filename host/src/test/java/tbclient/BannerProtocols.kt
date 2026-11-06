package tbclient

class LayoutFactory {
    @JvmField var layout: String? = null
    @JvmField var banner_card: BannerCardLayout? = null
}

class BannerCardLayout {
    @JvmField var components: List<Any?>? = null
    @JvmField var log_param: List<Any?>? = null
}

class ComponentFactory {
    @JvmField var component: String? = null
    @JvmField var feed_banner: FeedBannerComponent? = null
}

class FeedBannerComponent

class FeedKV {
    @JvmField var key: String? = null
    @JvmField var value: String? = null
}
