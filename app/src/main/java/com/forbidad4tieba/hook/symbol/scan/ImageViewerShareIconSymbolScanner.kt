package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.content.Context

internal object ImageViewerShareIconSymbolScanner {
    fun scanHostButtonResource(
        context: Context,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Int? {
        return resolveDrawableResource(context, cl, "icon_pure_topbar_share", logger)
            ?.also { log(logger, "imageViewerShareIconHost matched: icon_pure_topbar_share icon=$it") }
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

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }


}
