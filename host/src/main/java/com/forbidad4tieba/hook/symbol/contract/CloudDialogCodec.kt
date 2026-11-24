package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.CloudDialogScanSymbols
import com.forbidad4tieba.hook.symbol.model.CloudDialogSymbols
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject

/** An atomic descriptor group with exact signatures and shared scan/restoration checks. */
internal object CloudDialogCodec {
    const val MANAGER = "com.baidu.tbadk.core.dialog.yun.YunDialogManager"
    const val DATA = "com.baidu.tbadk.data.DialogStrategiesData"
    const val STRATEGY = "com.baidu.tbadk.data.DialogStrategiesData\$StrategiesConfigData"
    private val methodKeys = setOf("admission", "strategyType", "resultAllowed", "contextTime", "timeName")
    private val fieldKeys = setOf("dialogName", "dialogUrl", "dialogStrategy")

    fun encode(group: CloudDialogScanSymbols): String {
        validate(group)
        val targets = group.targets
        return JSONObject().apply {
            put("v", 1)
            put("methods", JSONObject().apply {
                put("admission", executable(targets.admissionMethod))
                put("strategyType", executable(targets.strategyTypeMethod))
                put("resultAllowed", executable(group.resultAllowedMethod))
                put("contextTime", executable(targets.contextTimeMethod))
                put("timeName", executable(targets.timeNameMethod))
            })
            put("fields", JSONObject().apply {
                put("dialogName", field(targets.dialogNameField))
                put("dialogUrl", field(targets.dialogUrlField))
                put("dialogStrategy", field(targets.dialogStrategiesField))
            })
            put("resultConstructor", executable(targets.resultConstructor))
        }.toString()
    }

    fun restore(value: String, cl: ClassLoader): CloudDialogScanSymbols {
        val root = JSONObject(value)
        require(root.getInt("v") == 1 && keys(root) == setOf("v", "methods", "fields", "resultConstructor")) {
            "Unsupported cloud dialog descriptor group"
        }
        val methods = root.getJSONObject("methods")
        val fields = root.getJSONObject("fields")
        require(keys(methods) == methodKeys && keys(fields) == fieldKeys) { "Incomplete cloud dialog descriptors" }
        fun method(key: String): Method {
            val spec = methods.getJSONObject(key)
            require(keys(spec) == setOf("owner", "name", "params", "return", "static"))
            return type(spec.getString("owner"), cl).getDeclaredMethod(spec.getString("name"), *parameters(spec, cl)).apply {
                require(returnType == type(spec.getString("return"), cl))
                require(Modifier.isStatic(modifiers) == spec.getBoolean("static"))
                isAccessible = true
            }
        }
        fun field(key: String): Field {
            val spec = fields.getJSONObject(key)
            require(keys(spec) == setOf("owner", "name", "type", "static"))
            return type(spec.getString("owner"), cl).getDeclaredField(spec.getString("name")).apply {
                require(this.type == type(spec.getString("type"), cl))
                require(Modifier.isStatic(modifiers) == spec.getBoolean("static"))
                isAccessible = true
            }
        }
        val constructor = root.getJSONObject("resultConstructor")
        require(keys(constructor) == setOf("owner", "params"))
        val group = CloudDialogScanSymbols(
            CloudDialogSymbols(
                method("admission"), field("dialogName"), field("dialogUrl"), field("dialogStrategy"),
                method("strategyType"), type(constructor.getString("owner"), cl)
                    .getDeclaredConstructor(*parameters(constructor, cl)).apply { isAccessible = true },
                method("contextTime"), method("timeName"),
            ),
            method("resultAllowed"),
        )
        validate(group)
        return group
    }

    fun validate(group: CloudDialogScanSymbols) = with(group.targets) {
        fun instance(method: Method) {
            require(!Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
                !method.declaringClass.isInterface && !Modifier.isAbstract(method.declaringClass.modifiers)) {
                "Invalid cloud dialog instance method"
            }
        }
        listOf(admissionMethod, strategyTypeMethod, contextTimeMethod, timeNameMethod, group.resultAllowedMethod).forEach(::instance)
        require(admissionMethod.declaringClass.name == MANAGER && admissionMethod.parameterCount == 3 &&
            admissionMethod.parameterTypes[1].name == DATA && admissionMethod.parameterTypes[2] == JSONObject::class.java) {
            "Invalid cloud dialog admission signature"
        }
        fun dto(field: Field, name: String, type: Class<*>) {
            require(field.declaringClass.name == DATA && field.name == name && field.type == type && !Modifier.isStatic(field.modifiers)) {
                "Invalid cloud dialog DTO field: $name"
            }
        }
        dto(dialogNameField, "dialogName", String::class.java)
        dto(dialogUrlField, "dialogUrl", String::class.java)
        dto(dialogStrategiesField, "dialogStrategy", List::class.java)
        require(strategyTypeMethod.declaringClass.name == STRATEGY && strategyTypeMethod.parameterCount == 0 &&
            strategyTypeMethod.returnType == String::class.java) { "Invalid cloud dialog strategy reader" }
        require(resultConstructor.declaringClass == admissionMethod.returnType &&
            !Modifier.isAbstract(resultConstructor.declaringClass.modifiers) && !resultConstructor.declaringClass.isInterface &&
            resultConstructor.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType, String::class.java, String::class.java))) {
            "Invalid cloud dialog rejection constructor"
        }
        require(group.resultAllowedMethod.declaringClass == admissionMethod.returnType &&
            group.resultAllowedMethod.parameterCount == 0 && group.resultAllowedMethod.returnType == Boolean::class.javaPrimitiveType) {
            "Invalid cloud dialog result consumer"
        }
        require(contextTimeMethod.declaringClass == admissionMethod.parameterTypes[0] && contextTimeMethod.parameterCount == 0 &&
            contextTimeMethod.returnType == timeNameMethod.declaringClass && timeNameMethod.parameterCount == 0 &&
            timeNameMethod.returnType == String::class.java) { "Invalid cloud dialog caller time relationship" }
        require(contextTimeMethod.declaringClass.declaredConstructors.any { constructor ->
            constructor.parameterTypes.take(2) == listOf(android.content.Context::class.java, timeNameMethod.declaringClass) &&
                constructor.parameterTypes.contains(JSONObject::class.java)
        }) { "Cloud dialog caller does not own context/time/extra" }
        require(timeNameMethod.declaringClass.declaredConstructors.any { constructor ->
            constructor.parameterTypes.contentEquals(arrayOf(String::class.java, Boolean::class.javaPrimitiveType))
        }) { "Cloud dialog time is not the named page/event value" }
        require(listOf(admissionMethod, strategyTypeMethod, contextTimeMethod, timeNameMethod, group.resultAllowedMethod).distinct().size == 5) {
            "Aliased cloud dialog method roles"
        }
    }

    private fun executable(value: Executable): JSONObject = JSONObject().apply {
        put("owner", value.declaringClass.name)
        put("params", JSONArray(value.parameterTypes.map { it.name }))
        if (value is Method) {
            put("name", value.name)
            put("return", value.returnType.name)
            put("static", Modifier.isStatic(value.modifiers))
        }
    }

    private fun field(value: Field): JSONObject = JSONObject().put("owner", value.declaringClass.name)
        .put("name", value.name).put("type", value.type.name).put("static", Modifier.isStatic(value.modifiers))

    private fun parameters(value: JSONObject, cl: ClassLoader): Array<Class<*>> = value.getJSONArray("params").let { array ->
        (0 until array.length()).map { type(array.getString(it), cl) }.toTypedArray()
    }

    private fun type(name: String, cl: ClassLoader): Class<*> = if (name == "void") Void.TYPE else
        requireNotNull(CacheTargetValidation.resolveCachedParameterClass(name, cl)) { "Cloud dialog type unavailable: $name" }

    private fun keys(value: JSONObject): Set<String> = value.keys().asSequence().toSet()
}
