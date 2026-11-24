package com.forbidad4tieba.hook.symbol.contract

import com.baidu.tieba.homepage.personalize.PersonalizePageView
import com.forbidad4tieba.hook.symbol.model.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeRefreshFlowCodecTest {
    private val loader = javaClass.classLoader!!
    private fun json() = JSONObject(HomeRefreshFlowCodec.encode(HomeRefreshFlowFixture.flow()))
    private fun spec(method: Method) = JSONObject().put("owner", method.declaringClass.name)
        .put("name", method.name).put("return", method.returnType.name)
        .put("static", Modifier.isStatic(method.modifiers)).put("params", JSONArray(method.parameterTypes.map { it.name }))
    private fun rejected(root: JSONObject) {
        assertThrows(Exception::class.java) { HomeRefreshFlowCodec.restore(root.toString(), loader) }
        assertFalse(AutoRefreshContract.isCacheValid(buildHookSymbols { this[AutoRefreshContract.autoRefreshFlow] = root.toString() }, loader))
    }

    @Test fun exactLifecycleAndCacheTargetsRoundTripWithoutExecutingHostCode() {
        val original = HomeRefreshFlowFixture.flow()
        val encoded = HomeRefreshFlowCodec.encode(original)
        assertEquals(original, HomeRefreshFlowCodec.restore(encoded, loader))
        val symbols = buildHookSymbols {
            this[AutoRefreshContract.autoRefreshFlow] = encoded
            this[AutoRefreshContract.autoRefreshCacheRestoreMethod] = "restore"
        }
        val targets = requireNotNull(AutoRefreshContract.resolveAutoRefreshSymbols(loader, symbols))
        assertEquals(original, targets.flow)
        assertEquals("restore", targets.cacheRestoreMethod.name)
    }

    @Test fun schemaAndEveryRoleCategoryAreRequired() {
        rejected(json().put("v", Int.MIN_VALUE))
        rejected(json().put("homeFragment", "retired.Home"))
        val roles = listOf("methods" to HomeRefreshMethod.APP_INITIALIZE.name,
            "constructors" to HomeRefreshCarrier.CACHE_TASK.name, "fields" to HomeRefreshField.NEW_FEED_CACHE_LISTENER.name)
        for ((category, role) in roles) {
            val missing = json()
            missing.getJSONObject(category).remove(role)
            rejected(missing)
        }
        val empty = json()
        empty.getJSONObject("methods").put(HomeRefreshMethod.APP_INITIALIZE.name, JSONArray())
        rejected(empty)
    }

    @Test fun startupOwnerAndDistinctPageRolesCannotBeReplacedByOtherValidMembers() {
        val wrongOwner = json()
        wrongOwner.getJSONObject("methods").put(HomeRefreshMethod.APP_INITIALIZE.name,
            JSONArray().put(spec(PersonalizePageView::class.java.getDeclaredMethod("initializeCache"))))
        rejected(wrongOwner)
        val aliased = json()
        aliased.getJSONObject("methods").put(HomeRefreshMethod.PAGE_RESUME.name,
            aliased.getJSONObject("methods").getJSONArray(HomeRefreshMethod.CACHE_INITIALIZE.name))
        rejected(aliased)
    }

    @Test fun newCacheRequiresItsInterfaceListenerAndDistinctLoadAndPostOwners() {
        val flow = HomeRefreshFlowFixture.flow()
        val load = flow.methods.getValue(HomeRefreshMethod.CACHE_LOAD).single {
            it.declaringClass == flow.field(HomeRefreshField.NEW_FEED_CACHE).type
        }
        assertTrue(load.parameterTypes[0].isInterface)
        assertTrue(load.parameterTypes[0].isAssignableFrom(flow.field(HomeRefreshField.NEW_FEED_CACHE_LISTENER).type))
        val listener = json()
        listener.getJSONObject("fields").getJSONObject(HomeRefreshField.NEW_FEED_CACHE_LISTENER.name)
            .put("name", "cache").put("type", HomeRefreshFlowFixture.Cache::class.java.name)
        rejected(listener)
        for (role in listOf(HomeRefreshMethod.CACHE_LOAD, HomeRefreshMethod.CACHE_POST)) {
            val duplicate = json()
            val first = duplicate.getJSONObject("methods").getJSONArray(role.name).getJSONObject(0)
            duplicate.getJSONObject("methods").put(role.name, JSONArray().put(first).put(first))
            rejected(duplicate)
        }
    }

    @Test fun cacheAndIdleContinuationsRetainTheExactBootstrapTaskLinks() {
        val flow = HomeRefreshFlowFixture.flow()
        val task = flow.constructors.getValue(HomeRefreshCarrier.CACHE_TASK).single().declaringClass
        for (role in listOf(HomeRefreshField.CACHE_CONTINUATION_TASK, HomeRefreshField.IDLE_CONTINUATION_TASK)) {
            assertEquals(task, flow.field(role).type)
            val wrong = json()
            wrong.getJSONObject("fields").getJSONObject(role.name).put("owner", HomeRefreshFlowFixture.Other::class.java.name)
            rejected(wrong)
        }
        for ((role, owner) in listOf(HomeRefreshCarrier.CACHE_TASK to HomeRefreshFlowFixture.OtherTask::class.java,
            HomeRefreshCarrier.CACHE_CALLBACK to HomeRefreshFlowFixture.OtherCallback::class.java)) {
            val wrong = json()
            wrong.getJSONObject("constructors").getJSONArray(role.name).getJSONObject(0).put("owner", owner.name)
            rejected(wrong)
        }
    }

    @Test fun invalidScannedMembersCannotBeEncodedAsAUsableGraph() {
        val original = HomeRefreshFlowFixture.flow()
        val invalid = original.copy(methods = original.methods + (HomeRefreshMethod.NEW_REFRESH to
            listOf(HomeRefreshFlowFixture.method(HomeRefreshFlowFixture.Other::class.java, "refresh", java.lang.Boolean.TYPE))))
        assertThrows(IllegalArgumentException::class.java) { HomeRefreshFlowCodec.encode(invalid) }
    }
}
