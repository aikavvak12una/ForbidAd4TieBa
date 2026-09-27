package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.DexAutoRefreshMatch
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** The refresh entry scrolls the feed to its start and starts the host refresh indicator. */
internal object AutoRefreshDexScanner {
    fun match(method: MethodData): DexAutoRefreshMatch? {
        if (Modifier.isStatic(method.modifiers) || method.returnTypeName != "void" || method.paramCount != 0) {
            return null
        }
        val invokes = method.invokes
        val selectsFeedStart = invokes.any {
            it.declaredClassName == "com.baidu.adp.widget.ListView.BdRecyclerView" &&
                it.methodName == "setSelection" && it.paramTypeNames == listOf("int")
        }
        val startsRefreshing = invokes.any {
            it.declaredClassName == "com.baidu.tieba.homepage.personalize.bigday.BigdaySwipeRefreshLayout" &&
                it.methodName == "setRefreshing" && it.paramTypeNames == listOf("boolean")
        }
        if (!selectsFeedStart || !startsRefreshing) return null
        return DexAutoRefreshMatch(method.methodName, 220, "BdRecyclerView.setSelection,BigdaySwipeRefreshLayout.setRefreshing")
    }
}
