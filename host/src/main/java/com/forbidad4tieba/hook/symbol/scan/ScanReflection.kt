package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.contracts.MemberAccess
import android.view.View
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object ScanReflection {
    private const val TAG = "[HookSymbolResolver]"
    private const val PLAIN_URL_MESSAGE_DISPATCH_METHOD = "dispatchResponsedMessage"

    fun safeFindClass(name: String, cl: ClassLoader): Class<*>? {
        HookSymbolScanSession.get()?.let { return it.findClass(name, cl) }
        return safeFindClassUncached(name, cl)
    }

    fun safeFindClassUncached(name: String, cl: ClassLoader): Class<*>? {
        return try {
            MemberAccess.findClassOrNull(name, cl)
        } catch (_: Throwable) {
            null
        }
    }

    fun isAssignableTo(typeName: String, base: Class<*>, cl: ClassLoader): Boolean =
        safeFindClass(typeName, cl)?.let(base::isAssignableFrom) == true

    /** JVM nesting metadata, not a binary-name prefix, establishes lexical ownership. */
    fun isDeclaredWithin(type: Class<*>, owner: Class<*>): Boolean =
        generateSequence(type) { it.enclosingClass }.any { it == owner }

    /** Resolves a type from a cached descriptor; this does not select a candidate. */
    fun resolveType(typeName: String, cl: ClassLoader): Class<*>? = when (typeName) {
        "void" -> Void.TYPE
        "boolean" -> java.lang.Boolean.TYPE
        "byte" -> java.lang.Byte.TYPE
        "char" -> java.lang.Character.TYPE
        "short" -> java.lang.Short.TYPE
        "int" -> java.lang.Integer.TYPE
        "long" -> java.lang.Long.TYPE
        "float" -> java.lang.Float.TYPE
        "double" -> java.lang.Double.TYPE
        else -> safeFindClass(typeName, cl)
    }

    fun collectInstanceFields(clazz: Class<*>): List<Field> {
        HookSymbolScanSession.get()?.let { return it.collectInstanceFields(clazz) }
        return collectInstanceFieldsUncached(clazz)
    }

    fun collectInstanceFieldsUncached(clazz: Class<*>): List<Field> {
        val out = ArrayList<Field>(16)
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                out.add(field)
            }
            current = current.superclass
        }
        return out
    }

    fun collectInstanceMethods(clazz: Class<*>): List<Method> {
        HookSymbolScanSession.get()?.let { return it.collectInstanceMethods(clazz) }
        return collectInstanceMethodsUncached(clazz)
    }

    fun collectInstanceMethodsUncached(clazz: Class<*>): List<Method> {
        val out = ArrayList<Method>(24)
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                out.add(method)
            }
            current = current.superclass
        }
        return out
    }

    fun isListType(type: Class<*>): Boolean {
        return List::class.java.isAssignableFrom(type) || ArrayList::class.java.isAssignableFrom(type)
    }

    fun isBooleanType(type: Class<*>): Boolean {
        return type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType
    }

    fun isIntType(type: Class<*>): Boolean {
        return type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType
    }

    fun isAdapterLike(type: Class<*>): Boolean {
        val methods = collectInstanceMethods(type)
        val hasGetCount = methods.any {
            it.name == "getCount" &&
                it.parameterTypes.isEmpty() &&
                (it.returnType == Int::class.javaPrimitiveType || it.returnType == Int::class.java)
        }
        if (!hasGetCount) return false
        return methods.any {
            it.name == "notifyDataSetChanged" && it.parameterTypes.isEmpty() && it.returnType == Void.TYPE
        }
    }

    fun isFragmentLikeType(type: Class<*>): Boolean {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            if (current.name == "androidx.fragment.app.Fragment") return true
            current = current.superclass
        }
        return false
    }

    // These names come from a selected descriptor, never a sample-name preference.
    fun pickMethod(methods: List<Method>, resolvedName: String?): Method? =
        methods.singleOrNull { !resolvedName.isNullOrBlank() && it.name == resolvedName }

    fun pickFieldName(fields: List<Field>, resolvedName: String?): String? =
        fields.singleOrNull { !resolvedName.isNullOrBlank() && it.name == resolvedName }?.name

    fun isPlainUrlMessageDispatchMethod(method: Method, responsedMessageClass: Class<*>): Boolean {
        return method.name == PLAIN_URL_MESSAGE_DISPATCH_METHOD &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            responsedMessageClass.isAssignableFrom(method.parameterTypes[0])
    }

    fun isMountCardLinkLayoutOnClickMethod(method: Method, methodName: String): Boolean {
        return method.name == methodName &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == View::class.java
    }

    fun resolveMountCardLinkLayoutDataField(layoutClass: Class<*>, dataClass: Class<*>): Field? {
        val candidates = collectInstanceFields(layoutClass).filter { field ->
            !Modifier.isStatic(field.modifiers) && dataClass.isAssignableFrom(field.type)
        }
        return candidates.singleOrNull()
    }

    fun isMountCardLinkLayoutStructureValid(
        layoutClass: Class<*>,
        onClickMethod: Method,
        dataClass: Class<*>,
        dataField: Field,
        getUrlMethod: Method,
    ): Boolean {
        if (!View.OnClickListener::class.java.isAssignableFrom(layoutClass)) return false
        if (!View::class.java.isAssignableFrom(layoutClass)) return false
        if (!isMountCardLinkLayoutOnClickMethod(onClickMethod, onClickMethod.name)) return false
        if (Modifier.isStatic(dataField.modifiers) || !dataClass.isAssignableFrom(dataField.type)) return false
        if (Modifier.isStatic(getUrlMethod.modifiers)) return false
        return getUrlMethod.returnType == String::class.java && getUrlMethod.parameterTypes.isEmpty()
    }

    fun runRules(
        candidates: List<String>,
        cl: ClassLoader,
        rules: List<ScanRule>,
        logger: ScanLogger?,
        tag: String,
    ): ScanMatch? {
        for (rule in rules) {
            val matches = ArrayList<ScanMatch>()
            var skippedByReflection = 0
            var firstReflectionError: String? = null
            val ruleCandidates = rule.candidateNames(candidates, cl, logger).distinct()
            for (className in ruleCandidates) {
                try {
                    val cls = safeFindClass(className, cl) ?: continue
                    val match = rule.match(cls, cl, logger)
                    if (match != null) {
                        matches.add(match)
                    }
                } catch (t: Throwable) {
                    skippedByReflection++
                    if (firstReflectionError == null) {
                        firstReflectionError = HookSymbolScanDiagnostics.sanitizeScanStatusText(
                            HookSymbolScanDiagnostics.formatScanException(t),
                        )
                    }
                }
            }
            if (skippedByReflection > 0) {
                val line = "$tag scan rule ${rule.javaClass.simpleName} skipped classes by reflection=$skippedByReflection" +
                    (firstReflectionError?.let { ", firstException=$it" } ?: "")
                logDebugAndLogger(logger, line)
            }
            if (matches.isEmpty()) {
                if (skippedByReflection > 0) {
                    HookSymbolScanSession.get()?.scanErrors?.let { errors ->
                        HookSymbolScanDiagnostics.recordScanIssue(
                            logger = logger,
                            tag = tag,
                            errors = errors,
                            detail = "rule ${rule.javaClass.simpleName} no semantic match; " +
                                "reflectionFailures=$skippedByReflection" +
                                (firstReflectionError?.let { ", firstException=$it" } ?: ""),
                        )
                    }
                }
                HookSymbolScanDiagnostics.log(
                    logger,
                    "$tag scan rule ${rule.javaClass.simpleName} no semantic match among ${ruleCandidates.size} candidates",
                )
                continue
            }
            val match = chooseUniqueScanMatch(
                tag = tag,
                ruleName = rule.javaClass.simpleName,
                matches = matches,
                logger = logger,
                minScore = rule.minScore,
                minScoreGap = rule.minScoreGap,
            )
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun chooseUniqueScanMatch(
        tag: String,
        ruleName: String,
        matches: List<ScanMatch>,
        logger: ScanLogger?,
        minScore: Int,
        minScoreGap: Int,
    ): ScanMatch? {
        require(minScoreGap > 0)
        if (matches.isEmpty()) return null
        val sorted = matches.sortedWith(
            compareByDescending<ScanMatch> { it.score }
                .thenBy { it.className }
                .thenBy { it.methodName }
                .thenBy { it.fieldName },
        )
        val best = sorted.first()
        if (best.score < minScore) {
            HookSymbolScanDiagnostics.log(
                logger,
                "$tag scan rule $ruleName rejected: best score=${best.score} < minScore=$minScore, " +
                    "best=${describeScanMatch(best)}",
            )
            return null
        }

        val topScoreMatches = sorted.filter { it.score == best.score }
        if (topScoreMatches.size > 1) {
            val sample = describeScanMatches(topScoreMatches)
            HookSymbolScanDiagnostics.log(
                logger,
                "$tag scan rule $ruleName ambiguous top score=${best.score}, " +
                    "candidates=${topScoreMatches.size}: $sample",
            )
            return null
        }

        val second = sorted.getOrNull(1)
        if (second != null && minScoreGap > 0) {
            val gap = best.score.toLong() - second.score.toLong()
            if (gap < minScoreGap) {
                HookSymbolScanDiagnostics.log(
                    logger,
                    "$tag scan rule $ruleName ambiguous close score: best=${describeScanMatch(best)}, " +
                        "second=${describeScanMatch(second)}, gap=$gap < minGap=$minScoreGap",
                )
                return null
            }
        }

        HookSymbolScanDiagnostics.log(logger, "$tag matched by $ruleName: ${best.className}.${best.methodName} score=${best.score}")
        return best
    }

    private fun describeScanMatches(matches: List<ScanMatch>): String {
        return matches.take(5).joinToString("; ") { describeScanMatch(it) }
    }

    private fun describeScanMatch(match: ScanMatch): String {
        return "${match.className}.${match.methodName}/${match.fieldName}:${match.score}"
    }

    private fun logDebugAndLogger(logger: ScanLogger?, line: String) {
        try {
            Diagnostics.logD("$TAG: $line")
        } catch (t: Throwable) {
            Diagnostics.logD("HookSymbolResolver: ${t.message}")
        }
        try {
            logger?.log(line)
        } catch (t: Throwable) {
            Diagnostics.logD("HookSymbolResolver: ${t.message}")
        }
    }
}
