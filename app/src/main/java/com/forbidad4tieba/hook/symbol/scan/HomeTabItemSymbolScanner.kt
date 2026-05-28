package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.*

import com.forbidad4tieba.hook.HookSymbolResolver

import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HomeTabItemSymbolScanner {
    fun scan(homeMatch: ScanMatch, cl: ClassLoader, logger: ScanLogger?): HomeTabItemScanSymbols {
        val homeClass = safeFindClass(homeMatch.className, cl)
        if (homeClass == null) {
            log(logger, "home item scan: home class not found ${homeMatch.className}")
            return HomeTabItemScanSymbols()
        }
        val itemClass = resolveHomeTabItemClass(homeClass, homeMatch.fieldName)
        if (itemClass == null) {
            log(logger, "home item scan: item class unresolved")
            return HomeTabItemScanSymbols()
        }
        val fields = collectInstanceFields(itemClass)
        val typeField = pickHomeTabIntFieldName(fields, preferredName = "a")

        val stringFields = fields.filter { it.type == String::class.java }
        val codeField = pickHomeTabStringFieldName(
            fields = stringFields,
            preferredNames = listOf("c"),
            containsKeyword = "code",
        )
        val nameField = pickHomeTabStringFieldName(
            fields = stringFields,
            preferredNames = listOf("b"),
            containsKeyword = "name",
            excludes = setOfNotNull(codeField),
        )
        val urlField = pickHomeTabStringFieldName(
            fields = stringFields,
            preferredNames = listOf("d"),
            containsKeyword = "url",
            excludes = setOfNotNull(codeField, nameField),
        )

        val mainSetterMethod = collectInstanceMethods(itemClass)
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1 &&
                    (method.parameterTypes[0] == Boolean::class.javaPrimitiveType ||
                        method.parameterTypes[0] == Boolean::class.java)
            }
            .minWithOrNull(
                compareBy<Method>(
                    { if (it.name == "k") 0 else 1 },
                    { if (it.name.contains("main", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val mainIntField = fields
            .filter { field ->
                (field.type == Int::class.javaPrimitiveType || field.type == Int::class.javaObjectType)
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == "i") 0 else 1 },
                    { if (it.name.contains("main", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val mainBooleanField = fields
            .filter { field ->
                (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java)
            }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name.contains("main", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )?.name

        val out = HomeTabItemScanSymbols(
            typeField = typeField,
            codeField = codeField,
            nameField = nameField,
            urlField = urlField,
            mainSetterMethod = mainSetterMethod,
            mainIntField = mainIntField,
            mainBooleanField = mainBooleanField,
        )
        log(
            logger,
            "home item scan: cls=${itemClass.name}, " +
                "fields={${out.typeField},${out.codeField},${out.nameField},${out.urlField}}, " +
                "main={${out.mainSetterMethod},${out.mainIntField},${out.mainBooleanField}}",
        )
        return out
    }

    internal fun resolveHomeTabItemClass(homeClass: Class<*>, listFieldName: String): Class<*>? {
        val factoryReturn = collectInstanceMethods(homeClass)
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType != Void.TYPE &&
                    !method.returnType.isPrimitive &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == String::class.java &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }
            .minWithOrNull(
                compareBy<Method>(
                    { if (it.name.length <= 2) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.returnType
        if (factoryReturn != null) return factoryReturn

        val listField = collectInstanceFields(homeClass).firstOrNull { it.name == listFieldName } ?: return null
        val generic = listField.genericType as? java.lang.reflect.ParameterizedType ?: return null
        val actual = generic.actualTypeArguments.firstOrNull() as? Class<*>
        return actual
    }

    private fun pickHomeTabIntFieldName(fields: List<java.lang.reflect.Field>, preferredName: String): String? {
        return fields
            .filter { it.type == Int::class.javaPrimitiveType || it.type == Int::class.javaObjectType }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { if (it.name == preferredName) 0 else 1 },
                    { if (it.name.contains("type", ignoreCase = true)) 0 else 1 },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.name
    }

    private fun pickHomeTabStringFieldName(
        fields: List<java.lang.reflect.Field>,
        preferredNames: List<String>,
        containsKeyword: String? = null,
        excludes: Set<String> = emptySet(),
    ): String? {
        return fields
            .filter { !excludes.contains(it.name) }
            .minWithOrNull(
                compareBy<java.lang.reflect.Field>(
                    { preferredNames.indexOf(it.name).let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } },
                    {
                        if (!containsKeyword.isNullOrBlank() && it.name.contains(containsKeyword, ignoreCase = true)) {
                            0
                        } else {
                            1
                        }
                    },
                    { it.name.length },
                    { it.name },
                ),
            )
            ?.name
    }

    private fun safeFindClass(name: String, cl: ClassLoader): Class<*>? =
        HookSymbolResolver.safeFindClass(name, cl)

    private fun collectInstanceFields(clazz: Class<*>): List<java.lang.reflect.Field> =
        HookSymbolResolver.collectInstanceFields(clazz)

    private fun collectInstanceMethods(clazz: Class<*>): List<Method> =
        HookSymbolResolver.collectInstanceMethods(clazz)

    private fun log(logger: ScanLogger?, line: String) {
        HookSymbolScanDiagnostics.log(logger, line)
    }
}
