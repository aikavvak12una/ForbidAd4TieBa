package com.forbidad4tieba.hook.feature.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainUrlDirectBrowserHookTest {
    @Test
    fun keepsTiebaPrivateWebViewRoutesInsideTheHost() {
        assertTrue(
            PlainUrlDirectBrowserHook.isTiebaPrivateWebViewRoute(
                "com.baidu.tieba://tbwebview?url=https%3A%2F%2Ftieba.baidu.com%2Fmo%2Fq%2Fhybrid-main-usercenter%2FuserCenter",
            ),
        )
        assertTrue(
            PlainUrlDirectBrowserHook.isTiebaPrivateWebViewRoute(
                "COM.BAIDU.TIEBA://TBWEBVIEW?url=https%3A%2F%2Fexample.com",
            ),
        )
        assertNull(
            PlainUrlDirectBrowserHook.normalizeWebUrlForTest(
                "com.baidu.tieba://tbwebview?url=http%3A%2F%2Fcom.baidu.tieba%2Fuser%2F1163779723",
            ),
        )
    }

    @Test
    fun recognizesTiebaAppRoutesButNotOrdinaryWebUrls() {
        assertFalse(
            PlainUrlDirectBrowserHook.isTiebaPrivateWebViewRoute("https://example.com/article"),
        )
        assertTrue(PlainUrlDirectBrowserHook.isTiebaPrivateRoute("tiebaapp://device/getDeviceInfo"))
        assertFalse(PlainUrlDirectBrowserHook.isTiebaPrivateRoute("http://com.baidu.tieba.example/path"))
    }
}
