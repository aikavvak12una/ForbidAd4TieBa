package com.forbidad4tieba.hook.symbol.scan

import android.content.Context
import com.forbidad4tieba.hook.diagnostic.HookSymbolScanDiagnostics
import com.forbidad4tieba.hook.symbol.model.HomeNativeGlassHostDarkModeSwitchSymbols
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** Resolves the manual night-mode switch, whose state follows the actual host skin. */
internal object HostDarkModeSwitchSymbolScanner {
    private const val TAG = "HomeNativeGlassHook.HostDarkModeSwitch"
    private const val ACTIVITY = "com.baidu.tieba.setting.more.MoreActivity"
    private const val SWITCH = "com.baidu.adp.widget.BdSwitchView.BdSwitchView"
    private const val STATE = "$SWITCH\$SwitchState"
    private const val VIEW = "android.view.View"
    private const val SKIN_MANAGER = "com.baidu.tbadk.core.util.SkinManager"
    private const val APPLICATION = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val FOLLOW_SYSTEM_KEY = "key_is_follow_system_mode"
    private const val INTERCEPTABLE = "com.baidu.titan.sdk.runtime.Interceptable"
    private const val INTERCEPT_RESULT = "com.baidu.titan.sdk.runtime.InterceptResult"

    // A leaf state forwarder may load constants/references and use the host's Titan dispatch.
    // Reject arithmetic, primitive field reads, extra calls and other sources of boolean values.
    private val FORWARDER_OPS = setOf(
        "sget-object", "const/4", "const/16", "const", "const/high16",
        "move-object", "move-object/from16", "move-object/16", "move-result-object",
        "invoke-virtual", "invoke-virtual/range", "invoke-interface", "invoke-interface/range",
        "if-eqz", "if-nez", "return-void", "nop",
    )

    fun scan(context: Context, cl: ClassLoader, logger: ScanLogger?): HomeNativeGlassHostDarkModeSwitchSymbols =
        scanSubStep(TAG, logger, HomeNativeGlassHostDarkModeSwitchSymbols()) {
            val info = context.applicationInfo
            val paths = listOfNotNull(info?.sourceDir) + info?.splitSourceDirs.orEmpty()
            HookSymbolScanSession.withDexKitBridge(paths, logger) {
                scan(it.bridge, cl, logger)
            } ?: missing(logger, "DexKit bridge unavailable")
        }

    private fun scan(bridge: DexKitBridge, cl: ClassLoader, logger: ScanLogger?): HomeNativeGlassHostDarkModeSwitchSymbols {
        val empty = HomeNativeGlassHostDarkModeSwitchSymbols()
        val switch = bridge.getClassData(SWITCH) ?: return missing(logger, "switch class missing")
        val activity = bridge.getClassData(ACTIVITY) ?: return missing(logger, "activity class missing")
        val methods = switch.methods
        val touch = unique("Touch", methods.filter {
            it.methodName == "onTouch" && instanceMethod(it, "boolean", VIEW, "android.view.MotionEvent")
        }, logger) { it.descriptor } ?: return empty
        val stateField = unique("StateField", touch.usingFields.filter {
            it.usingType.isWrite() && it.field.declaredClassName == SWITCH &&
                !Modifier.isStatic(it.field.modifiers) && it.field.typeName == STATE
        }.map { it.field }.distinctBy { it.descriptor }, logger) { it.descriptor } ?: return empty
        val listenerSetter = unique("ListenerSetter", methods.filter {
            it.methodName == "setOnSwitchStateChangeListener" && !Modifier.isStatic(it.modifiers) &&
                it.returnTypeName == "void" && it.paramCount == 1
        }, logger) { it.descriptor } ?: return empty
        val listenerClass = bridge.getClassData(listenerSetter.paramTypeNames.single())
            ?: return missing(logger, "listener interface missing")
        if (!Modifier.isInterface(listenerClass.modifiers)) return missing(logger, "listener is not an interface")
        val listener = unique("Listener", listenerClass.methods.filter {
            instanceMethod(it, "void", VIEW, STATE) && touch.calls(it)
        }, logger) { it.descriptor } ?: return empty
        val stateWriter = unique("StateWriter", methods.filter {
            instanceMethod(it, "void", STATE, "boolean") && it.calls(listener) &&
                it.reads(stateField) && it.usingFields.any { usage ->
                    usage.usingType.isWrite() && usage.field.descriptor == stateField.descriptor
                } && it.invokes.any { call ->
                    call.declaredClassName == VIEW && call.methodName == "invalidate" &&
                        instanceMethod(call, "void")
                }
        }, logger) { it.descriptor } ?: return empty

        fun methodsUsing(value: Int): Set<String> = bridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().declaredClass(SWITCH).addUsingNumber(value)),
        ).mapTo(HashSet()) { it.descriptor }
        val usesZero = methodsUsing(0)
        val usesOne = methodsUsing(1)
        fun forwarder(state: String): MethodData? = unique(state, methods.filter {
            it.descriptor in usesOne && it.descriptor !in usesZero &&
                isNotifyingForwarder(it, stateWriter, state)
        }, logger) { it.descriptor }
        val setOn = forwarder("ON") ?: return empty
        val setOff = forwarder("OFF") ?: return empty

        val activitySkinChange = unique("ActivitySkinChange", activity.methods.filter {
            it.methodName == "onChangeSkinType" && instanceMethod(it, "void", "int")
        }, logger) { it.descriptor } ?: return empty
        val switchClass = ScanReflection.safeFindClass(SWITCH, cl)
            ?: return missing(logger, "switch runtime class missing")
        val controllerFields = activitySkinChange.usingFields.filter {
            it.usingType.isRead() && it.field.declaredClassName == ACTIVITY && !Modifier.isStatic(it.field.modifiers)
        }.map { it.field }.distinctBy { it.descriptor }.filter { field ->
            activitySkinChange.invokes.any {
                it.declaredClassName == field.typeName && it.methodName == "onChangeSkinType" &&
                    instanceMethod(it, "void", "int")
            }
        }
        val controllerField = unique("ControllerField", controllerFields, logger) { it.descriptor } ?: return empty
        val controller = bridge.getClassData(controllerField.typeName)
            ?: return missing(logger, "controller class missing")
        val skinChange = unique("ControllerSkinChange", controller.methods.filter {
            it.methodName == "onChangeSkinType" && instanceMethod(it, "void", "int") && activitySkinChange.calls(it)
        }, logger) { it.descriptor } ?: return empty

        // Both settings controls have the same type. Only the manual control is synchronized
        // by onChangeSkinType; counting references to the follow-system preference selects the wrong one.
        val manualField = unique("ManualField", skinChange.usingFields.filter {
            it.usingType.isRead() && it.field.declaredClassName == controller.name &&
                !Modifier.isStatic(it.field.modifiers) && switchClass.isAssignableFrom(it.field.getTypeInstance(cl))
        }.map { it.field }.distinctBy { it.descriptor }, logger) { it.descriptor } ?: return empty
        val getter = unique("ManualGetter", controller.methods.filter {
            !Modifier.isStatic(it.modifiers) && it.paramCount == 0 &&
                it.returnTypeName == SWITCH && it.reads(manualField) &&
                it.usingFields.none { usage -> usage.usingType.isWrite() } &&
                it.usingFields.filter { usage -> usage.field.declaredClassName == controller.name }
                    .all { usage -> usage.field.descriptor == manualField.descriptor || isTitanField(usage.field) } &&
                it.invokes.all(::isTitanDispatch)
        }, logger) { it.descriptor } ?: return empty
        val callback = unique("Callback", activity.methods.filter { method ->
            method.methodName == listener.methodName && instanceMethod(method, "void", VIEW, STATE) &&
                method.reads(controllerField) && method.calls(getter) && FOLLOW_SYSTEM_KEY in method.usingStrings &&
                method.invokes.any {
                    it.declaredClassName == SKIN_MANAGER && it.methodName == "setDayOrDarkSkinTypeWithSystemMode" &&
                        it.paramTypeNames == listOf("boolean", "boolean")
                } && method.invokes.any { call ->
                    call.declaredClassName == controller.name && instanceMethod(call, "void", "int") &&
                        call.invokes.any {
                            it.declaredClassName == APPLICATION && it.methodName == "setSkinType" &&
                                instanceMethod(it, "void", "int")
                        }
                }
        }, logger) { it.descriptor } ?: return empty
        val listenerType = ScanReflection.safeFindClass(listenerClass.name, cl)
            ?: return missing(logger, "listener runtime class missing")
        val activityType = ScanReflection.safeFindClass(ACTIVITY, cl)
            ?: return missing(logger, "activity runtime class missing")
        if (!listenerType.isAssignableFrom(activityType) || !stateField.getTypeInstance(cl).isEnum) {
            return missing(logger, "listener implementation or state enum mismatch")
        }
        // Restore without constructing host objects; inaccessible or changed runtime signatures fail this substep.
        controllerField.getFieldInstance(cl)
        stateField.getFieldInstance(cl)
        listOf(getter, setOn, setOff, callback).forEach { it.getMethodInstance(cl) }
        HookSymbolScanDiagnostics.log(logger, "$TAG manual=${manualField.descriptor}, getter=${getter.descriptor}, " +
            "state=${stateField.descriptor}, on=${setOn.methodName}, off=${setOff.methodName}, callback=${callback.descriptor}")
        return HomeNativeGlassHostDarkModeSwitchSymbols(
            moreActivityClass = ACTIVITY,
            controllerField = controllerField.fieldName,
            switchGetterMethod = getter.methodName,
            switchStateField = stateField.fieldName,
            switchSetOnMethod = setOn.methodName,
            switchSetOffMethod = setOff.methodName,
            switchCallbackMethod = callback.methodName,
        )
    }

    private fun isNotifyingForwarder(method: MethodData, stateWriter: MethodData, state: String): Boolean {
        if (!instanceMethod(method, "void") || !method.calls(stateWriter)) return false
        val usages = method.usingFields
        val enumReads = usages.filter { it.field.declaredClassName == STATE }
        if (enumReads.size != 1 || enumReads.single().field.fieldName != state ||
            !Modifier.isStatic(enumReads.single().field.modifiers) || enumReads.single().field.typeName != STATE) return false
        if (usages.any { !it.usingType.isRead() || (it.field.declaredClassName != STATE && !isTitanField(it.field)) }) return false
        if (method.invokes.any { it.descriptor != stateWriter.descriptor && !isTitanDispatch(it) }) return false
        val ops = method.opNames
        return ops.all { it in FORWARDER_OPS } && ops.count { it.startsWith("invoke-virtual") } == 1
        // This no-argument leaf has no primitive input, field read, arithmetic or helper result.
        // Its boolean argument can only be a constant. Requiring 1 AND excluding 0 rejects silent
        // setters even if they also contain an unrelated 1; enum identity alone is insufficient.
    }

    private fun isTitanField(field: FieldData): Boolean =
        Modifier.isStatic(field.modifiers) && field.typeName == INTERCEPTABLE

    private fun isTitanDispatch(method: MethodData): Boolean =
        method.declaredClassName == INTERCEPTABLE && method.methodName == "invokeV" &&
            method.paramTypeNames == listOf("int", "java.lang.Object") && method.returnTypeName == INTERCEPT_RESULT

    private fun instanceMethod(method: MethodData, returns: String, vararg params: String): Boolean =
        !Modifier.isStatic(method.modifiers) && !method.isConstructor &&
            method.returnTypeName == returns && method.paramTypeNames == params.toList()

    private fun MethodData.calls(method: MethodData): Boolean = invokes.any { it.descriptor == method.descriptor }

    private fun MethodData.reads(field: FieldData): Boolean = usingFields.any {
        it.usingType.isRead() && it.field.descriptor == field.descriptor
    }

    private fun <T> unique(role: String, candidates: List<T>, logger: ScanLogger?, describe: (T) -> String): T? {
        if (candidates.size == 1) return candidates.single()
        missing(logger, "$role candidates=" + candidates.joinToString(",", transform = describe).ifEmpty { "-" })
        return null
    }

    private fun missing(logger: ScanLogger?, detail: String): HomeNativeGlassHostDarkModeSwitchSymbols {
        HookSymbolScanSession.get()?.scanErrors?.let {
            HookSymbolScanDiagnostics.recordScanIssue(logger, TAG, it, detail)
        } ?: HookSymbolScanDiagnostics.log(logger, "$TAG $detail")
        return HomeNativeGlassHostDarkModeSwitchSymbols()
    }
}
