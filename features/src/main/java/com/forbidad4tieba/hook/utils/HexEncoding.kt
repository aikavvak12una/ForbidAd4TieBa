package com.forbidad4tieba.hook.utils

/** Stable digest encoding for persisted keys; no locale or per-byte Formatter allocations. */
internal fun ByteArray.toLowerHex(): String {
    val alphabet = "0123456789abcdef"
    val chars = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xFF
        chars[index * 2] = alphabet[value ushr 4]
        chars[index * 2 + 1] = alphabet[value and 0xF]
    }
    return chars.concatToString()
}
