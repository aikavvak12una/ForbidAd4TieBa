package com.forbidad4tieba.hook.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A lexical regression gate for known name shortcuts, complemented by semantic selection tests. */
class SymbolDiscoverySourceTest {
    @Test fun productionDiscoveryDoesNotUseSampleNamesOrDexFile() {
        val root = File(checkNotNull(System.getProperty("project.root")))
        val sources = listOf("contracts", "host", "features", "runtime", "app").flatMap { module ->
            File(root, module + "/src/main").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("Production source roots must be present", sources.size > 100)
        val textByPath = sources.associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
        assertEquals(emptyList<String>(), SymbolDiscoverySourceGuard.violations(textByPath))
    }

    @Test fun rejectsLiteralFiltersScoresOrderingAndReflectionCalls() {
        listOf(
            """if (fields.any { it.name == "b" }) score += 4""",
            """methods.singleOrNull { it.name == "c" }""",
            """methods.firstOrNull { "w" == it.name }""",
            """compareBy<Method>({ if (it.name == "k") 0 else 1 })""",
            """methods.filter { it.name.equals("s") }""",
            """methodName("s")""",
            """owner.getDeclaredMethod("F", Boolean::class.java)""",
            """pickField(fields, preferredName = "a")""",
        ).forEach { assertTrue(it, violations(it).isNotEmpty()) }
    }

    @Test fun rejectsConstantsEvenAcrossFilesAndAliases() {
        assertTrue(SymbolDiscoverySourceGuard.violations(mapOf(
            "Names.kt" to """const val LEGACY = "s" """,
            "Scan.kt" to """val target = Names.LEGACY
                methods.singleOrNull { it.name == target }""",
        )).isNotEmpty())
        assertTrue(violations("""private const val ENABLE_METHOD = "s" """).isNotEmpty())
    }

    @Test fun rejectsOpaqueClassesLengthBiasAndGuessedSuffixes() {
        listOf(
            """val owner = "com.baidu.tieba.np6" """,
            """val task = "com.baidu.tieba.NativeProxy\${'$'}a" """,
            """compareBy<Method> { it.name.length }""",
            """for (suffix in 'a'..'z') probe(suffix)""",
            """import dalvik.system.DexFile as Inventory""",
        ).forEach { assertTrue(it, violations(it).isNotEmpty()) }
    }

    @Test fun rejectsDiscoveryByTypeSpellingAndPartialDexNames() {
        listOf(
            """fields.filter { it.field.typeName.endsWith("Layout") }""",
            """if (field.type.name.endsWith("TextView")) score += 42""",
            """names.filter { name -> name.endsWith("View") }""",
            """if (clazz.name.contains("FloatingBar")) score += 60""",
            """methods.filter { !it.name.startsWith("on") }""",
            """field.type.simpleName == "DynamicIconData" """,
            """val alias = field.type.name
                alias.endsWith("TextView")""",
            """val alias = method.name
                compareBy { alias.length }""",
            """ClassMatcher.create().className("AiEmoji", StringMatchType.Contains)""",
            """MethodMatcher.create().name(PART, StringMatchType.StartsWith)""",
            """Regex(".*View").matches(clazz.name)""",
        ).forEach { assertTrue(it, violations(it).isNotEmpty()) }
    }

    @Test fun permitsPackageScopesTypeIdentityAndNonDiscoverySuffixes() {
        assertEquals(emptyList<String>(), violations("""
            val host = clazz.name.startsWith("com.baidu.tieba.")
            val type = field.type == Class.forName(STABLE_TYPE, false, cl)
            val views = fields.filter { View::class.java.isAssignableFrom(it.type) }
            val nested = clazz.enclosingClass == owner
            val anchor = usingStrings.any { it.endsWith("ready") }
            val typeName = context.resources.getResourceTypeName(resourceId)
            if (typeName == "id") acceptResource()
            method.matches(owner, name, returns, *params)
        """))
        assertEquals(emptyList<String>(), SymbolDiscoverySourceGuard.violations(mapOf(
            "features/src/main/java/example/ImageCache.kt" to """file.name.endsWith(".tmp")""",
        )))
    }

    @Test fun permitsDescriptorsPublicApisSemanticStringsAndDiagnosticSorting() {
        val source = """
            // methods.firstOrNull { it.name == "w" }
            /* val owner = "com.baidu.tieba.np6" */
            val method = methods.singleOrNull { it.name == cached.methodName }
            val restored = owner.getDeclaredMethod(resolvedName, *params)
            val keys = listOf("pb", "id", "b")
            val callback = methods.singleOrNull { it.name == "onScroll" }
            val metadata = kotlin.Metadata::class.java.getMethod("d2")
            val resources = "com.baidu.tieba.R\${'$'}id"
            val diagnosticTag = "com.baidu.tieba.R.drawable.${'$'}name"
            val diagnostics = candidates.sortedBy { it.name }.joinToString()
        """.trimIndent()
        assertEquals(emptyList<String>(), violations(source))
    }

    private fun violations(source: String) = SymbolDiscoverySourceGuard.violations(mapOf(
        "host/src/main/java/example/symbol/scan/Fixture.kt" to source,
    ))
}

internal object SymbolDiscoverySourceGuard {
    private data class Token(val text: String, val offset: Int)
    private val lexer = Regex(
        "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|" +
            "//[^\\n]*|/\\*[\\s\\S]*?\\*/|[A-Za-z_][A-Za-z0-9_]*|==|!=|\\.\\.|\\S",
    )
    private val names = setOf("name", "simpleName", "methodName", "fieldName", "className", "typeName", "declaredClassName")
    private val reflectionCalls = setOf("getMethod", "getDeclaredMethod", "getField", "getDeclaredField")
    private val shortName = Regex("[A-Za-z][A-Za-z0-9]?")
    // Kotlin's published Metadata ABI, not host obfuscation.
    private val metadataMembers = setOf("d1", "d2")

    fun violations(sources: Map<String, String>): List<String> {
        val tokens = sources.mapValues { (_, source) ->
            lexer.findAll(source).filterNot { it.value.startsWith("//") || it.value.startsWith("/*") }
                .map { Token(it.value, it.range.first) }.toList()
        }
        val globals = mutableMapOf<String, String>()
        // Only constants cross files. Ordinary local names must not contaminate other files.
        while (tokens.values.map { expandAliases(it, globals, onlyConstants = true) }.any { it }) { /* fixed point */ }

        return tokens.flatMap { (path, file) ->
            val constants = globals.toMutableMap()
            while (expandAliases(file, constants, onlyConstants = false)) { /* fixed point */ }
            val nameAliases = nameAliases(file)
            val discovery = path.startsWith("host/src/main/") &&
                ("/symbol/scan/" in path || "/symbol/dexkit/" in path)
            val failures = linkedSetOf<String>()
            fun report(index: Int, reason: String) {
                val line = sources.getValue(path).take(file[index].offset).count { it == '\n' } + 1
                failures.add(path + ":" + line + ": " + reason)
            }
            fun memberName(index: Int): Boolean = file.getOrNull(index)?.text in names &&
                (file[index].text != "typeName" || file.getOrNull(index - 1)?.text == ".")
            file.forEachIndexed { index, token ->
                val next = file.getOrNull(index + 1)?.text
                val next2 = file.getOrNull(index + 2)?.text
                val literal = literal(token.text)
                if (literal != null && opaqueHostClass(literal)) report(index, "sample host class: " + literal)
                if (token.text == "DexFile") report(index, "DexFile inventory bypasses DexKit")
                if (token.text == "preferredName" || token.text == "preferredNames") report(index, "member name preference")
                if (token.text in nameAliases && next == "." && next2 == "length") report(index, "member name length bias")
                if (token.text == "'a'" && next == ".." && next2 == "'z'") report(index, "guessed inner-class suffixes")

                if (discovery && token.text in nameAliases && next == ".") {
                    if (next2 in setOf("endsWith", "contains", "matches")) report(index, "target name shape predicate")
                    if (next2 == "startsWith") {
                        val prefix = value(file, index + 4, constants)
                        // Package boundaries and generated Android resources are stable namespaces.
                        val packagePrefix = prefix?.matches(Regex("(?:[a-z_][a-z0-9_]*\\.)+")) == true
                        if (!packagePrefix && prefix != "R$") report(index, "target name prefix predicate")
                    }
                }
                if (discovery && token.text == "simpleName" && next in setOf("==", "!=", ".")) {
                    report(index, "simple type name does not establish type identity")
                }
                if (discovery && token.text in names && next == "(") {
                    val arguments = file.drop(index + 2).takeWhile { it.text != ")" }.map { it.text }
                    if ("StringMatchType" in arguments && arguments.any { it in setOf("Contains", "StartsWith", "EndsWith", "SimilarRegex") }) {
                        report(index, "partial DexKit target name matcher")
                    }
                }
                if (discovery && token.text == "matches" && next == "(") {
                    val arguments = file.drop(index + 2).takeWhile { it.text != ")" }
                    if (arguments.none { it.text == "," || it.text == ":" } && arguments.any { it.text in nameAliases }) {
                        report(index, "target name regex predicate")
                    }
                }

                if (memberName(index)) {
                    val operand = when {
                        next in setOf("==", "!=", "=", "(") -> index + 2
                        next == "." && next2 == "equals" && file.getOrNull(index + 3)?.text == "(" -> index + 4
                        else -> null
                    }
                    if (operand != null && opaqueMember(value(file, operand, constants))) report(index, "sample member name")
                }
                if (token.text in setOf("==", "!=") && opaqueMember(value(file, index - 1, constants))) {
                    var end = index + 1
                    while (file.getOrNull(end + 1)?.text == ".") end += 2
                    if (memberName(end)) report(index, "sample member name")
                }
                if (token.text in reflectionCalls && next == "(" && opaqueMember(value(file, index + 2, constants))) {
                    report(index, "sample member reflection lookup")
                }
                if (token.text == "val") {
                    val name = file.getOrNull(index + 1)?.text.orEmpty()
                    if (Regex(".*(?:_METHOD|_FIELD|_CLASS|_NAME)$").matches(name) && opaqueMember(constants[name])) {
                        report(index, "sample member constant: " + name)
                    }
                }
            }
            failures
        }
    }

    private fun opaqueMember(value: String?): Boolean =
        value != null && shortName.matches(value) && value !in metadataMembers

    private fun nameAliases(file: List<Token>): Set<String> {
        val aliases = names.toMutableSet()
        do {
            var changed = false
            file.indices.filter { file[it].text == "val" }.forEach { index ->
                if (file.getOrNull(index + 2)?.text != "=") return@forEach
                var last = index + 3
                while (file.getOrNull(last + 1)?.text == "." && file.getOrNull(last + 3)?.text != "(") last += 2
                if (file.getOrNull(last)?.text in aliases && file.getOrNull(last + 1)?.text !in setOf("(", ".")) {
                    changed = aliases.add(file[index + 1].text) || changed
                }
            }
        } while (changed)
        return aliases
    }

    private fun expandAliases(file: List<Token>, constants: MutableMap<String, String>, onlyConstants: Boolean): Boolean {
        var changed = false
        file.indices.filter { file[it].text == "val" }.forEach { index ->
            if (onlyConstants && file.getOrNull(index - 1)?.text != "const") return@forEach
            val name = file.getOrNull(index + 1)?.text ?: return@forEach
            var equals = index + 2
            if (file.getOrNull(equals)?.text == ":" && file.getOrNull(equals + 1)?.text == "String") equals += 2
            if (file.getOrNull(equals)?.text != "=" || name in constants) return@forEach
            value(file, equals + 1, constants)?.let { constants[name] = it; changed = true }
        }
        return changed
    }

    private fun value(tokens: List<Token>, start: Int, constants: Map<String, String>): String? {
        val first = tokens.getOrNull(start)?.text ?: return null
        literal(first)?.let { return it }
        var last = start
        while (tokens.getOrNull(last + 1)?.text == ".") last += 2
        return tokens.getOrNull(last)?.text?.let(constants::get)
    }

    private fun literal(text: String): String? {
        if (!text.startsWith("\"")) return null
        val width = if (text.startsWith("\"\"\"")) 3 else 1
        val body = text.substring(width, text.length - width)
        if (Regex("""(?<!\\)\$[A-Za-z_{]""").containsMatchIn(body)) return null
        return body.replace("\\$", "$")
    }

    private fun opaqueHostClass(value: String): Boolean {
        if (listOf("com.baidu.tieba.", "com.baidu.tbadk.", "com.baidu.adp.widget.ListView.").none(value::startsWith)) return false
        val simple = value.substringAfterLast('.')
        if (simple == "R" || simple.startsWith("R$")) return false
        return Regex("[a-z][a-z0-9]{0,5}").matches(simple.substringBefore('$')) ||
            ('$' in simple && Regex("[a-z][a-z0-9]{0,4}").matches(simple.substringAfterLast('$')))
    }
}
