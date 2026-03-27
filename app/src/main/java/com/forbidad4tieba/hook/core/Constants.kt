package com.forbidad4tieba.hook.core

object Constants {
    const val TAG = "TiebaHook"
    const val TARGET_PACKAGE = "com.baidu.tieba"
    
    val AD_CLASS_NAMES = arrayOf(
        "com.baidu.tieba.funad.view.AbsFeedAdxView",
        "com.baidu.tieba.recapp.lego.view.AdCardBaseView",
        "com.baidu.tieba.funad.view.TbAdVideoView",
        "com.baidu.tieba.feed.ad.compact.DelegateFunAdView",
        "com.baidu.tieba.pb.pb.main.view.PbImageAlaRecommendView",
        "com.baidu.tieba.core.widget.recommendcard.RecommendCardView"
    )
}
