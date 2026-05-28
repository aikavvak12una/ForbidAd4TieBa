package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context
import android.widget.ImageView
import java.lang.reflect.Modifier

internal object ImageViewerShareIconSymbolScanner {
    fun scanFromDex(
        context: Context,
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Int? {
        val ownerCandidates = findOwnerCandidates(candidates, cl)
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
                val nested = safeFindClass(owner.className, cl)
                    ?.declaredClasses
                    ?.map { it.name }
                    .orEmpty()
                listOf(owner.className) + nested
            }
            .distinct()
        val match = DexShareIconScanner.scan(sourcePaths, ownerClassNames, cl, logger)
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

    private fun findOwnerCandidates(
        candidates: List<String>,
        cl: ClassLoader,
    ): List<ShareIconOwnerCandidate> {
        return candidates.asSequence()
            .mapNotNull { className ->
                val cls = safeFindClass(className, cl) ?: return@mapNotNull null
                val score = scoreOwnerClass(cls)
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

    private fun scoreOwnerClass(cls: Class<*>): Int {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return 0
        if (!cls.name.startsWith("com.baidu.tieba.")) return 0

        val fields = runCatching { cls.declaredFields }.getOrNull() ?: return 0
        val constructors = runCatching { cls.declaredConstructors }.getOrNull() ?: return 0
        val methods = runCatching { cls.declaredMethods }.getOrNull() ?: return 0
        val nestedClasses = runCatching { cls.declaredClasses }.getOrNull().orEmpty()

        val hasImageViewField = fields.any { field ->
            ImageView::class.java.isAssignableFrom(field.type)
        }
        val hasHeadImageField = fields.any { field ->
            field.type.name.endsWith(".HeadImageView") ||
                field.type.name == "com.baidu.tbadk.core.view.HeadImageView"
        }
        val hasAnimatorField = fields.any { field ->
            field.type.name == "android.animation.ValueAnimator"
        }
        val hasImageViewCtor = constructors.any { ctor ->
            ctor.parameterTypes.size == 1 &&
                ImageView::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        val hasRunnableNested = nestedClasses.any { nested ->
            Runnable::class.java.isAssignableFrom(nested)
        }
        val boolFieldCount = fields.count { field ->
            field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java
        }
        val intFieldCount = fields.count { field ->
            field.type == Int::class.javaPrimitiveType || field.type == Int::class.java
        }
        val noArgBooleanMethods = methods.count { method ->
            method.parameterTypes.isEmpty() &&
                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java)
        }
        val noArgIntMethods = methods.count { method ->
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
        score -= methods.size / 6
        score -= fields.size / 4
        score -= cls.simpleName.length
        return if (score >= 120) score else 0
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
        HookSymbolResolver.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
