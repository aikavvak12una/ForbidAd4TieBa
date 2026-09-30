package com.forbidad4tieba.hook.symbol.contract

import android.app.Activity
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LaunchSplashContractTest {
    class LaunchActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) = Unit
    }
    class InheritedActivity : Activity()
    class WrongOwner { fun onCreate(savedInstanceState: Bundle?) = Unit }

    @Test fun validatesDeclaredActivityLifecycleWithoutConstructingTheHost() {
        val method = LaunchSplashContract.validate(LaunchActivity::class.java)
        assertEquals(LaunchActivity::class.java, method.declaringClass)
        assertEquals(Void.TYPE, method.returnType)
    }

    @Test fun missingOverrideAndUnrelatedOwnerFailClosed() {
        assertThrows(NoSuchMethodException::class.java) {
            LaunchSplashContract.validate(InheritedActivity::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            LaunchSplashContract.validate(WrongOwner::class.java)
        }
    }
}
