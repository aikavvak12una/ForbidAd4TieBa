package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HomeRefreshCarrier
import com.forbidad4tieba.hook.symbol.model.HomeRefreshField
import com.forbidad4tieba.hook.symbol.model.HomeRefreshFlowSymbols
import com.forbidad4tieba.hook.symbol.model.HomeRefreshMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject

/** One cache entry, with exhaustive roles and exact member signatures; no name-only restoration. */
internal object HomeRefreshFlowCodec {
    fun encode(targets: HomeRefreshFlowSymbols): String {
        HomeRefreshFlowValidation.validate(targets)
        return JSONObject().apply {
        put("v", 3)
        put("methods", JSONObject().apply {
            targets.methods.forEach { (role, methods) ->
                put(role.name, JSONArray().apply { methods.forEach { put(executable(it)) } })
            }
        })
        put("constructors", JSONObject().apply {
            targets.constructors.forEach { (role, constructors) ->
                put(role.name, JSONArray().apply { constructors.forEach { put(executable(it)) } })
            }
        })
        put("fields", JSONObject().apply {
            targets.fields.forEach { (role, field) ->
                put(role.name, JSONObject().put("owner", field.declaringClass.name)
                    .put("name", field.name).put("type", field.type.name)
                    .put("static", Modifier.isStatic(field.modifiers)))
            }
        })
        }.toString()
    }

    fun restore(value: String, cl: ClassLoader): HomeRefreshFlowSymbols {
        val root = JSONObject(value)
        require(root.getInt("v") == 3) { "Unsupported homepage refresh flow schema" }
        require(keys(root) == setOf("v", "methods", "constructors", "fields"))
        val methodJson = root.getJSONObject("methods")
        val constructorJson = root.getJSONObject("constructors")
        val fieldJson = root.getJSONObject("fields")
        require(keys(methodJson) == HomeRefreshMethod.entries.map { it.name }.toSet())
        require(keys(constructorJson) == HomeRefreshCarrier.entries.map { it.name }.toSet())
        require(keys(fieldJson) == HomeRefreshField.entries.map { it.name }.toSet())
        val methods = HomeRefreshMethod.entries.associateWith { role ->
            val array = methodJson.getJSONArray(role.name)
            require(array.length() > 0)
            val restored = (0 until array.length()).map { index ->
                val spec = array.getJSONObject(index)
                require(keys(spec) == setOf("owner", "name", "params", "return", "static")) { "Invalid method descriptor: $role" }
                val owner = type(spec.getString("owner"), cl)
                val method = owner.getDeclaredMethod(spec.getString("name"), *parameters(spec, cl))
                require(method.returnType == type(spec.getString("return"), cl))
                require(Modifier.isStatic(method.modifiers) == spec.getBoolean("static"))
                method.apply { isAccessible = true }
            }
            require(restored.distinct().size == restored.size)
            restored
        }
        val constructors = HomeRefreshCarrier.entries.associateWith { role ->
            val array = constructorJson.getJSONArray(role.name)
            require(array.length() > 0)
            val restored = (0 until array.length()).map { index ->
                val spec = array.getJSONObject(index)
                require(keys(spec) == setOf("owner", "params")) { "Invalid constructor descriptor: $role" }
                type(spec.getString("owner"), cl).getDeclaredConstructor(*parameters(spec, cl))
                    .apply { isAccessible = true }
            }
            require(restored.distinct().size == restored.size)
            restored
        }
        val fields = HomeRefreshField.entries.associateWith { role ->
            val spec = fieldJson.getJSONObject(role.name)
            require(keys(spec) == setOf("owner", "name", "type", "static")) { "Invalid field descriptor: $role" }
            val field = type(spec.getString("owner"), cl).getDeclaredField(spec.getString("name"))
            require(field.type == type(spec.getString("type"), cl))
            require(Modifier.isStatic(field.modifiers) == spec.getBoolean("static"))
            field.apply { isAccessible = true }
        }
        return HomeRefreshFlowSymbols(methods, constructors, fields).also(HomeRefreshFlowValidation::validate)
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

    private fun parameters(value: JSONObject, cl: ClassLoader): Array<Class<*>> {
        val array = value.getJSONArray("params")
        return (0 until array.length()).map { type(array.getString(it), cl) }.toTypedArray()
    }

    private fun type(name: String, cl: ClassLoader): Class<*> =
        if (name == "void") Void.TYPE else CacheTargetValidation.resolveCachedParameterClass(name, cl)
            ?: error("HomeRefresh class unavailable: $name")

    private fun keys(value: JSONObject): Set<String> = value.keys().asSequence().toSet()
}
