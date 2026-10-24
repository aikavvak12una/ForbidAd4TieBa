package com.forbidad4tieba.hook.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleResetProcessTest {
    @Test fun onlyTheOwnedImageWorkerCanBeStoppedByAReset() {
        fun selected(name: String, uid: Int = 10059, pid: Int = 2) =
            ModuleUserDataCleaner.isResetWorker(name, uid, 10059, pid, 1)

        assertTrue(selected("com.baidu.tieba:remote"))
        assertFalse(selected("com.baidu.tieba:remote", uid = 10060))
        assertFalse(selected("com.baidu.tieba:remote", pid = 1))
        assertFalse(selected("com.baidu.tieba:remote", pid = 0))
        assertFalse(selected("com.baidu.tieba"))
        assertFalse(selected("com.baidu.tieba:media"))
        assertFalse(selected("com.baidu.tieba.other:remote"))
        assertFalse(selected("com.android.systemui"))
    }
}
