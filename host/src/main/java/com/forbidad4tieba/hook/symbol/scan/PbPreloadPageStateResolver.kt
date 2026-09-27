package com.forbidad4tieba.hook.symbol.scan

import android.app.Application
import com.forbidad4tieba.hook.symbol.model.PbPreloadPageStateTargets
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

/** Resolve both ends of the page-render state stream, without depending on generated lambda names. */
internal object PbPreloadPageStateResolver {
    const val VIEW_MODEL = "com.baidu.tieba.pb.pagebrowser.viewmodel.PageBrowserViewModel"
    private const val FRAGMENT = "com.baidu.tieba.pb.pagebrowser.ui.PageBrowserFragment"
    private const val SHARED_FLOW = "kotlinx.coroutines.flow.SharedFlow"
    private const val MUTABLE_FLOW = "kotlinx.coroutines.flow.MutableSharedFlow"
    private const val CONTINUATION = "kotlin.coroutines.Continuation"
    private const val SUSPEND_LAMBDA = "kotlin.coroutines.jvm.internal.SuspendLambda"

    fun scan(bridge: DexKitBridge, logger: ScanLogger?): Pair<String, String>? {
        val dispatch = method("PageStateDispatch", bridge.getClassData(VIEW_MODEL)?.methods.orEmpty().filter {
            "获取页面数据成功" in it.usingStrings && it.paramTypeNames.lastOrNull() == CONTINUATION &&
                it.invokes.any { call -> call.declaredClassName == MUTABLE_FLOW && call.methodName == "emit" }
        }, logger) ?: return null
        val mutable = field("MutablePageState", dispatch.usingFields.map { it.field }, MUTABLE_FLOW, logger)
            ?: return null
        val state = method("PageStateType", bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().addEqString("PageBrowserState(isLoading=")),
        ).filter { it.methodName == "toString" && it.paramTypeNames.isEmpty() && it.returnTypeName == "java.lang.String" }, logger)
            ?: return null
        check(dispatch.invokes.any { it.declaredClassName == state.declaredClassName && it.methodName == "<init>" }) {
            "page dispatcher does not construct the resolved page state"
        }

        val render = method("PageStateConsumer", bridge.findMethod(
            FindMethod.create().searchPackages("com.baidu")
                .matcher(MethodMatcher.create().name("invoke").paramTypes(state.declaredClassName, CONTINUATION)),
        ).filter {
            val owner = bridge.getClassData(it.declaredClassName)
            owner?.superClass?.name == SUSPEND_LAMBDA && owner.fields.any { field -> field.typeName == FRAGMENT }
        }, logger) ?: return null
        val observer = method("PageStateObserver", bridge.getClassData(render.declaredClassName)?.methods.orEmpty()
            .filter { it.methodName == "<init>" }.flatMap { it.callers }.filter {
                it.methodName == "invokeSuspend" && it.invokes.any { call ->
                    call.declaredClassName.startsWith("kotlinx.coroutines.flow.") && call.methodName == "collectLatest" &&
                        Modifier.isStatic(call.modifiers) && call.returnTypeName == "java.lang.Object" &&
                        call.paramTypeNames == listOf("kotlinx.coroutines.flow.Flow", "kotlin.jvm.functions.Function2", CONTINUATION)
                }
            }, logger) ?: return null
        val getter = method("PageStateGetter", observer.invokes.filter {
            it.declaredClassName == VIEW_MODEL && it.returnTypeName == SHARED_FLOW && it.paramTypeNames.isEmpty()
        }, logger) ?: return null
        val exposed = field("ExposedPageState", getter.usingFields.map { it.field }, SHARED_FLOW, logger) ?: return null
        val constructors = bridge.getClassData(VIEW_MODEL)?.methods.orEmpty().filter { it.methodName == "<init>" }
        check(constructors.any { ctor ->
            val fields = ctor.usingFields.map { it.field.descriptor }.toSet()
            mutable.descriptor in fields && exposed.descriptor in fields
        }) { "page state fields are not initialized together" }
        return mutable.fieldName to exposed.fieldName
    }

    private fun method(label: String, candidates: List<MethodData>, logger: ScanLogger?): MethodData? =
        selectUniqueScanCandidate("PbForcePreloadHook.$label", candidates.distinctBy { it.descriptor }, logger) { it.descriptor }

    private fun field(label: String, candidates: List<FieldData>, type: String, logger: ScanLogger?): FieldData? =
        selectUniqueScanCandidate("PbForcePreloadHook.$label", candidates.filter {
            it.declaredClassName == VIEW_MODEL && it.typeName == type &&
                !Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers)
        }.distinctBy { it.descriptor }, logger) { it.descriptor }

    fun restore(cl: ClassLoader, mutableName: String, flowName: String): PbPreloadPageStateTargets {
        val vm = Class.forName(VIEW_MODEL, false, cl)
        check(vm.superclass?.name == "androidx.lifecycle.AndroidViewModel") { "unexpected page ViewModel base" }
        val constructors = vm.declaredConstructors.filter { ctor ->
            val types = ctor.parameterTypes
            Modifier.isPublic(ctor.modifiers) && types.size == 5 && types[0] == Application::class.java &&
                types[1].name == "com.baidu.tieba.pb.pagebrowser.domain.LoadPageDataUseCase" &&
                types[3].name == "com.baidu.tieba.pb.pagebrowser.domain.NetRefreshUseCase"
        }
        check(constructors.size == 1) { "page ViewModel constructor is not unique" }
        val mutable = vm.getDeclaredField(mutableName)
        val exposed = vm.getDeclaredField(flowName)
        check(mutable != exposed && mutable.type.name == MUTABLE_FLOW && exposed.type.name == SHARED_FLOW) {
            "unexpected page state fields"
        }
        for (field in listOf(mutable, exposed)) {
            check(!Modifier.isStatic(field.modifiers) && Modifier.isFinal(field.modifiers)) { "invalid page state field: $field" }
            field.isAccessible = true
        }
        val overflow = Class.forName("kotlinx.coroutines.channels.BufferOverflow", false, cl)
        val factory = Class.forName("kotlinx.coroutines.flow.SharedFlowKt", false, cl)
            .getDeclaredMethod("MutableSharedFlow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, overflow)
        check(Modifier.isPublic(factory.modifiers) && Modifier.isStatic(factory.modifiers) && factory.returnType == mutable.type) {
            "invalid host SharedFlow factory"
        }
        return PbPreloadPageStateTargets(constructors.single(), mutable, exposed, factory, overflow.getField("SUSPEND").get(null)!!)
    }
}
