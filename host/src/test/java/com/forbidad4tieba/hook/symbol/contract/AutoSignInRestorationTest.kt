package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSignInRestorationTest {
    @Test fun nativeTargetsRemainUsableWithoutTheOptionalHybridProxy() {
        val symbols = fixture()
        val targets = AutoSignInContract.resolveAutoSignInNetworkSymbols(javaClass.classLoader!!, symbols)
        val client = targets.netCtor.newInstance("fixture") as Net
        targets.addPostDataMethod.invoke(client, "key", "value")
        targets.setNeedTbsMethod.invoke(client, true)
        targets.setNeedSigMethod.invoke(client, true)
        assertEquals("value", targets.postNetDataMethod.invoke(client))
        assertTrue(client.tbs && client.sig)
        assertEquals("fixture-account", targets.getCurrentAccountMethod.invoke(null))
        assertNull(targets.hybridNativeProxy)
        assertTrue(AutoSignInContract.isCacheValid(symbols, javaClass.classLoader!!))
    }

    @Test fun invalidConstructorOrAddressNeverPublishesAUsableBridge() {
        for (symbols in listOf(fixture(spec = "invalid"), fixture(address = "missing"))) {
            assertTrue(runCatching {
                AutoSignInContract.resolveAutoSignInNetworkSymbols(javaClass.classLoader!!, symbols)
            }.isFailure)
            assertTrue(!AutoSignInContract.isCacheValid(symbols, javaClass.classLoader!!))
        }
    }

    private fun fixture(spec: String = "java.lang.String", address: String = "address") = buildHookSymbols {
        this[AutoSignInContract.autoSignInNetworkClass] = Net::class.java.name
        this[AutoSignInContract.autoSignInNetworkConstructorSpec] = spec
        this[AutoSignInContract.autoSignInNetworkAddPostDataMethod] = "add"
        this[AutoSignInContract.autoSignInNetworkPostNetDataMethod] = "post"
        this[AutoSignInContract.autoSignInNetworkSetNeedTbsMethod] = "needTbs"
        this[AutoSignInContract.autoSignInNetworkSetNeedSigMethod] = "needSig"
        this[AutoSignInContract.autoSignInTbConfigClass] = Config::class.java.name
        this[AutoSignInContract.autoSignInServerAddressField] = address
        this[AutoSignInContract.autoSignInCoreApplicationClass] = Account::class.java.name
        this[AutoSignInContract.autoSignInCurrentAccountMethod] = "current"
    }

    private class Net(@Suppress("UNUSED_PARAMETER") url: String) {
        private val data = mutableMapOf<String, String>()
        var tbs = false
        var sig = false
        fun add(key: String, value: String) { data[key] = value }
        fun post(): String = data.getValue("key")
        fun needTbs(value: Boolean) { tbs = value }
        fun needSig(value: Boolean) { sig = value }
    }
    private object Config { @JvmField val address = "fixture" }
    private object Account { @JvmStatic fun current(): String = "fixture-account" }
}
