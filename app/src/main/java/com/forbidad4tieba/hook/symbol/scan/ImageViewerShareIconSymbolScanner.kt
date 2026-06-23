package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import android.widget.ImageView
import com.forbidad4tieba.hook.symbol.dexkit.DexKitSemanticScanner
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object ImageViewerShareIconSymbolScanner {
    fun scanHostButtonResource(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Int? {
        return resolveDrawableResource(context, cl, "icon_pure_topbar_share", logger)
            ?.also { log(logger, "imageViewerShareIconHost matched: icon_pure_topbar_share icon=$it") }
    }

    fun scanFromDex(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Int? {
        val ownerCandidates = findOwnerCandidates(candidates, cl, logger)
        if (ownerCandidates.isEmpty()) {
            log(logger, "imageViewerShareIconDex: owner candidate not found")
            return null
        }

        val sourcePaths = appSourcePaths(context)
        if (sourcePaths.isEmpty()) {
            log(logger, "imageViewerShareIconDex: apk source path unavailable")
            return null
        }

        val ownerClassNames = ownerCandidates
            .flatMap { owner ->
                val nested = findNestedClassNames(owner.className, cl, logger)
                listOf(owner.className) + nested
            }
            .distinct()
        val match = DexKitSemanticScanner.scanShareIcon(
            sourcePaths = sourcePaths,
            ownerClassNames = ownerClassNames,
            cl = cl,
            resolveDrawableResource = { name -> resolveDrawableResource(context, cl, name, logger) },
            logger = logger,
        )
        if (match != null) {
            log(
                logger,
                "imageViewerShareIconDex matched: " +
                    "${match.ownerClassName}.${match.ownerMethodName} icon=${match.resId} score=${match.score}",
            )
            return match.resId
        }

        val ownerPreview = ownerCandidates
            .take(5)
            .joinToString(",") { "${it.className}:${it.score}" }
        log(logger, "imageViewerShareIconDex: no drawable constant matched, owners=$ownerPreview")
        return null
    }

    private fun resolveDrawableResource(
        context: Context,
        cl: ClassLoader,
        name: String,
        logger: ScanLogger?,
    ): Int? {
        val byResources = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (isResolvedDrawableResource(context, byResources, logger, "resources.getIdentifier($name)")) {
            return byResources
        }
        val byRClass = resolveRIntField(cl, "com.baidu.tieba.R\$drawable", name, logger)
        if (isResolvedDrawableResource(context, byRClass, logger, "com.baidu.tieba.R.drawable.$name")) {
            return byRClass
        }
        val byLivenpsRClass = resolveRIntField(cl, "com.baidu.searchbox.livenps.R\$drawable", name, logger)
        if (isResolvedDrawableResource(
                context,
                byLivenpsRClass,
                logger,
                "com.baidu.searchbox.livenps.R.drawable.$name",
            )
        ) {
            return byLivenpsRClass
        }
        log(logger, "imageViewerShareIconDex: drawable resource missing $name")
        return null
    }

    private fun resolveRIntField(
        cl: ClassLoader,
        className: String,
        fieldName: String,
        logger: ScanLogger?,
    ): Int? {
        val clazz = safeFindClass(className, cl) ?: return null
        val field = try {
            clazz.getDeclaredField(fieldName)
        } catch (_: NoSuchFieldException) {
            null
        } catch (t: Throwable) {
            log(logger, "imageViewerShareIconDex: read field failed $className.$fieldName: ${t.message}")
            null
        } ?: return null
        return try {
            field.isAccessible = true
            field.getInt(null).takeIf { it != 0 }
        } catch (t: Throwable) {
            log(logger, "imageViewerShareIconDex: get field failed $className.$fieldName: ${t.message}")
            null
        }
    }

    private fun isResolvedDrawableResource(
        context: Context,
        id: Int?,
        logger: ScanLogger?,
        source: String,
    ): Boolean {
        if (id == null || id <= 0) return false
        return try {
            val typeName = context.resources.getResourceTypeName(id)
            val entryName = context.resources.getResourceEntryName(id)
            val accepted = typeName == "drawable" &&
                (entryName.contains("share", ignoreCase = true) || source.contains("share", ignoreCase = true))
            if (!accepted) {
                log(logger, "imageViewerShareIconDex: rejected $source=$id type=$typeName entry=$entryName")
            }
            accepted
        } catch (t: Throwable) {
            log(logger, "imageViewerShareIconDex: rejected $source=$id: ${t.message}")
            false
        }
    }

    private fun findOwnerCandidates(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): List<ShareIconOwnerCandidate> {
        return candidates.asSequence()
            .mapNotNull { className ->
                val cls = safeFindClass(className, cl) ?: return@mapNotNull null
                val score = scoreOwnerClass(cls, logger)
                if (score > 0) ShareIconOwnerCandidate(className, score) else null
            }
            .sortedWith(
                compareByDescending<ShareIconOwnerCandidate> { it.score }
                    .thenBy { it.className.length }
                    .thenBy { it.className },
            )
            .take(16)
            .toList()
    }

    private fun scoreOwnerClass(cls: Class<*>, logger: ScanLogger?): Int {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return 0
        if (!cls.name.startsWith("com.baidu.tieba.")) return 0

        val shape = scanSubStep(
            tag = "ImageViewerNativeShareHook.Icon.${cls.name}.OwnerShape",
            logger = logger,
            fallback = null,
        ) {
            OwnerShape(
                fields = cls.declaredFields.toList(),
                constructors = cls.declaredConstructors.toList(),
                methods = cls.declaredMethods.toList(),
            )
        } ?: return 0
        val nestedClasses = scanSubStep(
            tag = "ImageViewerNativeShareHook.Icon.${cls.name}.NestedClasses",
            logger = logger,
            fallback = emptyList<Class<*>>(),
        ) {
            cls.declaredClasses.toList()
        }

        val hasImageViewField = shape.fields.any { field ->
            ImageView::class.java.isAssignableFrom(field.type)
        }
        val hasHeadImageField = shape.fields.any { field ->
            field.type.name.endsWith(".HeadImageView") ||
                field.type.name == "com.baidu.tbadk.core.view.HeadImageView"
        }
        val hasAnimatorField = shape.fields.any { field ->
            field.type.name == "android.animation.ValueAnimator"
        }
        val hasImageViewCtor = shape.constructors.any { ctor ->
            ctor.parameterTypes.size == 1 &&
                ImageView::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        val hasRunnableNested = nestedClasses.any { nested ->
            Runnable::class.java.isAssignableFrom(nested)
        }
        val boolFieldCount = shape.fields.count { field ->
            field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java
        }
        val intFieldCount = shape.fields.count { field ->
            field.type == Int::class.javaPrimitiveType || field.type == Int::class.java
        }
        val noArgBooleanMethods = shape.methods.count { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java)
        }
        val noArgIntMethods = shape.methods.count { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Int::class.javaPrimitiveType || method.returnType == Int::class.java)
        }

        if (!hasImageViewField || !hasAnimatorField || !hasImageViewCtor) return 0

        var score = 120
        if (hasHeadImageField) score += 24
        if (hasRunnableNested) score += 20
        score += boolFieldCount.coerceAtMost(3) * 6
        score += intFieldCount.coerceAtMost(2) * 5
        score += noArgBooleanMethods.coerceAtMost(2) * 4
        score += noArgIntMethods.coerceAtMost(1) * 4
        score -= shape.methods.size / 6
        score -= shape.fields.size / 4
        score -= cls.simpleName.length
        return if (score >= 120) score else 0
    }

    private fun findNestedClassNames(
        className: String,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): List<String> {
        val cls = safeFindClass(className, cl) ?: return emptyList()
        return scanSubStep(
            tag = "ImageViewerNativeShareHook.Icon.$className.OwnerNestedClassNames",
            logger = logger,
            fallback = emptyList(),
        ) {
            cls.declaredClasses.map { it.name }
        }
    }

    private fun appSourcePaths(context: Context): List<String> {
        return buildList {
            context.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            context.applicationInfo?.splitSourceDirs?.forEach { path ->
                if (!path.isNullOrBlank()) add(path)
            }
        }.distinct()
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }

    private data class OwnerShape(
        val fields: List<Field>,
        val constructors: List<Constructor<*>>,
        val methods: List<Method>,
    )
}
