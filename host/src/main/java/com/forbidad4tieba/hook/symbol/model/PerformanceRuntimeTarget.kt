package com.forbidad4tieba.hook.symbol.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Stable host entry points; discovery and restoration share the exact signature contract. */
enum class PerformanceRuntimeTarget(
    val className: String,
    val methodName: String,
    val returnTypeName: String,
    val parameterTypeNames: List<String>,
    val isStatic: Boolean,
    val featureKey: String,
    val semanticKey: String? = null,
) {
    AD_CSJ(
        "com.baidu.tbadk.switchs.CsjInitSwitchInject", "isOpen", "boolean", emptyList(), true,
        HookFeatureKey.DISABLE_AD_SDK_COMPONENTS, "platform_csj_init",
    ),
    AD_GDT(
        "com.baidu.tbadk.switchs.GdtInitSwitch", "isOn", "boolean", emptyList(), true,
        HookFeatureKey.DISABLE_AD_SDK_COMPONENTS, "platform_gdt_init",
    ),
    AD_BAIDU(
        "com.baidu.tbadk.switchs.BaiduBqtInitSwitch", "isOn", "boolean", emptyList(), true,
        HookFeatureKey.DISABLE_AD_SDK_COMPONENTS, "platform_baidu_bqt_init",
    );

    internal fun matchesSignature(modifiers: Int, returnType: String, parameters: List<String>): Boolean =
        Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) == isStatic &&
            returnType == returnTypeName && parameters == parameterTypeNames

    internal fun matches(method: Method): Boolean =
        method.declaringClass.name == className && method.name == methodName &&
            matchesSignature(method.modifiers, method.returnType.name, method.parameterTypes.map { it.name })
}
