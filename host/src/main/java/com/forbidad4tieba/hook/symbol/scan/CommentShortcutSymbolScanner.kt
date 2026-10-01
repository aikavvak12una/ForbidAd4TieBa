package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.json.JSONObject

internal object CommentShortcutSymbolScanner {
    const val VIEW = "com.baidu.tieba.pb.pagebrowser.ui.inner.SwitchBarView"
    const val TITLE = "com.baidu.tieba.view.TextLineView"
    const val ICON_PREFIX = "icon_forum_level_"
    const val PAGE = "com.baidu.tieba.pb.pagebrowser.viewmodel.PageBrowserViewModel"
    const val COMMENTS = "com.baidu.tieba.pb.pagebrowser.comment.viewmodel.CommentListViewModel"
    const val EXT = "com.baidu.tieba.pb.pagebrowser.utils.PageBrowserExtKt"
    const val ACTIVITY = "androidx.fragment.app.FragmentActivity"
    const val THREAD = "com.baidu.tbadk.core.data.ThreadData"

    fun scan(logger: ScanLogger?): String? = ScanDexQueries.query("CommentShortcut", logger) { bridge ->
        fun <T> one(label: String, items: List<T>): T = requireNotNull(
            uniqueSemanticCandidate("CommentShortcut.$label", items.distinct(), logger),
        ) { "Missing or ambiguous $label" }
        val methods = checkNotNull(bridge.getClassData(VIEW)).methods
        val bind = one("bind", methods.filter {
            it.returnTypeName == "void" && it.paramTypeNames.size == 1 && "curSwitchBarData" in it.usingStrings
        })
        val binding = one("binding", methods.filter { it.methodName == "getBinding" && it.paramTypeNames.isEmpty() })
        val host = one("hostTitle", checkNotNull(bridge.getClassData(binding.returnTypeName)).methods.filter {
            it.returnTypeName == TITLE && it.paramTypeNames.isEmpty() && "binding.hostTitle" in it.usingStrings
        })
        val iconOwners = ScanDexQueries.classesUsingStrings(logger, ICON_PREFIX)
        one("nativeLevelIcon", iconOwners.flatMap { owner -> checkNotNull(bridge.getClassData(owner)).methods.filter {
            it.returnTypeName == "int" && it.paramTypeNames == listOf("int") && ICON_PREFIX in it.usingStrings &&
                it.calls("android.content.res.Resources", "getIdentifier")
        } })
        val ownerClick = one("ownerClick", checkNotNull(bridge.getClassData(
            "com.baidu.tieba.pb.pagebrowser.ui.inner.PbInnerViewHolder",
        )).methods.filter { "pb_onlyowner_click" in it.usingStrings && it.paramTypeNames == listOf("boolean") })
        val page = one("page", ownerClick.invokes.filter { it.declaredClassName == EXT &&
            it.returnTypeName == PAGE && it.paramTypeNames == listOf(ACTIVITY) })
        val current = one("current", ownerClick.invokes.filter { it.declaredClassName == PAGE &&
            it.returnTypeName == COMMENTS && it.paramTypeNames.isEmpty() })
        val thread = one("thread", checkNotNull(bridge.getClassData(PAGE)).methods.filter {
            it.returnTypeName == THREAD && it.paramTypeNames.isEmpty()
        })
        val busy = one("busy", ownerClick.invokes.filter { it.declaredClassName == COMMENTS &&
            it.returnTypeName == "boolean" && it.paramTypeNames.isEmpty() })
        val refresh = one("refresh", checkNotNull(bridge.getClassData(COMMENTS)).methods.filter {
            "仅仅刷新当前评论区数据：}" in it.usingStrings && it.returnTypeName == "void" && it.paramTypeNames.size == 1
        })
        val intent = refresh.paramTypeNames.single()
        check(checkNotNull(bridge.getClassData(intent)).methods.any { "OnlyRefreshCommentIntent(showLoading=" in it.usingStrings })
        JSONObject().put("bind", bind.methodName).put("data", bind.paramTypeNames.single())
            .put("binding", binding.returnTypeName).put("host", host.methodName)
            .put("page", page.methodName).put("current", current.methodName).put("busy", busy.methodName)
            .put("refresh", refresh.methodName).put("intent", intent).put("thread", thread.methodName).toString()
    }
}
