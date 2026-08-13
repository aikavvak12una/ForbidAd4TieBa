package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import android.view.View
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import com.forbidad4tieba.hook.symbol.model.CommentAvatarDirectProfileScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Locates the comment-floor adapter's listener-wiring method across versions.
 *
 * The host wires comment-floor avatar clicks inside a method with the stable signature
 * `void (PbCommenFloorItemViewHolder, int, PostData, View)` (22.9.1.0: `itd.S`, 22.0.1.0:
 * `w0d.R`). Subclass overrides (`jtd.S`/`h9d.S`/`x0d.R`) share the signature but only call
 * `super` and set extra listeners, so DexKit bytecode verification (the base method body
 * invokes `HeadPendantView.getHeadView` + `setOnClickListener`) selects the base method.
 * When DexKit is unavailable the reflection fallback picks the candidate whose declaring
 * class is a superclass of every other candidate.
 *
 * Also resolves the holder's avatar fields (`p` -> HeadImageView, `D` -> HeadPendantView)
 * and the `PostData` user getter (no-arg method returning `MetaData`).
 */
internal object CommentAvatarDirectProfileSymbolScanner {

    private const val TAG = "CommentAvatarDirectProfile"
    private const val HOLDER_CLASS = "com.baidu.tieba.pb.widget.holder.PbCommenFloorItemViewHolder"
    private const val POST_DATA_CLASS = "com.baidu.tieba.tbadkcore.data.PostData"
    private const val META_DATA_CLASS = "com.baidu.tbadk.data.MetaData"
    private const val HEAD_PENDANT_VIEW_CLASS = "com.baidu.tbadk.core.view.HeadPendantView"
    private const val HEAD_IMAGE_VIEW_CLASS = "com.baidu.tbadk.core.view.HeadImageView"

    fun scan(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): CommentAvatarDirectProfileScanSymbols {
        val holderClass = ScanReflection.safeFindClass(HOLDER_CLASS, cl)
        val postDataClass = ScanReflection.safeFindClass(POST_DATA_CLASS, cl)
        if (holderClass == null || postDataClass == null) {
            log(logger, "$TAG skipped: holder=$holderClass postData=$postDataClass")
            return CommentAvatarDirectProfileScanSymbols()
        }

        val wireCandidates = collectWireCandidates(candidates, cl, holderClass, postDataClass, logger)
        if (wireCandidates.isEmpty()) {
            log(logger, "$TAG wire method candidates empty")
            return CommentAvatarDirectProfileScanSymbols()
        }
        val wire = selectWireCandidate(wireCandidates, context, cl, logger)
            ?: return CommentAvatarDirectProfileScanSymbols()

        val headField = resolveHolderField(holderClass, HEAD_IMAGE_VIEW_CLASS, cl, "head", logger)
        val pendantField = resolveHolderField(holderClass, HEAD_PENDANT_VIEW_CLASS, cl, "pendant", logger)
        if (headField == null || pendantField == null) {
            log(logger, "$TAG holder avatar fields missing: head=$headField pendant=$pendantField")
            return CommentAvatarDirectProfileScanSymbols(
                wireClass = wire.first,
                wireMethod = wire.second,
            )
        }

        val metaDataClass = ScanReflection.safeFindClass(META_DATA_CLASS, cl)
        val postDataUserMethod = resolvePostDataUserMethod(postDataClass, metaDataClass, cl, logger)
        if (postDataUserMethod == null) {
            log(logger, "$TAG PostData user getter not found")
            return CommentAvatarDirectProfileScanSymbols(
                wireClass = wire.first,
                wireMethod = wire.second,
                holderHeadField = headField,
                holderHeadPendantField = pendantField,
            )
        }

        log(
            logger,
            "$TAG matched: ${wire.first}.${wire.second} " +
                "head=$headField pendant=$pendantField userMethod=$postDataUserMethod",
        )
        return CommentAvatarDirectProfileScanSymbols(
            wireClass = wire.first,
            wireMethod = wire.second,
            postDataUserMethod = postDataUserMethod,
            holderHeadField = headField,
            holderHeadPendantField = pendantField,
        )
    }

    private fun collectWireCandidates(
        candidates: List<String>,
        cl: ClassLoader,
        holderClass: Class<*>,
        postDataClass: Class<*>,
        logger: ScanLogger?,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(4)
        var skipped = 0
        var firstError: String? = null
        for (className in candidates) {
            try {
                val cls = ScanReflection.safeFindClass(className, cl) ?: continue
                val method = scanSubStep("$TAG.$className.Methods", logger, null) {
                    cls.declaredMethods.firstOrNull { method ->
                        isWireMethod(method, holderClass, postDataClass)
                    }
                } ?: continue
                out.add(cls.name to method.name)
            } catch (t: Throwable) {
                skipped++
                if (firstError == null) {
                    firstError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                        HookSymbolScanDiagnostics.formatScanException(t),
                    )
                }
            }
        }
        if (out.isEmpty() && skipped > 0) {
            HookSymbolScanDiagnostics.log(
                logger,
                "$TAG wire candidates empty, skipped=$skipped firstException=$firstError",
            )
        }
        return out.distinct()
    }

    internal fun isWireMethod(method: Method, holderClass: Class<*>, postDataClass: Class<*>): Boolean {
        if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE) return false
        val params = method.parameterTypes
        if (params.size != 4) return false
        if (!holderClass.isAssignableFrom(params[0])) return false
        if (params[1] != Int::class.javaPrimitiveType && params[1] != Int::class.java) return false
        if (!postDataClass.isAssignableFrom(params[2])) return false
        return params[3] == View::class.java
    }

    private fun selectWireCandidate(
        candidates: List<Pair<String, String>>,
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Pair<String, String>? {
        if (candidates.isEmpty()) return null
        val sourcePaths = appSourcePaths(context)
        val verified = if (sourcePaths.isNotEmpty()) {
            DexKitSemanticScanner.verifyCommentFloorWireBodies(sourcePaths, candidates, logger)
        } else {
            emptyList()
        }
        if (verified.size == 1) {
            log(logger, "$TAG DexKit verified: ${verified.single().first}.${verified.single().second}")
            return verified.single()
        }
        if (verified.size > 1) {
            log(logger, "$TAG DexKit ambiguous: $verified")
            return null
        }
        log(logger, "$TAG DexKit verification unavailable, reflection base fallback")
        return selectReflectionBase(candidates, cl, logger)
    }

    internal fun selectReflectionBase(
        candidates: List<Pair<String, String>>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Pair<String, String>? {
        val bases = candidates.filter { (className, _) ->
            val cls = ScanReflection.safeFindClass(className, cl) ?: return@filter false
            candidates.all { (other, _) ->
                other == className ||
                    ScanReflection.safeFindClass(other, cl)?.let { cls.isAssignableFrom(it) } == true
            }
        }
        if (bases.size != 1) {
            log(logger, "$TAG reflection base ambiguous: $bases")
            return null
        }
        return bases.single()
    }

    internal fun resolveHolderField(
        holderClass: Class<*>,
        fieldTypeName: String,
        cl: ClassLoader,
        label: String,
        logger: ScanLogger?,
    ): String? {
        val type = ScanReflection.safeFindClass(fieldTypeName, cl) ?: return null
        val fields = scanSubStep("$TAG.holder.$label.Fields", logger, null) {
            holderClass.declaredFields.toList()
        } ?: return null
        val matches = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && type.isAssignableFrom(field.type)
        }
        if (matches.size != 1) {
            HookSymbolScanDiagnostics.log(
                logger,
                "$TAG holder $label field matches=${matches.size}: ${matches.map { it.name }}",
            )
            return null
        }
        return matches.single().name
    }

    internal fun resolvePostDataUserMethod(
        postDataClass: Class<*>,
        metaDataClass: Class<*>?,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): String? {
        if (metaDataClass == null) return null
        val methods = scanSubStep("$TAG.PostData.Methods", logger, null) {
            postDataClass.declaredMethods.toList()
        } ?: return null
        val matches = methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                metaDataClass.isAssignableFrom(method.returnType)
        }
        if (matches.size != 1) {
            HookSymbolScanDiagnostics.log(
                logger,
                "$TAG PostData user getter matches=${matches.size}: ${matches.map { it.name }}",
            )
            return null
        }
        return matches.single().name
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
