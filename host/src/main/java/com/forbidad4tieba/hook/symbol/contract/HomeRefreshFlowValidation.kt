package com.forbidad4tieba.hook.symbol.contract

import com.forbidad4tieba.hook.symbol.model.HomeRefreshCarrier as CarrierRole
import com.forbidad4tieba.hook.symbol.model.HomeRefreshField as FieldRole
import com.forbidad4tieba.hook.symbol.model.HomeRefreshMethod as MethodRole
import com.forbidad4tieba.hook.symbol.model.HomeRefreshFlowSymbols
import java.lang.reflect.Modifier

/** Restore only the lifecycle and cache connections that the runtime consumes. */
internal object HomeRefreshFlowValidation {
    fun validate(flow: HomeRefreshFlowSymbols) {
        fun checkRole(valid: Boolean, role: Any) = require(valid) { "Invalid HomeRefresh role: $role" }
        fun field(role: FieldRole) = flow.field(role)
        fun inherits(type: Class<*>, name: String): Boolean =
            type.name == name || type.interfaces.any { inherits(it, name) } || type.superclass?.let { inherits(it, name) } == true
        flow.methods.forEach { (role, methods) ->
            val count = if (role == MethodRole.CACHE_LOAD || role == MethodRole.CACHE_POST) 2 else 1
            checkRole(methods.size == count && methods.distinct().size == count, "$role count")
        }
        val allMethods = flow.methods.values.flatten()
        checkRole(allMethods.distinct().size == allMethods.size, "method role overlap")
        flow.constructors.forEach { (role, constructors) ->
            checkRole(constructors.size == 1 && constructors.none { Modifier.isAbstract(it.declaringClass.modifiers) }, "$role count/concrete owner")
        }

        val page = flow.method(MethodRole.PAGE_INITIALIZE).declaringClass
        val swipe = flow.method(MethodRole.REFRESH_INDICATOR).declaringClass
        val controller = flow.method(MethodRole.REFRESH).declaringClass
        val feedOwner = field(FieldRole.FEED_OWNER_MODEL).declaringClass
        val feed = field(FieldRole.FEED_OWNER_MODEL).type
        val cache = field(FieldRole.NEW_FEED_CACHE).type
        val task = flow.constructors.getValue(CarrierRole.CACHE_TASK).single()
        val callback = flow.constructors.getValue(CarrierRole.CACHE_CALLBACK).single()
        val cacheLoad = flow.methods.getValue(MethodRole.CACHE_LOAD)
        val prefetchOwner = flow.method(MethodRole.INITIAL_PREFETCH).declaringClass
        checkRole(prefetchOwner.declaredMethods.count {
            !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) && it.parameterCount == 0 && it.returnType.name == DATA
        } == 1 && prefetchOwner.declaredMethods.count {
            Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == prefetchOwner
        } == 1, "initial prefetch cache owner protocol")
        checkRole(page.name == PAGE && swipe.name == SWIPE, "page/swipe classes")
        checkRole(setOf(controller, feedOwner, feed, cache).size == 4, "lifecycle/cache owner separation")
        checkRole(cacheLoad.map { it.declaringClass }.toSet() == setOf(controller, cache), "cache owner coverage")
        val newLoad = cacheLoad.single { it.declaringClass == cache }
        checkRole(newLoad.parameterCount == 2 && newLoad.parameterTypes[0].isInterface &&
            newLoad.parameterTypes[1] == java.lang.Boolean.TYPE, "new cache listener protocol")
        val listener = newLoad.parameterTypes[0]

        flow.fields.forEach { (role, value) ->
            checkRole(!Modifier.isStatic(value.modifiers), role)
            val valid = when (role) {
                FieldRole.FEED_OWNER_MODEL -> value.declaringClass == feedOwner && value.type == feed &&
                    feedOwner == flow.method(MethodRole.NEW_REFRESH).declaringClass
                FieldRole.NEW_FEED_CACHE -> value.declaringClass == feed && value.type == cache
                FieldRole.NEW_FEED_CACHE_LISTENER -> value.declaringClass == feed && listener.isAssignableFrom(value.type)
                FieldRole.CACHE_CONTINUATION_TASK -> value.declaringClass == flow.method(MethodRole.CACHE_CONTINUE).declaringClass &&
                    value.type == task.declaringClass && inherits(value.declaringClass, "java.lang.Runnable")
                FieldRole.IDLE_CONTINUATION_TASK -> value.declaringClass == flow.method(MethodRole.IDLE_CONTINUE).declaringClass &&
                    value.type == task.declaringClass && inherits(value.declaringClass, "android.os.MessageQueue\$IdleHandler")
            }
            checkRole(valid, role)
        }
        checkRole(inherits(task.declaringClass, ASYNC_TASK) &&
            task.parameterTypes.contentEquals(arrayOf(controller, java.lang.Boolean.TYPE)), "cache task constructor")
        checkRole(callback.parameterTypes.contentEquals(newLoad.parameterTypes), "cache callback constructor")
        checkRole(flow.methods.getValue(MethodRole.CACHE_POST).map { it.declaringClass }.toSet() ==
            setOf(task.declaringClass, callback.declaringClass), "cache post owner coverage")

        flow.methods.forEach { (role, methods) -> methods.forEach { method ->
            fun instance(owner: Class<*>, returns: String, vararg params: String): Boolean =
                method.declaringClass == owner && !Modifier.isStatic(method.modifiers) &&
                    method.returnType.name == returns && method.parameterTypes.map { it.name } == params.toList()
            checkRole(!Modifier.isAbstract(method.modifiers), "$role concrete member")
            val valid = when (role) {
                MethodRole.APP_INITIALIZE -> method.declaringClass.name == APP_INITIALIZE && method.name == "execute" &&
                    instance(method.declaringClass, "void")
                MethodRole.INITIAL_PREFETCH -> instance(method.declaringClass, "void", "boolean")
                MethodRole.PAGE_INITIALIZE -> instance(page, "void", "android.content.Context")
                MethodRole.CACHE_INITIALIZE, MethodRole.PAGE_RESUME, MethodRole.PAGE_TRIGGER -> instance(page, "void")
                MethodRole.PAGE_FOREGROUND -> method.name == "setViewForeground" && instance(page, "void")
                MethodRole.REFRESH -> instance(controller, "void", "boolean")
                MethodRole.NEW_REFRESH -> instance(feedOwner, "void", "boolean")
                MethodRole.CACHE_LOAD -> instance(controller, "void", "boolean") ||
                    instance(cache, "void", listener.name, "boolean")
                MethodRole.DISPATCH -> instance(controller, "void")
                MethodRole.NEW_DISPATCH -> instance(feed, "void")
                MethodRole.CACHE_BACKGROUND -> instance(task.declaringClass, DATA, "[Ljava.lang.Void;")
                MethodRole.CACHE_POST -> instance(task.declaringClass, "void", DATA) ||
                    (method.name == "onReturnDataInUI" && instance(callback.declaringClass, "void", "java.lang.Object"))
                MethodRole.CACHE_CONTINUE -> method.name == "run" && instance(field(FieldRole.CACHE_CONTINUATION_TASK).declaringClass, "void")
                MethodRole.IDLE_CONTINUE -> method.name == "queueIdle" && instance(field(FieldRole.IDLE_CONTINUATION_TASK).declaringClass, "boolean")
                MethodRole.REFRESH_INDICATOR -> method.name == "setRefreshing" && instance(swipe, "void", "boolean")
            }
            checkRole(valid, role)
        } }
    }

    private const val PAGE = "com.baidu.tieba.homepage.personalize.PersonalizePageView"
    private const val SWIPE = "com.baidu.tieba.homepage.personalize.bigday.BigdaySwipeRefreshLayout"
    private const val ASYNC_TASK = "com.baidu.adp.lib.asyncTask.BdAsyncTask"
    private const val DATA = "tbclient.Personalized.DataRes"
    private const val APP_INITIALIZE = "com.baidu.searchbox.task.async.appcreate.InitPersonalizePageDataTask"
}
