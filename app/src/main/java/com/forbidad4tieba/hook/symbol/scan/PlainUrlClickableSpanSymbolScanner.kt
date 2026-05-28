package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import android.text.style.ClickableSpan
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PlainUrlClickableSpanSymbolScanner {
    private const val PLAIN_URL_CLICKABLE_SPAN_CLASS = "com.baidu.tieba.ui7"
    private const val PLAIN_URL_CLICKABLE_SPAN_TYPE_FIELD = "d"
    private const val PLAIN_URL_CLICKABLE_SPAN_URL_FIELD = "e"
    private const val PLAIN_URL_CLICKABLE_SPAN_TEXT_FIELD = "i"
    private val PLAIN_URL_CLICKABLE_SPAN_CONTAINER_CLASSES = listOf(
        "com.baidu.tieba.si7",
        "com.baidu.tieba.qi7",
        "com.baidu.tbadk.widget.richText.TbRichTextItem",
    )

    fun scan(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): PlainUrlClickableSpanScanSymbols {
        val spanClass = resolveSpanClass(candidates, cl, logger) ?: return PlainUrlClickableSpanScanSymbols()

        val onClickMethod = spanClass.declaredMethods.singleOrNull { method ->
            isOnClickMethod(method, "onClick")
        } ?: run {
            log(logger, "plainUrlClickableSpan: onClick(View) not found in ${spanClass.name}")
            return PlainUrlClickableSpanScanSymbols()
        }
        val resolvedFields = resolveFields(spanClass, logger) ?: run {
            log(logger, "plainUrlClickableSpan: fields mismatch in ${spanClass.name}")
            return PlainUrlClickableSpanScanSymbols()
        }
        val typeField = resolvedFields.typeField
        val urlField = resolvedFields.urlField
        val textField = resolvedFields.textField

        if (!isStructureValid(spanClass, onClickMethod, typeField, urlField, textField)) {
            log(logger, "plainUrlClickableSpan: structure mismatch in ${spanClass.name}")
            return PlainUrlClickableSpanScanSymbols()
        }

        val ownerClasses = collectOnClickOwners(candidates, cl, spanClass, onClickMethod.name)
        if (ownerClasses.isEmpty()) {
            log(logger, "plainUrlClickableSpan: no onClick owners found")
            return PlainUrlClickableSpanScanSymbols()
        }

        log(
            logger,
            "plainUrlClickableSpan matched: ${spanClass.name}.${onClickMethod.name} " +
                "owners=${ownerClasses.size} fields=${typeField.name}/${urlField.name}/${textField.name} " +
                "evidence=${resolvedFields.evidence}",
        )
        return PlainUrlClickableSpanScanSymbols(
            className = spanClass.name,
            onClickMethod = onClickMethod.name,
            onClickOwnerClasses = ownerClasses,
            typeField = typeField.name,
            urlField = urlField.name,
            textField = textField.name,
        )
    }

    private fun resolveSpanClass(
        candidates: List<String>,
        cl: ClassLoader,
        logger: ScanLogger?,
    ): Class<*>? {
        safeFindClass(PLAIN_URL_CLICKABLE_SPAN_CLASS, cl)
            ?.takeIf(::isFixedClassCandidate)
            ?.let { return it }

        val matches = ArrayList<PlainUrlClickableSpanClassMatch>()
        var skippedByReflection = 0
        for (className in candidates) {
            if (!isCandidateClassName(className)) continue
            val cls = try {
                safeFindClass(className, cl)
            } catch (_: Throwable) {
                skippedByReflection++
                null
            } ?: continue
            val score = scoreClass(cls)
            if (score >= 120) {
                matches.add(PlainUrlClickableSpanClassMatch(cls, score))
            }
        }
        if (skippedByReflection > 0) {
            log(logger, "plainUrlClickableSpan scan skipped classes by reflection=$skippedByReflection")
        }
        if (matches.isEmpty()) {
            log(logger, "plainUrlClickableSpan: no semantic class match among ${candidates.size} candidates")
            return null
        }

        val sorted = matches.sortedWith(
            compareByDescending<PlainUrlClickableSpanClassMatch> { it.score }
                .thenBy { it.clazz.name.length }
                .thenBy { it.clazz.name },
        )
        val best = sorted.first()
        val topScoreMatches = sorted.filter { it.score == best.score }
        if (topScoreMatches.size > 1) {
            val sample = topScoreMatches.take(5).joinToString("; ") { "${it.clazz.name}:${it.score}" }
            log(logger, "plainUrlClickableSpan ambiguous class score=${best.score}: $sample")
            return null
        }
        val second = sorted.getOrNull(1)
        if (second != null && best.score - second.score < 12) {
            log(
                logger,
                "plainUrlClickableSpan ambiguous class close score: " +
                    "best=${best.clazz.name}:${best.score}, second=${second.clazz.name}:${second.score}",
            )
            return null
        }

        log(logger, "plainUrlClickableSpan class matched by semantic scan: ${best.clazz.name} score=${best.score}")
        return best.clazz
    }

    private fun isFixedClassCandidate(cls: Class<*>): Boolean {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return false
        if (!ClickableSpan::class.java.isAssignableFrom(cls)) return false
        val hasOnClick = cls.declaredMethods.any { method ->
            isOnClickMethod(method, "onClick")
        }
        return hasOnClick && hasConstructor(cls)
    }

    private fun isCandidateClassName(className: String): Boolean {
        if (!className.startsWith("com.baidu.tieba.") && !className.startsWith("com.baidu.tbadk.")) return false
        val shortName = className.substringAfterLast('.')
        return isLikelyObfuscatedShortName(shortName) ||
            className.startsWith("com.baidu.tbadk.widget.richText.") ||
            shortName.contains("RichText", ignoreCase = true) ||
            shortName.contains("Clickable", ignoreCase = true) ||
            shortName.contains("Span", ignoreCase = true)
    }

    private fun scoreClass(cls: Class<*>): Int {
        if (cls.isInterface || Modifier.isAbstract(cls.modifiers)) return 0
        if (!ClickableSpan::class.java.isAssignableFrom(cls)) return 0
        if (!cls.declaredMethods.any { isOnClickMethod(it, "onClick") }) return 0
        if (!hasConstructor(cls)) return 0
        val fields = collectInstanceFields(cls)
        val intFieldCount = fields.count { !Modifier.isStatic(it.modifiers) && isIntType(it.type) }
        val stringFieldCount = fields.count { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        if (intFieldCount < 2 || stringFieldCount < 1) return 0

        var score = 110
        if (cls.name == PLAIN_URL_CLICKABLE_SPAN_CLASS) score += 45
        if (cls.name.startsWith("com.baidu.tieba.")) score += 12
        if (cls.simpleName.length <= 4) score += 8
        if (intFieldCount >= 4) score += 16 else score += intFieldCount * 3
        if (stringFieldCount >= 3) score += 16 else score += stringFieldCount * 4
        if (fields.any { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.type.isInterface &&
                    field.type.declaredMethods.any { method ->
                        method.returnType == Void.TYPE &&
                            method.parameterTypes.size == 1 &&
                            method.parameterTypes[0].isAssignableFrom(cls)
                    }
            }
        ) {
            score += 18
        }
        if (fields.any { field -> !Modifier.isStatic(field.modifiers) && Map::class.java.isAssignableFrom(field.type) }) {
            score += 8
        }
        if (fields.any { field -> !Modifier.isStatic(field.modifiers) && field.type.name == "tbclient.ThemeColorInfo" }) {
            score += 8
        }
        score -= cls.declaredMethods.size / 10
        score -= fields.size / 8
        return score
    }

    private fun collectOnClickOwners(
        candidates: List<String>,
        cl: ClassLoader,
        spanClass: Class<*>,
        onClickMethodName: String,
    ): List<String> {
        val owners = LinkedHashSet<String>()

        fun collect(clazz: Class<*>?) {
            if (clazz == null) return
            if (!spanClass.isAssignableFrom(clazz)) return
            val hasOnClick = clazz.declaredMethods.any { method ->
                isOnClickMethod(method, onClickMethodName)
            }
            if (hasOnClick) owners.add(clazz.name)
        }

        collect(spanClass)
        for (containerName in PLAIN_URL_CLICKABLE_SPAN_CONTAINER_CLASSES) {
            val containerClass = safeFindClass(containerName, cl) ?: continue
            for (nested in containerClass.declaredClasses) {
                collect(nested)
            }
        }
        for (className in candidates) {
            val shortName = className.substringAfterLast('.')
            if (!isLikelyObfuscatedShortName(shortName) && '$' !in className) continue
            val clazz = safeFindClass(className, cl) ?: continue
            collect(clazz)
        }
        return owners.toList()
    }

    private fun resolveFields(spanClass: Class<*>, logger: ScanLogger?): PlainUrlClickableSpanFieldSymbols? {
        probeConstructorFields(spanClass, logger)?.let { return it }
        resolveNamedFields(spanClass)?.let { return it }
        return resolveOrderedFields(spanClass)
    }

    private fun probeConstructorFields(
        spanClass: Class<*>,
        logger: ScanLogger?,
    ): PlainUrlClickableSpanFieldSymbols? {
        val constructors = spanClass.declaredConstructors
            .filter { ctor ->
                ctor.parameterTypes.size <= 5 &&
                    ctor.parameterTypes.any(::isIntType) &&
                    ctor.parameterTypes.any { it == String::class.java }
            }
            .sortedWith(compareBy({ it.parameterTypes.size }, { it.toGenericString() }))
        val fields = collectInstanceFields(spanClass)

        for (ctor in constructors) {
            val paramTypes = ctor.parameterTypes
            val firstIntIndex = paramTypes.indexOfFirst(::isIntType)
            val firstStringIndex = paramTypes.indexOfFirst { it == String::class.java }
            if (firstIntIndex < 0 || firstStringIndex < 0) continue

            val intMarkers = mutableMapOf<Int, Int>()
            val stringMarkers = mutableMapOf<Int, String>()
            val args = arrayOfNulls<Any>(paramTypes.size)
            var unsupportedPrimitive = false
            for (index in paramTypes.indices) {
                val type = paramTypes[index]
                args[index] = when {
                    isIntType(type) -> (775300 + index).also { intMarkers[index] = it }
                    type == String::class.java -> "https://tbhook.example/link-$index".also {
                        stringMarkers[index] = it
                    }
                    isBooleanType(type) -> false
                    type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> 0L
                    type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> 0f
                    type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> 0.0
                    type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType -> 0.toShort()
                    type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType -> 0.toByte()
                    type == Char::class.javaPrimitiveType || type == Char::class.javaObjectType -> 0.toChar()
                    type.isPrimitive -> {
                        unsupportedPrimitive = true
                        null
                    }
                    else -> null
                }
            }
            if (unsupportedPrimitive) continue

            val instance = runCatching {
                ctor.isAccessible = true
                ctor.newInstance(*args)
            }.getOrNull() ?: continue
            val typeMarker = intMarkers[firstIntIndex] ?: continue
            val urlMarker = stringMarkers[firstStringIndex] ?: continue

            val typeField = fields.singleOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    isIntType(field.type) &&
                    runCatching {
                        field.isAccessible = true
                        field.get(instance) == typeMarker
                    }.getOrDefault(false)
            } ?: continue
            val urlField = fields.singleOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.type == String::class.java &&
                    runCatching {
                        field.isAccessible = true
                        field.get(instance) == urlMarker
                    }.getOrDefault(false)
            } ?: continue
            val textField = pickTextField(fields, urlField)
            return PlainUrlClickableSpanFieldSymbols(
                typeField = typeField,
                urlField = urlField,
                textField = textField,
                evidence = "constructor",
            )
        }

        log(logger, "plainUrlClickableSpan: constructor probe unavailable in ${spanClass.name}")
        return null
    }

    private fun resolveNamedFields(spanClass: Class<*>): PlainUrlClickableSpanFieldSymbols? {
        val fields = collectInstanceFields(spanClass)
        val typeField = fields.singleOrNull { field ->
            field.name == PLAIN_URL_CLICKABLE_SPAN_TYPE_FIELD &&
                !Modifier.isStatic(field.modifiers) &&
                isIntType(field.type)
        } ?: return null
        val urlField = fields.singleOrNull { field ->
            field.name == PLAIN_URL_CLICKABLE_SPAN_URL_FIELD &&
                !Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        } ?: return null
        return PlainUrlClickableSpanFieldSymbols(
            typeField = typeField,
            urlField = urlField,
            textField = pickTextField(fields, urlField),
            evidence = "named",
        )
    }

    private fun resolveOrderedFields(spanClass: Class<*>): PlainUrlClickableSpanFieldSymbols? {
        val fields = collectInstanceFields(spanClass)
        val intFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && isIntType(field.type)
        }
        val stringFields = fields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == String::class.java
        }
        val typeField = intFields.getOrNull(3) ?: return null
        val urlField = stringFields.firstOrNull() ?: return null
        val textField = stringFields.getOrNull(2) ?: urlField
        return PlainUrlClickableSpanFieldSymbols(
            typeField = typeField,
            urlField = urlField,
            textField = textField,
            evidence = "fieldOrder",
        )
    }

    private fun pickTextField(fields: List<Field>, urlField: Field): Field {
        return fields.singleOrNull { field ->
            field.name == PLAIN_URL_CLICKABLE_SPAN_TEXT_FIELD &&
                !Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        } ?: urlField
    }

    internal fun isOnClickMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == View::class.java
    }

    internal fun isStructureValid(
        spanClass: Class<*>,
        onClickMethod: Method,
        typeField: Field,
        urlField: Field,
        textField: Field,
    ): Boolean {
        if (spanClass.isInterface || Modifier.isAbstract(spanClass.modifiers)) return false
        if (!ClickableSpan::class.java.isAssignableFrom(spanClass)) return false
        if (!isOnClickMethod(onClickMethod, onClickMethod.name)) return false
        if (Modifier.isStatic(typeField.modifiers) || !isIntType(typeField.type)) return false
        if (Modifier.isStatic(urlField.modifiers) || urlField.type != String::class.java) return false
        if (Modifier.isStatic(textField.modifiers) || textField.type != String::class.java) return false
        if (!hasConstructor(spanClass)) return false
        val fields = collectInstanceFields(spanClass)
        return fields.count { isIntType(it.type) } >= 4 &&
            fields.count { it.type == String::class.java } >= 3
    }

    private fun hasConstructor(spanClass: Class<*>): Boolean {
        return spanClass.declaredConstructors.any { ctor ->
            ctor.parameterTypes.any(::isIntType) &&
                ctor.parameterTypes.any { it == String::class.java }
        }
    }

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) return false
        }
        return true
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun isIntType(type: Class<*>): Boolean =
        HookSymbolResolver.isIntType(type)

    private fun isBooleanType(type: Class<*>): Boolean =
        HookSymbolResolver.isBooleanType(type)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
