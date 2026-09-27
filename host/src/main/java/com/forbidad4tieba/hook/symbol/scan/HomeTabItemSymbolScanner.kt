package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.model.HomeTabItemScanSymbols
import com.forbidad4tieba.hook.symbol.model.HomeTabScanSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HomeTabItemSymbolScanner {
    fun scan(homeTab: HomeTabScanSymbols, cl: ClassLoader, logger: ScanLogger?): HomeTabItemScanSymbols {
        val homeClass = homeTab.tabClass?.let { ScanReflection.safeFindClass(it, cl) } ?: return HomeTabItemScanSymbols()
        val itemClass = resolveHomeTabItemClass(homeClass, homeTab.listField ?: return HomeTabItemScanSymbols())
            ?: return HomeTabItemScanSymbols()
        val factory = homeClass.declaredMethods.singleOrNull(::isItemFactory)
            ?.let { ScanDexQueries.method(it, logger) } ?: return HomeTabItemScanSymbols()
        val methods = ScanDexQueries.methods(itemClass.name, logger)
        val parser = uniqueSemanticCandidate("HomeTabItem.JsonParser", methods.filter {
            it.paramTypeNames == listOf("org.json.JSONObject") && it.returnTypeName == "void" &&
                it.usingStrings.containsAll(listOf("tab_type", "tab_name", "tab_code", "tab_url", "is_main_tab")) &&
                it.calls("org.json.JSONObject", "optString") && it.calls("org.json.JSONObject", "optInt")
        }, logger) ?: return HomeTabItemScanSymbols()
        val parsedFields = parser.usingFields.filter { it.usingType == FieldUsingType.Write }
            .map { it.field.descriptor }.toSet()

        fun selectField(label: String, fields: List<FieldData>, mustBeParsed: Boolean = true): FieldData? {
            val eligible = fields.filter { !mustBeParsed || it.descriptor in parsedFields }.distinctBy { it.descriptor }
            val field = uniqueSemanticCandidate("HomeTabItem.$label", eligible, logger) ?: return null
            val reflected = field.getFieldInstance(cl)
            return field.takeIf { reflected.declaringClass == itemClass && !Modifier.isStatic(reflected.modifiers) }
        }

        // The factory supplies type/name/code; the picture-tab decoder writes only the display name.
        val type = selectField("Type", factory.instanceFields(itemClass.name, "int", FieldUsingType.Write))
        val nameParser = uniqueSemanticCandidate("HomeTabItem.DisplayNameParser", methods.filter {
            Modifier.isStatic(it.modifiers) && it.returnTypeName == itemClass.name &&
                it.paramTypeNames == listOf("java.lang.String") &&
                it.usingStrings.containsAll(listOf("[pic-tab]", "tabName"))
        }, logger)
        val name = selectField("Name", nameParser?.instanceFields(itemClass.name, "java.lang.String", FieldUsingType.Write).orEmpty())
        val code = name?.let {
            selectField("Code", factory.instanceFields(itemClass.name, "java.lang.String", FieldUsingType.Write)
                .filter { field -> field.descriptor != it.descriptor })
        }

        // The URL composer reads the display name and URL, and passes them to the host URL API.
        val urlComposer = uniqueSemanticCandidate("HomeTabItem.UrlComposer", ScanDexQueries.methods(
            "com.baidu.tieba.homepage.framework.indicator.NewScrollFragmentAdapter", logger,
        ).filter {
            it.paramTypeNames == listOf(itemClass.name) && it.returnTypeName == "java.lang.String" &&
                it.calls("com.baidu.tbadk.core.util.UtilHelper", "urlAddParam") &&
                it.calls("com.baidu.adp.lib.util.BdStringHelper", "getUrlEncode")
        }, logger)
        val url = name?.let {
            selectField("Url", urlComposer?.instanceFields(itemClass.name, "java.lang.String", FieldUsingType.Read)
                .orEmpty().filter { field -> field.descriptor != it.descriptor })
        }
        val setter = uniqueSemanticCandidate("HomeTabItem.MainSetter", methods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.returnTypeName == "void" && method.paramTypeNames == listOf("boolean") &&
                factory.invokes.any { it.descriptor == method.descriptor }
        }, logger)
        val mainInt = selectField("MainInt", setter?.instanceFields(itemClass.name, "int", FieldUsingType.Write).orEmpty())
        val hash = methods.singleOrNull { it.methodName == "hashCode" && it.paramCount == 0 && it.returnTypeName == "int" }
            ?.takeIf { it.calls("java.util.Objects", "hash") }
        val mainBoolean = selectField("IdentityFlag", hash?.instanceFields(itemClass.name, "boolean", FieldUsingType.Read).orEmpty(), false)
        return HomeTabItemScanSymbols(
            typeField = type?.name,
            codeField = code?.name,
            nameField = name?.name,
            urlField = url?.name,
            mainSetterMethod = setter?.getMethodInstance(cl)?.name,
            mainIntField = mainInt?.name,
            mainBooleanField = mainBoolean?.name,
        )
    }

    internal fun isItemFactory(method: Method): Boolean =
        !Modifier.isStatic(method.modifiers) && !method.returnType.isPrimitive &&
            method.parameterTypes.contentEquals(arrayOf(
                Int::class.javaPrimitiveType, String::class.java, String::class.java, Boolean::class.javaPrimitiveType,
            ))

    internal fun resolveHomeTabItemClass(homeClass: Class<*>, listFieldName: String): Class<*>? {
        val listField = ScanReflection.collectInstanceFields(homeClass).singleOrNull {
            it.name == listFieldName && List::class.java.isAssignableFrom(it.type)
        } ?: return null
        val factory = homeClass.declaredMethods.singleOrNull(::isItemFactory) ?: return null
        val genericType = (listField.genericType as? java.lang.reflect.ParameterizedType)?.actualTypeArguments?.singleOrNull() as? Class<*>
        return factory.returnType.takeIf { genericType == null || genericType == it }
    }
}
