package com.forbidad4tieba.hook.symbol.contract

import android.content.Context
import com.baidu.searchbox.task.async.appcreate.InitPersonalizePageDataTask
import com.baidu.adp.lib.asyncTask.BdAsyncTask
import com.baidu.tieba.homepage.personalize.PersonalizePageView
import com.baidu.tieba.homepage.personalize.bigday.BigdaySwipeRefreshLayout
import com.forbidad4tieba.hook.symbol.model.HomeRefreshCarrier as CarrierRole
import com.forbidad4tieba.hook.symbol.model.HomeRefreshField as FieldRole
import com.forbidad4tieba.hook.symbol.model.HomeRefreshMethod as MethodRole
import com.forbidad4tieba.hook.symbol.model.HomeRefreshFlowSymbols
import java.lang.reflect.Method
import tbclient.Personalized.DataRes

/** Type-only lifecycle/cache graph. Restoration never executes its members. */
@Suppress("UNUSED_PARAMETER")
internal object HomeRefreshFlowFixture {
    interface CacheListener
    class Controller {
        fun refresh(force: Boolean) {}
        fun loadCache(force: Boolean) {}
        fun dispatch() {}
        fun wrongBackground(args: Array<Void>): Boolean = false
        companion object { @JvmStatic fun staticRefresh(force: Boolean) {} }
    }
    class FeedOwner {
        @JvmField var feed: NewFeed? = null
        fun refresh(force: Boolean) {}
    }
    class NewFeed {
        @JvmField var cache: Cache? = null
        @JvmField var cacheListener: CacheListener? = null
        fun dispatch() {}
        companion object { @JvmField var staticListener: CacheListener? = null }
    }
    class Cache {
        fun load(listener: CacheListener, force: Boolean) {}
        fun wrongLoad(listener: String, force: Boolean) {}
    }
    class CacheReader {
        fun read(): DataRes = error("No host code")
        fun prefetch(force: Boolean) {}
        fun wrongPrefetch(force: Int) {}
        companion object {
            @JvmStatic fun instance(): CacheReader = error("No host code")
            @JvmStatic fun staticPrefetch(force: Boolean) {}
        }
    }
    class LegacyTask(controller: Controller, force: Boolean) : BdAsyncTask() {
        fun background(args: Array<Void>): DataRes = error("No host code")
        fun post(data: DataRes) {}
    }
    class OtherTask(controller: Controller, force: Boolean) : BdAsyncTask()
    class CacheCallback(listener: CacheListener, force: Boolean) { fun onReturnDataInUI(data: Any) {} }
    class OtherCallback(listener: CacheListener, force: Boolean)
    class CacheContinuation(task: LegacyTask) : Runnable {
        @JvmField var task: LegacyTask? = null
        override fun run() {}
        companion object { @JvmField var staticTask: LegacyTask? = null }
    }
    class IdleContinuation(task: LegacyTask) : android.os.MessageQueue.IdleHandler {
        @JvmField var task: LegacyTask? = null
        override fun queueIdle(): Boolean = false
    }
    class Other {
        @JvmField var task: LegacyTask? = null
        @JvmField var cacheListener: CacheListener? = null
        fun refresh(force: Boolean) {}
    }

    fun method(owner: Class<*>, name: String, vararg params: Class<*>): Method =
        owner.getDeclaredMethod(name, *params).apply { isAccessible = true }

    fun flow(): HomeRefreshFlowSymbols {
        val boolean = java.lang.Boolean.TYPE
        val page = PersonalizePageView::class.java
        val methods = MethodRole.entries.associateWith { role -> when (role) {
            MethodRole.APP_INITIALIZE -> listOf(method(InitPersonalizePageDataTask::class.java, "execute"))
            MethodRole.INITIAL_PREFETCH -> listOf(method(CacheReader::class.java, "prefetch", boolean))
            MethodRole.PAGE_INITIALIZE -> listOf(method(page, "initialize", Context::class.java))
            MethodRole.CACHE_INITIALIZE -> listOf(method(page, "initializeCache"))
            MethodRole.PAGE_RESUME -> listOf(method(page, "resume"))
            MethodRole.PAGE_FOREGROUND -> listOf(method(page, "setViewForeground"))
            MethodRole.PAGE_TRIGGER -> listOf(method(page, "trigger"))
            MethodRole.REFRESH -> listOf(method(Controller::class.java, "refresh", boolean))
            MethodRole.NEW_REFRESH -> listOf(method(FeedOwner::class.java, "refresh", boolean))
            MethodRole.CACHE_LOAD -> listOf(method(Controller::class.java, "loadCache", boolean),
                method(Cache::class.java, "load", CacheListener::class.java, boolean))
            MethodRole.DISPATCH -> listOf(method(Controller::class.java, "dispatch"))
            MethodRole.NEW_DISPATCH -> listOf(method(NewFeed::class.java, "dispatch"))
            MethodRole.CACHE_BACKGROUND -> listOf(method(LegacyTask::class.java, "background", Array<Void>::class.java))
            MethodRole.CACHE_POST -> listOf(method(LegacyTask::class.java, "post", DataRes::class.java),
                method(CacheCallback::class.java, "onReturnDataInUI", Any::class.java))
            MethodRole.CACHE_CONTINUE -> listOf(method(CacheContinuation::class.java, "run"))
            MethodRole.IDLE_CONTINUE -> listOf(method(IdleContinuation::class.java, "queueIdle"))
            MethodRole.REFRESH_INDICATOR -> listOf(method(BigdaySwipeRefreshLayout::class.java, "setRefreshing", boolean))
        } }
        fun constructor(owner: Class<*>, vararg params: Class<*>) = owner.getDeclaredConstructor(*params).apply { isAccessible = true }
        val constructors = CarrierRole.entries.associateWith { role -> when (role) {
            CarrierRole.CACHE_TASK -> listOf(constructor(LegacyTask::class.java, Controller::class.java, boolean))
            CarrierRole.CACHE_CALLBACK -> listOf(constructor(CacheCallback::class.java, CacheListener::class.java, boolean))
        } }
        val fields = FieldRole.entries.associateWith { role ->
            val (owner, name) = when (role) {
                FieldRole.FEED_OWNER_MODEL -> FeedOwner::class.java to "feed"
                FieldRole.NEW_FEED_CACHE -> NewFeed::class.java to "cache"
                FieldRole.NEW_FEED_CACHE_LISTENER -> NewFeed::class.java to "cacheListener"
                FieldRole.CACHE_CONTINUATION_TASK -> CacheContinuation::class.java to "task"
                FieldRole.IDLE_CONTINUATION_TASK -> IdleContinuation::class.java to "task"
            }
            owner.getDeclaredField(name).apply { isAccessible = true }
        }
        return HomeRefreshFlowSymbols(methods, constructors, fields)
    }
}
