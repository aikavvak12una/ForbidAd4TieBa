package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.forbidad4tieba.hook.symbol.model.AiImageField
import com.forbidad4tieba.hook.symbol.model.AiImageMethod
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject

/** Exact descriptors and role-specific structural checks, shared by scan and cache restoration. */
internal object AiImageCreationCodec {
    const val IMAGE_DATA = "com.baidu.tbadk.coreExtra.view.ImageUrlData"
    const val IMAGE_JUMP = "com.baidu.tbadk.coreExtra.data.ImageJumpIconData"
    const val URL_MANAGER = "com.baidu.tbadk.core.util.UrlManager"
    const val PAGE_CONTEXT = "com.baidu.tbadk.TbPageContext"
    const val URL_CANCEL = "com.baidu.tbadk.core.util.UrlManager\$UrlWebDialogCancelListener"
    const val WEB_CONFIG = "com.baidu.tbadk.core.atomData.TbWebViewActivityConfig"
    const val PB_GUIDE = "com.baidu.tieba.pb.view.PbAiEmojiCreationView"
    const val TIP = "com.baidu.tbadk.editortools.meme.view.AiMemeBottomTipView"

    fun encode(method: Method): String = JSONObject().apply {
        put("owner", method.declaringClass.name)
        put("name", method.name)
        put("params", JSONArray(method.parameterTypes.map { it.name }))
        put("return", method.returnType.name)
        put("static", Modifier.isStatic(method.modifiers))
    }.toString()

    fun encode(field: Field): String = JSONObject().apply {
        put("owner", field.declaringClass.name)
        put("name", field.name)
        put("type", field.type.name)
        put("static", Modifier.isStatic(field.modifiers))
    }.toString()

    fun restore(role: AiImageMethod, value: String, cl: ClassLoader): Method {
        val spec = JSONObject(value)
        require(spec.keys().asSequence().toSet() == setOf("owner", "name", "params", "return", "static"))
        val params = spec.getJSONArray("params")
        val method = type(spec.getString("owner"), cl).getDeclaredMethod(spec.getString("name"),
            *(0 until params.length()).map { type(params.getString(it), cl) }.toTypedArray())
        require(method.returnType == type(spec.getString("return"), cl))
        require(Modifier.isStatic(method.modifiers) == spec.getBoolean("static"))
        validate(role, method)
        return method.apply { isAccessible = true }
    }

    fun restore(role: AiImageField, value: String, cl: ClassLoader): Field {
        val spec = JSONObject(value)
        require(spec.keys().asSequence().toSet() == setOf("owner", "name", "type", "static"))
        val field = type(spec.getString("owner"), cl).getDeclaredField(spec.getString("name"))
        require(field.type == type(spec.getString("type"), cl))
        require(Modifier.isStatic(field.modifiers) == spec.getBoolean("static"))
        validate(role, field)
        return field.apply { isAccessible = true }
    }

    fun validate(role: AiImageMethod, method: Method) {
        require(!Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
            !Modifier.isAbstract(method.declaringClass.modifiers) && !method.declaringClass.isInterface) {
            "Invalid AI image instance method: $role"
        }
        val owner = method.declaringClass
        val params = method.parameterTypes.map { it.name }
        val valid = when (role) {
            AiImageMethod.IMAGE_JUMP_DATA -> owner.name == IMAGE_DATA && method.name == "getImageJumpIconData" &&
                params.isEmpty() && method.returnType.name == IMAGE_JUMP
            AiImageMethod.IMAGE_JUMP_SCHEME -> owner.name == IMAGE_JUMP && method.name == "getScheme" &&
                params.isEmpty() && method.returnType == String::class.java
            AiImageMethod.ROUTE_DISPATCH -> owner.name == URL_MANAGER && method.name == "dealOneLinkWithDialog" &&
                method.returnType == java.lang.Boolean.TYPE &&
                params == listOf(PAGE_CONTEXT, "java.lang.String", "[Ljava.lang.String;", "boolean", URL_CANCEL, "boolean", "android.os.Bundle")
            AiImageMethod.WEB_CONFIG_BUILD -> method.returnType.name == WEB_CONFIG &&
                params == listOf(Context::class.java.name)
            AiImageMethod.INPUT_AI_VISIBLE -> params.isEmpty() && method.returnType == Integer.TYPE
            AiImageMethod.INPUT_AI_SCHEMA -> params.isEmpty() && method.returnType == String::class.java
            AiImageMethod.PB_GUIDE_BIND -> owner.name == PB_GUIDE && LinearLayout::class.java.isAssignableFrom(owner) &&
                method.returnType == Void.TYPE && params == listOf("tbclient.SpriteMemeInfo", "int", "java.util.Map")
            AiImageMethod.PB_GUIDE_CAPSULE -> owner.name == PB_GUIDE && LinearLayout::class.java.isAssignableFrom(owner) &&
                method.name == "getCapsuleView" && params.isEmpty() && method.returnType == View::class.java
            AiImageMethod.IMAGE_BOTTOM_TIP_BIND -> owner.name == TIP && FrameLayout::class.java.isAssignableFrom(owner) &&
                method.returnType == Void.TYPE && params == listOf("java.lang.String", "java.lang.String")
        }
        require(valid) { "Invalid AI image signature: $role" }
    }

    fun validate(role: AiImageField, field: Field) {
        when (role) {
            AiImageField.WEB_URL -> require(!Modifier.isStatic(field.modifiers) && field.type == String::class.java) {
                "Invalid WebView builder URL field"
            }
        }
    }

    fun validateRelations(methods: Map<AiImageMethod, Method>, fields: Map<AiImageField, Field>) {
        fun sameOwner(first: AiImageMethod, second: AiImageMethod) {
            val a = methods[first] ?: return
            val b = methods[second] ?: return
            require(a.declaringClass == b.declaringClass) { "Different AI image owners: $first/$second" }
        }
        sameOwner(AiImageMethod.INPUT_AI_VISIBLE, AiImageMethod.INPUT_AI_SCHEMA)
        sameOwner(AiImageMethod.PB_GUIDE_BIND, AiImageMethod.PB_GUIDE_CAPSULE)
        val data = methods[AiImageMethod.IMAGE_JUMP_DATA]
        val scheme = methods[AiImageMethod.IMAGE_JUMP_SCHEME]
        if (data != null && scheme != null) require(data.returnType == scheme.declaringClass)
        val build = methods[AiImageMethod.WEB_CONFIG_BUILD]
        val url = fields[AiImageField.WEB_URL]
        if (build != null && url != null) require(build.declaringClass == url.declaringClass) { "Different WebView builder owner" }
        require(methods.values.distinct().size == methods.size) { "Aliased AI image method roles" }
    }

    private fun type(name: String, cl: ClassLoader): Class<*> = if (name == "void") Void.TYPE else
        requireNotNull(CacheTargetValidation.resolveCachedParameterClass(name, cl)) { "AI image type unavailable: $name" }
}
