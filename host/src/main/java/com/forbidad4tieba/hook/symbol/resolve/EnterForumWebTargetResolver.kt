package com.forbidad4tieba.hook.symbol.resolve

import com.forbidad4tieba.hook.symbol.contract.*

import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.contracts.Diagnostics
import com.forbidad4tieba.hook.symbol.model.EnterForumWebSymbols
import com.forbidad4tieba.hook.symbol.model.HookSymbols
import com.forbidad4tieba.hook.symbol.scan.ScanReflection
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object EnterForumWebTargetResolver {
    fun resolve(cl: ClassLoader, symbols: HookSymbols?): EnterForumWebSymbols? {
        if (symbols == null) {
            Diagnostics.log("[EnterForumWebHook] skipped: scan symbols unavailable")
            return null
        }
        val load = resolveWebLoad(cl, symbols) ?: return null
        return load.copy(sourceGetUrlMethod = resolveSource(cl, symbols))
    }

    /** Shared by cache validation and installation; a URL source alone is not safe to install. */
    fun resolveWebLoad(cl: ClassLoader, symbols: HookSymbols): EnterForumWebSymbols? = try {
        val controller = Class.forName(required("controller", symbols[EnterForumContract.enterForumWebControllerClass]), false, cl)
        val web = Class.forName(StableTiebaHookPoints.TB_WEB_VIEW_CLASS, false, cl)
        val owner = Class.forName(required("webView owner", symbols[EnterForumContract.enterForumWebViewFieldOwnerClass]), false, cl)
        check(!controller.isInterface && !Modifier.isAbstract(controller.modifiers)) { "controller is not concrete" }
        check(owner.isAssignableFrom(controller)) { "webView owner is not in the controller hierarchy" }
        val field = owner.getDeclaredField(required("webView field", symbols[EnterForumContract.enterForumWebViewField]))
        check(!Modifier.isStatic(field.modifiers) && field.type == web &&
            ScanReflection.collectInstanceFields(controller).filter { it.type == web }.singleOrNull() == field) {
            "controller webView field mismatch or ambiguity"
        }
        val load = controller.getDeclaredMethod(required("load method", symbols[EnterForumContract.enterForumWebLoadMethod]), String::class.java)
        check(!Modifier.isStatic(load.modifiers) && load.returnType == Void.TYPE) { "load method signature mismatch" }
        check(symbols[EnterForumContract.enterForumWebSetForceCommonMethod] == "setForceCommon") { "common policy method mismatch" }
        val policy = web.getDeclaredMethod("setForceCommon", Boolean::class.javaPrimitiveType)
        check(!Modifier.isStatic(policy.modifiers) && policy.returnType == Void.TYPE) { "common policy signature mismatch" }
        field.isAccessible = true
        load.isAccessible = true
        policy.isAccessible = true
        EnterForumWebSymbols(null, load, field, policy)
    } catch (failure: Throwable) {
        Diagnostics.log("[EnterForumWebHook] load policy resolve FAILED: $failure")
        null
    }

    private fun resolveSource(cl: ClassLoader, symbols: HookSymbols): Method? {
        val owner = symbols[EnterForumContract.enterForumInitInfoDataClass]?.takeIf { it.isNotBlank() } ?: return null
        val name = symbols[EnterForumContract.enterForumInitInfoGetUrlMethod]?.takeIf { it.isNotBlank() } ?: return null
        return try {
            Class.forName(owner, false, cl).getMethod(name).also {
                check(!Modifier.isStatic(it.modifiers) && it.returnType == String::class.java) { "source signature mismatch" }
                it.isAccessible = true
            }
        } catch (failure: Throwable) {
            Diagnostics.log("[EnterForumWebHook] optional source resolve FAILED: $failure")
            null
        }
    }

    private fun required(role: String, value: String?): String =
        requireNotNull(value?.takeIf { it.isNotBlank() }) { "missing $role" }
}
