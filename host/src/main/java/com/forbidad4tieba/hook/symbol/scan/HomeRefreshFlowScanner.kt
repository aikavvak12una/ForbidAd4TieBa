package com.forbidad4tieba.hook.symbol.scan

import com.forbidad4tieba.hook.symbol.contract.CacheTargetValidation
import com.forbidad4tieba.hook.symbol.model.HomeRefreshCarrier
import com.forbidad4tieba.hook.symbol.model.HomeRefreshField
import com.forbidad4tieba.hook.symbol.model.HomeRefreshFlowSymbols
import com.forbidad4tieba.hook.symbol.model.HomeRefreshMethod
import com.forbidad4tieba.hook.symbol.model.ScanLogger
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** One semantic graph identifies lifecycle entries and the initialization cache's own continuations. */
internal object HomeRefreshFlowScanner {
    private const val PAGE = "com.baidu.tieba.homepage.personalize.PersonalizePageView"
    private const val SWIPE = "com.baidu.tieba.homepage.personalize.bigday.BigdaySwipeRefreshLayout"
    private const val CORE = "com.baidu.tbadk.core.TbadkCoreApplication"
    private const val DATA = "tbclient.Personalized.DataRes"
    private const val PAGE_CONTEXT = "com.baidu.tbadk.TbPageContext"
    private const val ASYNC_TASK = "com.baidu.adp.lib.asyncTask.BdAsyncTask"
    private const val APP_INITIALIZE = "com.baidu.searchbox.task.async.appcreate.InitPersonalizePageDataTask"
    private const val MODEL = "com.baidu.tieba.homepage.personalize.model.RecPersonalizePageModel"

    fun scan(cl: ClassLoader, logger: ScanLogger?): HomeRefreshFlowSymbols? =
        ScanDexQueries.query("AutoRefreshHook.Lifecycle", logger) { bridge ->
            fun methods(owner: String): List<MethodData> = checkNotNull(bridge.getClassData(owner)) { "Missing $owner" }.methods.toList()
            fun fields(owner: String): List<FieldData> = checkNotNull(bridge.getClassData(owner)).fields.toList()
            fun <T> one(label: String, values: List<T>): T = requireNotNull(
                uniqueSemanticCandidate("AutoRefreshHook.Lifecycle.$label", values.distinct(), logger),
            ) { "Missing or ambiguous $label" }
            fun type(name: String): Class<*> = if (name == "void") Void.TYPE
                else checkNotNull(CacheTargetValidation.resolveCachedParameterClass(name, cl)) { "Unrestorable type $name" }
            fun method(value: MethodData): Method = value.getMethodInstance(cl).apply {
                require(returnType == type(value.returnTypeName) && parameterTypes.contentEquals(value.paramTypeNames.map(::type).toTypedArray()))
                require(Modifier.isStatic(modifiers) == Modifier.isStatic(value.modifiers))
                isAccessible = true
            }
            fun constructor(value: MethodData): Constructor<*> = type(value.declaredClassName)
                .getDeclaredConstructor(*value.paramTypeNames.map(::type).toTypedArray()).apply { isAccessible = true }
            fun field(value: FieldData): Field = type(value.declaredClassName).getDeclaredField(value.fieldName).apply {
                require(this.type == type(value.typeName) && !Modifier.isStatic(modifiers))
                isAccessible = true
            }
            fun instance(value: MethodData, result: String, vararg params: String): Boolean =
                !Modifier.isStatic(value.modifiers) && value.returnTypeName == result && value.paramTypeNames == params.toList()
            fun createdBy(value: MethodData, params: List<String>, parent: String): List<MethodData> =
                value.invokes.filter { it.methodName == "<init>" && it.paramTypeNames == params && type(parent).isAssignableFrom(type(it.declaredClassName)) }
                    .distinctBy { it.descriptor }
            fun singleField(owner: String, fieldType: String): Field = field(one("$owner/$fieldType", fields(owner)
                .filter { !Modifier.isStatic(it.modifiers) && it.typeName == fieldType }))

            val outMethods = linkedMapOf<HomeRefreshMethod, List<Method>>()
            val outConstructors = linkedMapOf<HomeRefreshCarrier, List<Constructor<*>>>()
            val outFields = linkedMapOf<HomeRefreshField, Field>()
            fun save(role: HomeRefreshMethod, value: MethodData) { outMethods[role] = listOf(method(value)) }

            val refresh = one("refresh", ScanDexQueries.classesUsingStrings(logger,
                "刷新请求数据", "tb.homepage_reco_forum_concern").flatMap(::methods).filter {
                instance(it, "void", "boolean") && it.usingStrings.containsAll(listOf("刷新请求数据", "tb.homepage_reco_forum_concern")) &&
                    it.calls(CORE, "getCurrentAccount")
            })
            val controller = refresh.declaredClassName
            val controlMethods = methods(controller)
            val pageMethods = methods(PAGE)
            save(HomeRefreshMethod.REFRESH, refresh)
            val pageRefresh = one("page refresh bridge", pageMethods.filter {
                instance(it, "void", "boolean") && it.invokes.any { call -> call.descriptor == refresh.descriptor }
            })
            val controllerCtor = one("controller constructor", controlMethods.filter {
                it.methodName == "<init>" && it.paramTypeNames.containsAll(listOf(PAGE, SWIPE))
            })
            val cacheLoad = one("cache task creation", refresh.invokes.filter {
                it.declaredClassName == controller && instance(it, "void", "boolean") &&
                    createdBy(it, listOf(controller, "boolean"), ASYNC_TASK).isNotEmpty()
            })
            val taskCtor = one("cache task", createdBy(cacheLoad, listOf(controller, "boolean"), ASYNC_TASK))
            outConstructors[HomeRefreshCarrier.CACHE_TASK] = listOf(constructor(taskCtor))
            val taskMethods = methods(taskCtor.declaredClassName)
            val background = one("cache background", taskMethods.filter {
                !Modifier.isStatic(it.modifiers) && it.returnTypeName == DATA && it.paramCount == 1 &&
                    type(it.paramTypeNames.single()) == Array<Void>::class.java &&
                    "读取缓存，开关开启" in it.usingStrings && it.calls(CORE, "getCurrentAccount")
            })
            save(HomeRefreshMethod.CACHE_BACKGROUND, background)
            val cacheGet = one("cache read owner", background.invokes.filter { instance(it, DATA) })
            val appInitialize = one("application recommendation initialization", methods(APP_INITIALIZE).filter {
                it.methodName == "execute" && instance(it, "void") &&
                    it.invokes.any { call -> call.declaredClassName == cacheGet.declaredClassName && instance(call, "void") }
            })
            save(HomeRefreshMethod.APP_INITIALIZE, appInitialize)
            val cacheInitialize = one("application cache initialization", appInitialize.invokes.filter {
                it.declaredClassName == cacheGet.declaredClassName && instance(it, "void")
            })
            val initializationCalls = cacheInitialize.invokes.flatMap { it.invokes }.distinctBy { it.descriptor }
            save(HomeRefreshMethod.INITIAL_PREFETCH, one("initial recommendation prefetch", initializationCalls.filter {
                it.declaredClassName == cacheGet.declaredClassName && instance(it, "void", "boolean") &&
                    it.calls("com.baidu.tbadk.abtest.UbsABTestHelper", "isOnlyNewFeedData") &&
                    it.invokes.any { call -> call.declaredClassName == MODEL && Modifier.isStatic(call.modifiers) &&
                        call.returnTypeName == MODEL && call.paramCount == 0 } &&
                    it.invokes.any { call -> call.declaredClassName == MODEL && !Modifier.isStatic(call.modifiers) &&
                        call.returnTypeName == "void" && call.paramCount == 6 &&
                        call.paramTypeNames.take(2) == listOf("int", "int") && call.paramTypeNames.takeLast(3) == listOf("int", "int", "boolean") }
            }))
            val post = one("cache post", taskMethods.filter {
                instance(it, "void", DATA) && it.calls("android.os.MessageQueue", "addIdleHandler")
            })
            val continuation = one("cache continuation", createdBy(post, listOf(taskCtor.declaredClassName), "java.lang.Runnable"))
            val idle = one("cache idle continuation", createdBy(post, listOf(taskCtor.declaredClassName), "android.os.MessageQueue\$IdleHandler"))
            save(HomeRefreshMethod.CACHE_CONTINUE, one("cache runnable", methods(continuation.declaredClassName).filter {
                it.methodName == "run" && instance(it, "void")
            }))
            save(HomeRefreshMethod.IDLE_CONTINUE, one("cache idle callback", methods(idle.declaredClassName).filter {
                it.methodName == "queueIdle" && instance(it, "boolean")
            }))
            outFields[HomeRefreshField.CACHE_CONTINUATION_TASK] = singleField(continuation.declaredClassName, taskCtor.declaredClassName)
            outFields[HomeRefreshField.IDLE_CONTINUATION_TASK] = singleField(idle.declaredClassName, taskCtor.declaredClassName)
            save(HomeRefreshMethod.DISPATCH, one("normal dispatch", controlMethods.filter {
                instance(it, "void") && createdBy(it, listOf(controller), "java.lang.Runnable").isNotEmpty() &&
                    it.invokes.any { call -> call.declaredClassName == cacheGet.declaredClassName && instance(call, "void", "java.lang.Runnable") }
            }))

            val apply = one("new feed cache owner", ScanDexQueries.classesUsingStrings(logger, "parseData. isUpdate:").flatMap(::methods).filter {
                instance(it, "void", "com.squareup.wire.Message", "boolean", "boolean") && "parseData. isUpdate:" in it.usingStrings
            })
            val feed = apply.declaredClassName
            val feedMethods = methods(feed)
            val newLoad = one("new cache entry", feedMethods.flatMap { it.invokes }.filter {
                !Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" && it.paramCount == 2 && it.paramTypeNames[1] == "boolean" &&
                    "recoFeedCacheListener" in it.usingStrings
            }.distinctBy { it.descriptor })
            outFields[HomeRefreshField.NEW_FEED_CACHE] = singleField(feed, newLoad.declaredClassName)
            outFields[HomeRefreshField.NEW_FEED_CACHE_LISTENER] = singleField(feed, newLoad.paramTypeNames[0])
            outMethods[HomeRefreshMethod.CACHE_LOAD] = listOf(method(cacheLoad), method(newLoad))
            val callbackCtor = one("new cache callback", newLoad.invokes.filter {
                it.methodName == "<init>" && it.paramTypeNames == newLoad.paramTypeNames &&
                    methods(it.declaredClassName).any { body -> body.methodName == "onReturnDataInUI" && instance(body, "void", "java.lang.Object") }
            })
            outConstructors[HomeRefreshCarrier.CACHE_CALLBACK] = listOf(constructor(callbackCtor))
            val callbackPost = one("new cache delivery", methods(callbackCtor.declaredClassName).filter {
                it.methodName == "onReturnDataInUI" && instance(it, "void", "java.lang.Object") &&
                    it.invokes.any { call -> call.declaredClassName == newLoad.declaredClassName &&
                        call.paramTypeNames == listOf(newLoad.paramTypeNames[0], "boolean", DATA) }
            })
            outMethods[HomeRefreshMethod.CACHE_POST] = listOf(method(post), method(callbackPost))
            val newCacheRefresh = one("new refresh cache phase", feedMethods.filter {
                instance(it, "void", "boolean") && it.invokes.any { call -> call.descriptor == newLoad.descriptor }
            })
            save(HomeRefreshMethod.NEW_DISPATCH, one("new feed dispatch", feedMethods.filter {
                instance(it, "void") && it.invokes.any { call -> Modifier.isStatic(call.modifiers) && call.returnTypeName == "void" &&
                    call.paramCount == 2 && call.paramTypeNames[0] == "int" && "fetchCache cmd: " in call.usingStrings }
            }))

            val alternate = one("alternate page owner", pageRefresh.invokes.filter {
                !Modifier.isStatic(it.modifiers) && it.paramCount == 0 && it.declaredClassName != controller &&
                    it.declaredClassName.startsWith("com.baidu.tieba.") &&
                    fields(it.declaredClassName).any { value -> value.typeName == PAGE && !Modifier.isStatic(value.modifiers) }
            }).declaredClassName
            val alternateCtor = one("alternate controller constructor", methods(alternate).filter {
                it.methodName == "<init>" && it.paramTypeNames == listOf(PAGE_CONTEXT, PAGE)
            })
            val feedOwnerCtor = one("alternate feed owner", alternateCtor.invokes.filter {
                it.methodName == "<init>" && it.paramTypeNames == listOf(PAGE_CONTEXT, alternate) &&
                    fields(it.declaredClassName).any { value -> value.typeName == feed && !Modifier.isStatic(value.modifiers) } &&
                    it.invokes.any { created -> created.methodName == "<init>" && created.declaredClassName == feed }
            })
            outFields[HomeRefreshField.FEED_OWNER_MODEL] = singleField(feedOwnerCtor.declaredClassName, feed)
            save(HomeRefreshMethod.NEW_REFRESH, one("alternate owner refresh", methods(feedOwnerCtor.declaredClassName).filter {
                instance(it, "void", "boolean") && it.invokes.any { call -> call.descriptor == newCacheRefresh.descriptor } &&
                    it.calls("android.os.Handler", "postDelayed")
            }))
            save(HomeRefreshMethod.PAGE_INITIALIZE, one("page initialization", pageMethods.filter {
                instance(it, "void", "android.content.Context") &&
                    it.invokes.any { call -> call.descriptor == controllerCtor.descriptor } &&
                    it.invokes.any { call -> call.descriptor == alternateCtor.descriptor }
            }))
            save(HomeRefreshMethod.CACHE_INITIALIZE, one("one-time cache initialization", pageMethods.filter {
                instance(it, "void") && "push_tid" in it.usingStrings &&
                    it.calls("java.util.concurrent.atomic.AtomicBoolean", "compareAndSet")
            }))
            val trigger = one("page refresh preparation", pageMethods.filter {
                instance(it, "void") && it.invokes.any { call -> call.declaredClassName == "com.baidu.adp.widget.ListView.BdRecyclerView" &&
                    call.methodName == "setSelection" && call.paramTypeNames == listOf("int") } &&
                    it.invokes.any { call -> call.declaredClassName == SWIPE && call.methodName == "setRefreshing" && call.paramTypeNames == listOf("boolean") }
            })
            save(HomeRefreshMethod.PAGE_TRIGGER, trigger)
            save(HomeRefreshMethod.PAGE_RESUME, one("page resume refresh", pageMethods.filter {
                instance(it, "void") && "首页刷新最近逛的吧" in it.usingStrings &&
                    it.invokes.any { call -> call.descriptor == trigger.descriptor }
            }))
            save(HomeRefreshMethod.PAGE_FOREGROUND, one("page foreground refresh", pageMethods.filter {
                it.methodName == "setViewForeground" && instance(it, "void") && "HomeHotReloadHelper" in it.usingStrings &&
                    it.invokes.any { call -> call.descriptor == trigger.descriptor }
            }))
            save(HomeRefreshMethod.REFRESH_INDICATOR, one("refresh indicator", methods(SWIPE).filter {
                it.methodName == "setRefreshing" && instance(it, "void", "boolean")
            }))
            HomeRefreshFlowSymbols(outMethods, outConstructors, outFields)
        }
}
