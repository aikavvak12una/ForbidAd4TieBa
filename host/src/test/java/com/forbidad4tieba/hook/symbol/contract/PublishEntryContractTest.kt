package com.forbidad4tieba.hook.symbol.contract

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishEntryContractTest {
    @Test fun restoresBothPublicStaticReadersWithTheStringSignature() {
        val readers = PublishEntryContract.validate(Readers::class.java)
        assertEquals(listOf("isHit", "isHitOnce"), readers.map { it.name })
        assertTrue(readers.all { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) })
        assertTrue(readers.all { it.returnType == Boolean::class.javaPrimitiveType })
        assertTrue(readers.all { it.parameterTypes.contentEquals(arrayOf(String::class.java)) })
    }

    @Test fun missingReaderDoesNotFallBackToAnOverload() {
        assertThrows(NoSuchMethodException::class.java) {
            PublishEntryContract.validate(MissingReader::class.java)
        }
    }

    @Test fun instancePrivateAndNonBooleanReadersFailClosed() {
        for (owner in listOf(InstanceReaders::class.java, PrivateReader::class.java, WrongReturn::class.java)) {
            assertThrows(IllegalStateException::class.java) { PublishEntryContract.validate(owner) }
        }
    }

    class Readers {
        companion object {
            @JvmStatic fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: String?) = true
            @JvmStatic fun isHitOnce(key: Int) = true
        }
    }

    class MissingReader {
        companion object {
            @JvmStatic fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: Int) = true
        }
    }

    class InstanceReaders {
        fun isHit(key: String?) = true
        fun isHitOnce(key: String?) = true
    }

    class PrivateReader {
        companion object {
            @JvmStatic private fun isHit(key: String?) = true
            @JvmStatic fun isHitOnce(key: String?) = true
        }
    }

    class WrongReturn {
        companion object {
            @JvmStatic fun isHit(key: String?) = 1
            @JvmStatic fun isHitOnce(key: String?) = true
        }
    }
}
