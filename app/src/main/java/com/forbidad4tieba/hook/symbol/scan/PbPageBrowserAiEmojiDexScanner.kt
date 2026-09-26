package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.DexPbPageBrowserAiEmojiCreationMatch
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

internal object PbPageBrowserAiEmojiDexScanner {
    fun match(method: MethodData): DexPbPageBrowserAiEmojiCreationMatch? {
        if (Modifier.isStatic(method.modifiers) || method.methodName == "<init>" ||
            method.returnTypeName != "void" || method.paramCount != 1) return null
        if ("uiState" !in method.usingStrings || method.invokes.none {
            it.declaredClassName == "com.baidu.tieba.pb.view.PbAiEmojiCreationView" &&
                it.methodName == "getCapsuleView"
        }) return null
        return DexPbPageBrowserAiEmojiCreationMatch(method.declaredClassName, method.methodName, 300,
            "uiState,PbAiEmojiCreationView.getCapsuleView")
    }
}
