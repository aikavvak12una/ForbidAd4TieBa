package com.forbidad4tieba.hook.core

/** Installation-time ownership only; callbacks never access this map. */
internal class OwnedHookSet<K, H : Any>(private val release: (H) -> Unit) {
    private val handles = LinkedHashMap<K, H>()

    @Synchronized
    fun install(key: K, create: () -> H): H {
        handles[key]?.let { return it }
        // Do not reserve the key until installation succeeds: failures must remain retryable.
        return create().also { handles[key] = it }
    }

    @Synchronized
    fun contains(key: K): Boolean = key in handles

    @Synchronized
    fun size(): Int = handles.size

    @Synchronized
    fun rollback(): List<Pair<K, Throwable>> {
        val failures = ArrayList<Pair<K, Throwable>>()
        for ((key, handle) in handles.entries.toList().asReversed()) {
            try {
                release(handle)
                handles.remove(key)
            } catch (failure: Throwable) {
                // Retain failed handles so a retry cannot silently install duplicates.
                failures += key to failure
            }
        }
        return failures
    }
}
