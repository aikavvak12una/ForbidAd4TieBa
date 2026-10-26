package com.forbidad4tieba.hook.feature.ui

/** Filtered rows and their original click positions are produced in one pass. */
internal object CollectionSearchIndex {
    data class Result(val items: List<Any>, val positions: IntArray)

    fun filter(items: List<Any>, tokens: List<String>, text: (Any) -> String): Result {
        val filtered = ArrayList<Any>(items.size)
        val positions = IntArray(items.size)
        items.forEachIndexed { index, item ->
            if (tokens.isNotEmpty()) {
                val searchable = text(item)
                if (!tokens.all { it in searchable }) return@forEachIndexed
            }
            positions[filtered.size] = index
            filtered.add(item)
        }
        return Result(filtered, if (filtered.size == positions.size) positions else positions.copyOf(filtered.size))
    }
}
