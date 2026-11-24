package com.forbidad4tieba.hook.symbol.contract

import com.baidu.tbadk.core.dialog.yun.YunDialogManager
import com.baidu.tbadk.data.DialogStrategiesData
import com.forbidad4tieba.hook.symbol.model.CloudDialogScanSymbols
import com.forbidad4tieba.hook.symbol.model.CloudDialogSymbols
import com.forbidad4tieba.hook.symbol.model.HookFeatureKey
import com.forbidad4tieba.hook.symbol.model.buildHookSymbols
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudDialogContractTest {
    private val loader = javaClass.classLoader!!

    private fun group(): CloudDialogScanSymbols = CloudDialogScanSymbols(
        CloudDialogSymbols(
            YunDialogManager::class.java.getDeclaredMethod("choose", YunDialogManager.Caller::class.java,
                DialogStrategiesData::class.java, JSONObject::class.java),
            DialogStrategiesData::class.java.getDeclaredField("dialogName"),
            DialogStrategiesData::class.java.getDeclaredField("dialogUrl"),
            DialogStrategiesData::class.java.getDeclaredField("dialogStrategy"),
            DialogStrategiesData.StrategiesConfigData::class.java.getDeclaredMethod("readCategory"),
            YunDialogManager.Decision::class.java.getDeclaredConstructor(Boolean::class.javaPrimitiveType, String::class.java, String::class.java),
            YunDialogManager.Caller::class.java.getDeclaredMethod("moment"),
            YunDialogManager.Clock::class.java.getDeclaredMethod("readLabel"),
        ),
        YunDialogManager.Decision::class.java.getDeclaredMethod("allowed"),
    )

    @Test fun exactDescriptorsRestoreRenamedMembersWithoutConstructingHostData() {
        DialogStrategiesData.constructed = 0
        val original = group()
        val cached = buildHookSymbols { this[CloudDialogContract.targetsDescriptor] = CloudDialogCodec.encode(original) }
        val restoredCache = requireNotNull(com.forbidad4tieba.hook.symbol.model.HookSymbols.fromJson(cached.toJson()))
        val restored = requireNotNull(CloudDialogContract.resolveCloudDialogSymbols(loader, restoredCache))
        assertEquals(original.targets, restored)
        assertTrue(AdFamilyContract.aggregates.getValue(HookFeatureKey.BLOCK_AD)
            .contains(HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS))
        assertTrue(com.forbidad4tieba.hook.symbol.status.HookFeatureStatusDeriver.derive(cached)
            .getValue(HookFeatureKey.BLOCK_AD).isSupported())
        val refused = restored.resultConstructor.newInstance(false, "module_policy", "fixture")
        assertEquals(false, original.resultAllowedMethod.invoke(refused))
        assertEquals(0, DialogStrategiesData.constructed)
    }

    @Test fun aMissingGroupStaysNegativeButPartialAndOldGroupsCannotActivate() {
        val absent = buildHookSymbols {}
        assertTrue(CloudDialogContract.isCacheValid(absent, loader))
        assertFalse(CloudDialogContract.capabilities(absent).getValue(HookFeatureKey.BLOCK_AD_CLOUD_DIALOGS).isSupported())
        assertNull(CloudDialogContract.resolveCloudDialogSymbols(loader, absent))
        val encoded = CloudDialogCodec.encode(group())
        val partial = JSONObject(encoded).apply { getJSONObject("methods").remove("contextTime") }
        val previous = JSONObject(encoded).put("v", 0)
        for (invalid in listOf(partial, previous)) {
            val cached = buildHookSymbols { this[CloudDialogContract.targetsDescriptor] = invalid.toString() }
            assertFalse(CloudDialogContract.isCacheValid(cached, loader))
            assertNull(CloudDialogContract.resolveCloudDialogSymbols(loader, cached))
        }
    }

    @Test fun restoredConstructorAndFieldsCannotChangeTheirSemanticRoles() {
        val encoded = CloudDialogCodec.encode(group())
        val wrongConstructor = JSONObject(encoded).apply {
            getJSONObject("resultConstructor").put("params", JSONArray(listOf("java.lang.String", "boolean", "java.lang.String")))
        }
        assertThrows(IllegalArgumentException::class.java) { CloudDialogCodec.restore(wrongConstructor.toString(), loader) }
        val aliasedField = JSONObject(encoded).apply {
            getJSONObject("fields").getJSONObject("dialogUrl").put("name", "dialogName")
        }
        assertThrows(IllegalArgumentException::class.java) { CloudDialogCodec.restore(aliasedField.toString(), loader) }
    }
}
