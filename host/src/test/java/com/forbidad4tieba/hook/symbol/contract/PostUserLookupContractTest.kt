package com.forbidad4tieba.hook.symbol.contract

import org.junit.Assert.*
import org.junit.Test

class PostUserLookupContractTest {
    @Test fun lookupPreparesTheHostRequestWithoutPostingOrShowingHostToasts() {
        val targets = PostUserLookupContract.validate(Network::class.java)
        val call = targets.newCall("123")
        val request = Network.last!!
        assertEquals("https://tiebac.baidu.com/c/u/user/getUserByTiebaUid", request.address)
        assertEquals(mapOf("tieba_uid" to "123"), request.parameters)
        assertTrue(request.bduss)
        assertTrue(request.tbs)
        assertTrue(request.signature)
        assertFalse(request.toast)
        assertEquals(0, request.posts)
        assertEquals("reply", call.execute())
        assertEquals(1, request.posts)
        call.cancel()
        assertTrue(request.cancelled)
    }

    @Test fun structurallyInvalidHostApisAreRejectedBeforeAnyRequest() {
        assertThrows(IllegalStateException::class.java) {
            PostUserLookupContract.validate(WrongReturn::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            PostUserLookupContract.validate(StaticPost::class.java)
        }
    }

    open class BaseNetwork(val address: String) {
        val parameters = linkedMapOf<String, String>()
        var bduss = false
        var toast = true
        var tbs = false
        var signature = false
        var cancelled = false
        fun addPostData(key: String, value: String) { parameters[key] = value }
        fun setCancel() { cancelled = true }
        fun setNeedBdussForPost(value: Boolean) { bduss = value }
        fun setNeedShowSeverToast(value: Boolean) { toast = value }
        fun setNeedTbs(value: Boolean) { tbs = value }
        fun setNeedSig(value: Boolean) { signature = value }
    }
    class Network(address: String) : BaseNetwork(address) {
        var posts = 0
        init { last = this }
        fun postNetData(): String { posts++; return "reply" }
        companion object { var last: Network? = null }
    }
    class WrongReturn(address: String) : BaseNetwork(address) { fun postNetData(): Int = 0 }
    class StaticPost(address: String) : BaseNetwork(address) {
        companion object { @JvmStatic fun postNetData(): String = "reply" }
    }
}
