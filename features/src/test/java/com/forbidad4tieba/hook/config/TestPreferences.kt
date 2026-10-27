package com.forbidad4tieba.hook.config

import android.content.SharedPreferences
import java.lang.reflect.Proxy

/** A strict, test-only store: each caller declares the operations its subject may use. */
internal class TestPreferences<V>(
    val values: Map<String, V>,
    private val readMethods: Set<String> = emptySet(),
    private val editorMethods: Set<String> = emptySet(),
    private val typedDefaults: Boolean = false,
    private val onApply: () -> Unit = {},
) {
    var edits = 0
        private set
    var applies = 0
        private set
    var commits = 0
        private set
    val writes: Int get() = applies + commits
    private val pending = linkedMapOf<String, V?>()
    private var clear = false

    private fun publish() {
        val writable = values as MutableMap<String, V>
        if (clear) writable.clear()
        pending.forEach { (key, value) -> if (value == null) writable.remove(key) else writable[key] = value }
        pending.clear()
        clear = false
    }

    private val editor by lazy {
        Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            check(method.name in editorMethods) { "Unexpected editor call: ${method.name}" }
            when (method.name) {
                "putBoolean", "putInt", "putString" -> {
                    @Suppress("UNCHECKED_CAST")
                    pending[args!![0] as String] = args[1] as V?
                    proxy
                }
                "remove" -> { pending[args!![0] as String] = null; proxy }
                "clear" -> { clear = true; proxy }
                "apply" -> { publish(); applies++; onApply(); null }
                "commit" -> { publish(); commits++; true }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
    }

    val prefs: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when (method.name) {
            "contains" -> values.containsKey(args!![0])
            "edit" -> { check(editorMethods.isNotEmpty()) { "Read-only test preferences" }; edits++; editor }
            else -> {
                check(method.name in readMethods) { "Unexpected preferences call: ${method.name}" }
                when (method.name) {
                    "getAll" -> values
                    "getBoolean", "getInt", "getString" -> {
                        val value = values[args!![0]]
                        val typed = when (method.name) {
                            "getBoolean" -> value as? Boolean
                            "getInt" -> value as? Int
                            else -> value as? String
                        }
                        (if (typedDefaults) typed else value) ?: args[1]
                    }
                    "registerOnSharedPreferenceChangeListener", "unregisterOnSharedPreferenceChangeListener" -> null
                    else -> error("Unexpected preferences call: ${method.name}")
                }
            }
        }
    } as SharedPreferences
}
