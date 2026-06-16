package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PbEarlyAdInsertSymbolScanner {
    private const val PB_AD_INSERT_CLASS = "com.baidu.tieba.pb.pb.main.underlayer.PbAdapterManagerInsertUtilKt"
    private const val MIN_METHOD_COUNT = 2
    // Sample obfuscated type names are kept only for the exact fast path.
    private const val PB_DATA_CLASS = "com.baidu.tieba.iic"
    private const val PB_FRAGMENT_CLASS = "com.baidu.tieba.pb.pb.main.PbFragment"
    private const val PB_ADAPTER_DATA_CLASS = "com.baidu.tieba.yf"
    private const val THREAD_DATA_CLASS = "com.baidu.tbadk.core.data.ThreadData"

    fun scan(candidates: List<String>, cl: ClassLoader, logger: ScanLogger?): PbEarlyAdInsertScanSymbols {
        val targetClassNames = (listOf(PB_AD_INSERT_CLASS) +
            candidates.filter(::isCandidateClassName)).distinct()
        val matches = ArrayList<ScanMatch>()
        val diagnostics = ArrayList<ScanMatch>()
        var fixedClassFound = false
        var skippedByReflection = 0

        for (className in targetClassNames) {
            try {
                val targetClass = safeFindClass(className, cl) ?: continue
                fixedClassFound = fixedClassFound || className == PB_AD_INSERT_CLASS
                if (targetClass.isInterface || Modifier.isAbstract(targetClass.modifiers)) continue
                val targetMethods = declaredMethods("Candidate", targetClass, logger) ?: run {
                    skippedByReflection++
                    continue
                }
                buildDiagnosticMatch(targetClass, targetMethods, logger)?.let(diagnostics::add)

                val specs = scanMethodSpecs(
                    targetClass,
                    targetMethods,
                    cl,
                    logger = null,
                    logExactMismatch = false,
                )
                if (specs.size < MIN_METHOD_COUNT) {
                    if (className == PB_AD_INSERT_CLASS && specs.isNotEmpty()) {
                        log(logger, "pbEarlyAdInsert: fixed class incomplete specs=${specs.size}, class=$className")
                    }
                    continue
                }

                val score = scoreCandidate(targetClass, targetMethods, specs, logger)
                matches.add(ScanMatch(className, specs.joinToString(";"), "", score))
            } catch (t: Throwable) {
                skippedByReflection++
                log(
                    logger,
                    "pbEarlyAdInsert: skip class=$className reflection failed: " +
                        "${t.javaClass.simpleName}:${t.message}",
                )
            }
        }

        if (skippedByReflection > 0) {
            log(logger, "pbEarlyAdInsert scan skipped classes by reflection=$skippedByReflection")
        }

        if (!fixedClassFound) {
            log(logger, "pbEarlyAdInsert: fixed class not found: $PB_AD_INSERT_CLASS")
        }

        val match = ScanReflection.chooseUniqueScanMatch(
            tag = "pbEarlyAdInsert",
            ruleName = "PbEarlyAdInsertSemanticRule",
            matches = matches,
            logger = logger,
            minScore = 140,
            minScoreGap = 12,
        )
        if (match == null) {
            logDiagnostics(diagnostics, logger)
            log(logger, "pbEarlyAdInsert: no complete unique semantic match, scanned=${targetClassNames.size}")
            return PbEarlyAdInsertScanSymbols(null, emptyList())
        }

        val specs = match.methodName
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return PbEarlyAdInsertScanSymbols(match.className, specs)
    }

    private fun isCandidateClassName(className: String): Boolean {
        val shortName = className.substringAfterLast('.')
        if (isLikelyObfuscatedShortName(shortName) &&
            (className.startsWith("com.baidu.tieba.") || className.startsWith("com.baidu.tbadk."))) {
            return true
        }
        if (!className.startsWith("com.baidu.tieba.pb.")) return false
        return shortName.endsWith("Kt") ||
            shortName.contains("Insert", ignoreCase = true) ||
            shortName.contains("Ad", ignoreCase = true) ||
            shortName.contains("Adapter", ignoreCase = true) ||
            isLikelyObfuscatedShortName(shortName)
    }

    private fun buildDiagnosticMatch(
        targetClass: Class<*>,
        targetMethods: List<Method>,
        logger: ScanLogger?,
    ): ScanMatch? {
        val metadata = kotlinMetadataStrings(targetClass, logger)
        val metadataScore = metadata.count { text ->
            text.contains("Ad") ||
                text.contains("Advert") ||
                text.contains("Adx") ||
                text.contains("FunAd") ||
                text.contains("insert", ignoreCase = true)
        }
        val interestingMethods = targetMethods.mapNotNull { method ->
            if (!Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers)) return@mapNotNull null
            val typeNames = method.parameterTypes.map { it.name } + method.returnType.name
            var score = 0
            if (typeNames.any { it == PB_FRAGMENT_CLASS }) score += 4
            if (typeNames.any { it == THREAD_DATA_CLASS }) score += 3
            if (typeNames.any { it == android.util.SparseArray::class.java.name }) score += 4
            if (typeNames.any { it == ArrayList::class.java.name }) score += 2
            if (method.returnType == Void.TYPE) score += 1
            if (score >= 6) method to score else null
        }
        if (interestingMethods.isEmpty() && metadataScore == 0) return null

        var score = 60 + metadataScore * 5
        if (targetClass.name.contains(".pb.", ignoreCase = true)) score += 10
        if (targetClass.name.contains("underlayer", ignoreCase = true)) score += 10
        if (targetClass.simpleName.endsWith("Kt")) score += 8
        score += interestingMethods.sumOf { it.second }.coerceAtMost(80)
        score -= targetMethods.size / 12
        val methodText = interestingMethods
            .sortedWith(compareByDescending<Pair<Method, Int>> { it.second }.thenBy { it.first.name })
            .take(6)
            .joinToString("; ") { describeMethodShape(it.first) }
        return ScanMatch(targetClass.name, methodText, "", score)
    }

    private fun logDiagnostics(diagnostics: List<ScanMatch>, logger: ScanLogger?) {
        if (diagnostics.isEmpty()) {
            log(logger, "pbEarlyAdInsert diag: no partial candidates with PbFragment/list signatures")
            return
        }
        val sample = diagnostics
            .sortedWith(compareByDescending<ScanMatch> { it.score }.thenBy { it.className })
            .take(6)
            .joinToString(" || ") { "${it.className}:${it.score}[${it.methodName.ifBlank { "-" }}]" }
        log(logger, "pbEarlyAdInsert diag top partial candidates: $sample")
    }

    private fun scoreCandidate(
        targetClass: Class<*>,
        targetMethods: List<Method>,
        specs: List<String>,
        logger: ScanLogger?,
    ): Int {
        val metadata = kotlinMetadataStrings(targetClass, logger)
        val specText = specs.joinToString(";")
        var score = 120
        if (targetClass.name == PB_AD_INSERT_CLASS) score += 80
        if (targetClass.name.startsWith("com.baidu.tieba.pb.")) score += 30
        if (targetClass.name.contains(".underlayer.")) score += 20
        if (targetClass.simpleName.endsWith("Kt")) score += 20
        if (metadata.any { it.contains("insertFunAd") || it.contains("handlePbCommentFunAd") }) score += 40
        if (metadata.any { it.contains("Advert") || it.contains("Adx") || it.contains("FunAd") }) score += 20
        if (specText.contains(android.util.SparseArray::class.java.name)) score += 35
        if (specText.contains(PB_FRAGMENT_CLASS) && specText.contains(ArrayList::class.java.name)) score += 10
        score += specs.size * 8
        score -= targetMethods.size / 10
        score -= targetClass.simpleName.length / 4
        return score
    }

    private fun scanMethodSpecs(
        targetClass: Class<*>,
        targetMethods: List<Method>,
        cl: ClassLoader,
        logger: ScanLogger?,
        logExactMismatch: Boolean = true,
    ): List<String> {
        if (targetClass.name == PB_AD_INSERT_CLASS) {
            log(logger, "pbEarlyAdInsert: fixed class found: $PB_AD_INSERT_CLASS")
        }

        val specs = ArrayList<String>(4)
        specs.addAll(
            findMethodSpecs(
                targetMethods = targetMethods,
                cl = cl,
                logger = logger,
                label = "pbCommentAd",
                returnTypeName = "void",
                paramTypeNames = listOf(PB_DATA_CLASS, PB_FRAGMENT_CLASS, "boolean"),
                expectedCount = 2,
                logMismatch = logExactMismatch,
            ),
        )
        specs.addAll(
            findMethodSpecs(
                targetMethods = targetMethods,
                cl = cl,
                logger = logger,
                label = "pbCommentFunAd",
                returnTypeName = "void",
                paramTypeNames = listOf(
                    ArrayList::class.java.name,
                    "boolean",
                    ArrayList::class.java.name,
                    PB_FRAGMENT_CLASS,
                    PB_DATA_CLASS,
                ),
                expectedCount = 1,
                logMismatch = logExactMismatch,
            ),
        )
        specs.addAll(
            findMethodSpecs(
                targetMethods = targetMethods,
                cl = cl,
                logger = logger,
                label = "pbFunBannerAd",
                returnTypeName = PB_ADAPTER_DATA_CLASS,
                paramTypeNames = listOf(
                    ArrayList::class.java.name,
                    "int",
                    PB_DATA_CLASS,
                    THREAD_DATA_CLASS,
                    PB_FRAGMENT_CLASS,
                ),
                expectedCount = 1,
                logMismatch = logExactMismatch,
            ),
        )

        val exactSpecs = specs.distinct()
        if (exactSpecs.size >= 4) return exactSpecs
        return scanFlexibleMethodSpecs(targetMethods, cl)
    }

    private fun scanFlexibleMethodSpecs(targetMethods: List<Method>, cl: ClassLoader): List<String> {
        val pbFragmentClass = resolveType(PB_FRAGMENT_CLASS, cl) ?: return emptyList()
        val threadDataClass = resolveType(THREAD_DATA_CLASS, cl)
        val methods = targetMethods.filter { method ->
            Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)
        }
        if (methods.size < MIN_METHOD_COUNT) return emptyList()
        fun isListLike(type: Class<*>): Boolean =
            List::class.java.isAssignableFrom(type) ||
                java.util.Collection::class.java.isAssignableFrom(type)

        val dataTypes = LinkedHashSet<Class<*>>()
        for (method in methods) {
            for (type in method.parameterTypes) {
                if (type.isPrimitive ||
                    type == String::class.java ||
                    type == pbFragmentClass ||
                    type == threadDataClass ||
                    isListLike(type)) {
                    continue
                }
                dataTypes.add(type)
            }
        }

        var bestSpecs: List<String> = emptyList()
        var bestScore = 0
        var bestScoreCount = 0
        for (dataType in dataTypes) {
            val commentAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == dataType &&
                    method.parameterTypes[1] == pbFragmentClass &&
                    method.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }
            val commentFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 5 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == pbFragmentClass &&
                    method.parameterTypes[4] == dataType
            }
            val bannerAd = methods.filter { method ->
                method.parameterTypes.size == 5 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == dataType &&
                    (threadDataClass == null || threadDataClass.isAssignableFrom(method.parameterTypes[3])) &&
                    method.parameterTypes[4] == pbFragmentClass &&
                    !method.returnType.isPrimitive &&
                    method.returnType != Void.TYPE
            }
            if (commentAd.size != 2 || commentFunAd.size != 1 || bannerAd.size != 1) continue

            val placeholderFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 6 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[3]) &&
                    method.parameterTypes[4] == dataType &&
                    isListLike(method.parameterTypes[5])
            }

            val candidateSpecs = (
                commentAd + commentFunAd + bannerAd +
                    placeholderFunAd.takeIf { it.size == 1 }.orEmpty()
                )
                .sortedBy { it.name }
                .map(::encodeMethodSpec)
                .distinct()
            val score = candidateSpecs.size * 10
            if (candidateSpecs.size >= 4 && score > bestScore) {
                bestSpecs = candidateSpecs
                bestScore = score
                bestScoreCount = 1
            } else if (candidateSpecs.size >= 4 && score == bestScore) {
                bestScoreCount++
            }
        }
        if (bestSpecs.size >= 4) {
            return if (bestScoreCount == 1) bestSpecs else emptyList()
        }

        var newBestSpecs: List<String> = emptyList()
        var newBestScore = 0
        var newBestScoreCount = 0
        for (dataType in dataTypes) {
            val commentFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 5 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == pbFragmentClass &&
                    method.parameterTypes[4] == dataType
            }
            val sparseAdData = methods.filter { method ->
                android.util.SparseArray::class.java.isAssignableFrom(method.returnType) &&
                    method.parameterTypes.size == 3 &&
                    isListLike(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == dataType &&
                    method.parameterTypes[2] == pbFragmentClass
            }
            val commentAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == dataType &&
                    method.parameterTypes[1] == pbFragmentClass &&
                    method.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }
            val placeholderFunAd = methods.filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 6 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                    isListLike(method.parameterTypes[3]) &&
                    method.parameterTypes[4] == dataType &&
                    isListLike(method.parameterTypes[5])
            }
            if (commentFunAd.size != 1 || sparseAdData.size != 1) continue

            val directCommentAd = commentAd.takeIf { it.size in 1..2 }.orEmpty()
            val directPlaceholderFunAd = placeholderFunAd.takeIf { it.size == 1 }.orEmpty()
            val candidateSpecs = (
                directCommentAd + commentFunAd + sparseAdData + directPlaceholderFunAd
                )
                .sortedBy { it.name }
                .map(::encodeMethodSpec)
                .distinct()
            val score = candidateSpecs.size * 10 + 50 +
                (if (directCommentAd.isNotEmpty()) 20 else 0) +
                (if (directPlaceholderFunAd.isNotEmpty()) 15 else 0)
            if (candidateSpecs.size >= MIN_METHOD_COUNT && score > newBestScore) {
                newBestSpecs = candidateSpecs
                newBestScore = score
                newBestScoreCount = 1
            } else if (candidateSpecs.size >= MIN_METHOD_COUNT && score == newBestScore) {
                newBestScoreCount++
            }
        }
        return if (newBestSpecs.size >= MIN_METHOD_COUNT && newBestScoreCount == 1) {
            newBestSpecs
        } else {
            emptyList()
        }
    }

    private fun findMethodSpecs(
        targetMethods: List<Method>,
        cl: ClassLoader,
        logger: ScanLogger?,
        label: String,
        returnTypeName: String,
        paramTypeNames: List<String>,
        expectedCount: Int,
        logMismatch: Boolean = true,
    ): List<String> {
        val returnType = resolveType(returnTypeName, cl) ?: run {
            if (logMismatch) log(logger, "pbEarlyAdInsert[$label]: return type missing: $returnTypeName")
            return emptyList()
        }
        val paramTypes = paramTypeNames.map { typeName ->
            resolveType(typeName, cl) ?: run {
                if (logMismatch) log(logger, "pbEarlyAdInsert[$label]: param type missing: $typeName")
                return emptyList()
            }
        }
        val matches = targetMethods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                (method.returnType == returnType || returnType.isAssignableFrom(method.returnType)) &&
                method.parameterTypes.contentEquals(paramTypes.toTypedArray())
        }
        if (matches.size != expectedCount) {
            if (logMismatch) {
                val names = matches.joinToString(",") { it.name }
                log(logger, "pbEarlyAdInsert[$label]: expected=$expectedCount actual=${matches.size} methods=$names")
            }
            return emptyList()
        }
        return matches.sortedBy { it.name }.map(::encodeMethodSpec)
    }

    internal fun resolveType(typeName: String, cl: ClassLoader): Class<*>? {
        return when (typeName) {
            "void" -> Void.TYPE
            "boolean" -> Boolean::class.javaPrimitiveType
            "int" -> Int::class.javaPrimitiveType
            else -> safeFindClass(typeName, cl)
        }
    }

    private fun encodeMethodSpec(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.name}|${method.returnType.name}|$params"
    }

    private fun kotlinMetadataStrings(targetClass: Class<*>, logger: ScanLogger?): List<String> {
        val metadata = targetClass.getAnnotation(kotlin.Metadata::class.java) ?: return emptyList()
        return (
            readMetadataStringArray(targetClass, metadata, "d1", logger) +
                readMetadataStringArray(targetClass, metadata, "d2", logger) +
                readMetadataStringArray(targetClass, metadata, "data1", logger) +
                readMetadataStringArray(targetClass, metadata, "data2", logger)
            )
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun readMetadataStringArray(
        targetClass: Class<*>,
        metadata: Annotation,
        methodName: String,
        logger: ScanLogger?,
    ): List<String> {
        val value = scanSubStep("PbEarlyAdInsertHook.${targetClass.name}.Metadata.$methodName", logger, null) {
            metadata.javaClass.getMethod(methodName).invoke(metadata)
        } as? Array<*> ?: return emptyList()
        return value.mapNotNull { it as? String }
    }

    private fun describeMethodShape(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name.substringAfterLast('.') }
        val ret = method.returnType.name.substringAfterLast('.')
        val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
        return "$staticPrefix${method.name}($params):$ret"
    }

    private fun isLikelyObfuscatedShortName(name: String): Boolean {
        if (name.isEmpty() || name.length > 6) return false
        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9')) return false
        }
        return true
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        ScanReflection.safeFindClass(name, cl)

    private fun declaredMethods(
        label: String,
        clazz: Class<*>,
        logger: ScanLogger?,
    ): List<Method>? {
        return scanSubStep("PbEarlyAdBlockHook.$label.Methods", logger, null) {
            clazz.declaredMethods.toList()
        }
    }

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
