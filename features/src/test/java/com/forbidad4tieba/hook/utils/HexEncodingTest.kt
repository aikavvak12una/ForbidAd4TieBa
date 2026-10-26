package com.forbidad4tieba.hook.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import java.util.Locale

class HexEncodingTest {
    @Test fun preservesEveryByteIncludingLeadingZerosAndNegativeBytes() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals(bytes.joinToString("") { String.format(Locale.US, "%02x", it) }, bytes.toLowerHex())
        assertEquals("", byteArrayOf().toLowerHex())
        assertEquals("00017f80ff", byteArrayOf(0, 1, 127, -128, -1).toLowerHex())
    }

    @Test fun persistedShaAndDownloadMd5IdentifiersStayUnchanged() {
        for (algorithm in listOf("SHA-256", "MD5")) for (input in listOf("", "123456", "https://example.org/图片?x=1")) {
            val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
            assertEquals(bytes.joinToString("") { "%02x".format(it) }, bytes.toLowerHex())
        }
    }
}
