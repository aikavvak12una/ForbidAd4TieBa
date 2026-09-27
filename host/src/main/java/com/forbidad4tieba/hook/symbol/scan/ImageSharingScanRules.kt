package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.net.Uri
import android.view.View
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Modifier

internal class ShareTrackUrlBuilderRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        ScanDexQueries.classesUsingStrings(logger, "sfc=", "client_type=2")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        val methods = ScanDexQueries.methods(cls.name, logger)
        val compose = uniqueSemanticCandidate("ShareUrl.Compose", methods.filter {
            Modifier.isStatic(it.modifiers) && it.returnTypeName == "java.lang.String" &&
                it.paramTypeNames == listOf("java.lang.String", "java.lang.String", "java.lang.String", "boolean") &&
                it.usingStrings.containsAll(listOf("sfc=", "client_type=2", "client_version=", "android_url_need_cuid"))
        }, logger) ?: return null
        val append = uniqueSemanticCandidate("ShareUrl.AppendQuery", methods.filter { method ->
            Modifier.isStatic(method.modifiers) && method.returnTypeName == "java.lang.String" &&
                method.paramTypeNames == listOf("java.lang.String", "java.lang.String") &&
                method.usingStrings.containsAll(listOf("?", "&")) && compose.invokes.any { it.descriptor == method.descriptor }
        }, logger) ?: return null
        val composeMethod = compose.getMethodInstance(cl)
        val appendMethod = append.getMethodInstance(cl)
        return ScanMatch(cls.name, "${composeMethod.name},${appendMethod.name}", "", 140)
    }
}

/** ShareItem exposes public protocol fields. Missing fields stay missing instead of using another String. */
internal class ImageViewerShareItemRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tbadk.coreExtra.share.ShareItem")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tbadk.coreExtra.share.ShareItem") return null
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        fun field(name: String, type: Class<*> = String::class.java) = fields.singleOrNull { it.name == name && it.type == type }?.name
        val uri = field("imageUri", Uri::class.java) ?: return null
        val names = listOf(field("qqTitle"), field("content"), field("linkUrl"), uri, field("imageUrl"), field("localFile"))
        return ScanMatch(cls.name, "", names.joinToString(",") { it.orEmpty() }, 140)
    }
}

internal class ImageViewerShareConfigRule(private val shareItemClassName: String) : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tbadk.core.atomData.ShareDialogConfig")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (cls.name != "com.baidu.tbadk.core.atomData.ShareDialogConfig") return null
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        val dialog = fields.singleOrNull { it.name == "isImageViewerDialog" && it.type == Boolean::class.javaPrimitiveType } ?: return null
        val item = fields.singleOrNull { it.name == "shareItem" && it.type.name == shareItemClassName } ?: return null
        val methods = (cls.declaredMethods.toList() + cls.methods).filter { !Modifier.isStatic(it.modifiers) }
            .distinctBy { "${it.name}:${it.returnType.name}:${it.parameterTypes.joinToString { type -> type.name }}" }
        val outside = methods.singleOrNull {
            it.name == "addOutsideTextView" && it.returnType == Void.TYPE && it.parameterTypes.contentEquals(
                arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, View.OnClickListener::class.java),
            )
        } ?: return null
        val get = methods.singleOrNull { it.name == "getRequestData" && it.parameterCount == 0 && Map::class.java.isAssignableFrom(it.returnType) } ?: return null
        val set = methods.singleOrNull {
            it.name == "setRequestData" && it.returnType == Void.TYPE && it.parameterCount == 1 && Map::class.java.isAssignableFrom(it.parameterTypes[0])
        } ?: return null
        val context = methods.singleOrNull { it.name == "getContext" && it.parameterCount == 0 && Context::class.java.isAssignableFrom(it.returnType) } ?: return null
        return ScanMatch(cls.name, listOf(outside.name, get.name, set.name, context.name).joinToString(","), "${dialog.name},${item.name}", 140)
    }
}

internal class ImageViewerShareItemViewRule : ScanRule() {
    override fun candidateNames(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?) =
        listOf("com.baidu.tieba.sharesdk.view.ShareDialogItemView")

    override fun match(cls: Class<*>, cl: ClassLoader, logger: ScanLogger?): ScanMatch? {
        if (!View::class.java.isAssignableFrom(cls)) return null
        val methods = cls.declaredMethods.filter { it.name == "setItemName" && it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) }
        val byRes = methods.singleOrNull { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) } ?: return null
        val byText = methods.singleOrNull { it.parameterTypes.contentEquals(arrayOf(String::class.java)) } ?: return null
        return ScanMatch(cls.name, "${byRes.name},${byText.name}", "", 140)
    }
}
