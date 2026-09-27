package com.forbidad4tieba.hook.symbol.scan

import android.view.View
import com.forbidad4tieba.hook.symbol.model.DexEnterForumCapsuleMethodKind
import com.forbidad4tieba.hook.symbol.model.DexEnterForumCapsuleMethodMatch
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** Keep all field candidates until the initializer and refresher agree on the same view. */
internal object EnterForumCapsuleDexScanner {
    fun scanMethod(method: MethodData, cl: ClassLoader): List<DexEnterForumCapsuleMethodMatch> {
        if (Modifier.isStatic(method.modifiers) || method.returnTypeName != "void" || method.paramCount != 0) {
            return emptyList()
        }
        val fields = method.usingFields.filter { it.field.declaredClassName == method.declaredClassName }
        val invokes = method.invokes
        val viewFields = fields.filter {
            !Modifier.isStatic(it.field.modifiers) &&
                ScanReflection.isAssignableTo(it.field.typeName, View::class.java, cl)
        }
        val writes = viewFields.filter { it.usingType.isWrite() }.map { it.field.fieldName }.distinct()
        val reads = viewFields.filter { it.usingType.isRead() }.map { it.field.fieldName }.distinct()
        val titles = fields.filter { it.usingType.isRead() && it.field.typeName == "java.lang.String" }
            .map { it.field.fieldName }.distinct()
        val addsNavigationView = invokes.any {
            it.declaredClassName == "com.baidu.tbadk.core.view.NavigationBar" && it.methodName == "addCustomView"
        }
        val findsView = invokes.any { it.methodName == "findViewById" }
        val setsClick = invokes.any { it.methodName == "setOnClickListener" }
        val checksTitle = invokes.any {
            it.declaredClassName == "android.text.TextUtils" && it.methodName == "isEmpty"
        }
        val stylesBackground = invokes.any {
            it.methodName == "setBackgroundResource" ||
                it.declaredClassName == "com.baidu.tbadk.core.elementsMaven.EMManager"
        }
        val setsVisibility = invokes.any { it.methodName == "setVisibility" }
        return buildList {
            if (addsNavigationView && findsView) {
                for (field in writes) add(DexEnterForumCapsuleMethodMatch(
                    ownerMethodName = method.methodName,
                    kind = DexEnterForumCapsuleMethodKind.INIT,
                    score = 245 + if (setsClick) 40 else 0,
                    evidence = "NavigationBar.addCustomView,findViewById,viewField=$field",
                    viewFieldName = field,
                ))
            }
            if (stylesBackground && (checksTitle || setsVisibility)) {
                for (view in reads) for (title in titles) add(DexEnterForumCapsuleMethodMatch(
                    ownerMethodName = method.methodName,
                    kind = DexEnterForumCapsuleMethodKind.REFRESH,
                    score = 235,
                    evidence = "background,titleField=$title,viewField=$view",
                    viewFieldName = view,
                    titleFieldName = title,
                ))
            }
        }
    }
}
