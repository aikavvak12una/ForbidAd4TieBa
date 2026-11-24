package com.forbidad4tieba.hook.feature.ui

import org.junit.Assert.*
import org.junit.Test

class BannerRefreshGuardTest {
    @Test fun nestedBindAndSkinRefreshSquashOnlyAfterTheOuterCall() {
        val guard = BannerRefreshGuard()
        val target = Any()
        val events = ArrayList<String>()
        val result = guard.run(target, {
            guard.run(target, { events += "skin" }, { events += "inner squash" })
            events += "bind"
            7
        }, { events += "squash" })
        assertEquals(7, result)
        assertEquals(listOf("skin", "bind", "squash"), events)
    }

    @Test fun throwingOriginalDoesNotSquashOrPoisonTheNextRefresh() {
        val guard = BannerRefreshGuard()
        val target = Any()
        var squashes = 0
        assertThrows(IllegalStateException::class.java) {
            guard.run(target, { error("host failure") }, { squashes++ })
        }
        assertEquals(0, squashes)
        guard.run(target, { Unit }, { squashes++ })
        assertEquals(1, squashes)
    }

    @Test fun equalButDistinctInstancesAreNotCoalesced() {
        data class Target(val value: Int)
        val guard = BannerRefreshGuard()
        val first = Target(1)
        val second = Target(1)
        var squashes = 0
        guard.run(first, {
            guard.run(second, { Unit }, { squashes++ })
        }, { squashes++ })
        assertEquals(2, squashes)
    }
}
