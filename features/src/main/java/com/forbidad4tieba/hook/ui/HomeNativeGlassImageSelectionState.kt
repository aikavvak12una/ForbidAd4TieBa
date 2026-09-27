package com.forbidad4tieba.hook.ui

internal class HomeNativeGlassImageSelectionState(
    var path: String,
    var tintColor: Int,
) {
    var paletteColors: List<Int> = emptyList()
    var defaultTintColor: Int? = null
}
