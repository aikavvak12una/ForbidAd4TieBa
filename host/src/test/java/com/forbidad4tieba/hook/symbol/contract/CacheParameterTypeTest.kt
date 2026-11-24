package com.forbidad4tieba.hook.symbol.contract

import org.junit.Assert.*
import org.junit.Test

class CacheParameterTypeTest {
    private val loader = javaClass.classLoader!!
    private fun resolve(name: String) = CacheTargetValidation.resolveCachedParameterClass(name, loader)

    @Test fun theCurrentHostVoidArrayRestoresFromDexKitAndCachedJvmSpelling() {
        assertEquals(Array<Void>::class.java, resolve("java.lang.Void[]"))
        assertEquals(resolve("[Ljava.lang.Void;"), resolve("java.lang.Void[]"))
    }

    @Test fun primitiveAndNestedArraySignaturesRetainTheirExactTypes() {
        assertEquals(IntArray::class.java, resolve("int[]"))
        assertEquals(Array<IntArray>::class.java, resolve("int[][]"))
        assertEquals(Array<Array<String>>::class.java, resolve("java.lang.String[][]"))
        assertEquals(resolve("[[I"), resolve("int[][]"))
    }

    @Test fun scalarPrimitiveAndReferenceTypesKeepTheExistingCacheBehavior() {
        assertEquals(java.lang.Boolean.TYPE, resolve("boolean"))
        assertEquals(String::class.java, resolve("java.lang.String"))
        assertNull(resolve("missing.parameter.Component[]"))
    }
}
