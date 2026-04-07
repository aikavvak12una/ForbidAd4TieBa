package com.forbidad4tieba.hook.feature.ui

import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat

object MainTabBottomHook {

    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val className = "com.baidu.tbadk.mainTab.MaintabAddResponedData"

        try {
            val clazz = XposedCompat.findClassOrNull(className, cl)
            val delegateClass = XposedCompat.findClassOrNull("com.baidu.tbadk.mainTab.FragmentDelegate", cl)
            if (clazz == null || delegateClass == null) {
                XposedCompat.log("[MainTabBottomHook] class NOT FOUND: $className or FragmentDelegate")
                return
            }

            // Hook addFragment(FragmentDelegate)
            val addMethod = XposedCompat.findMethodOrNull(clazz, "addFragment", delegateClass)
            if (addMethod != null) {
                mod.hook(addMethod).intercept { chain ->
                    if (ConfigManager.isBottomTabSimplifyEnabled) {
                        val delegate = chain.args[0]
                        if (delegate != null) {
                            try {
                                val ftsMethod = XposedCompat.findMethodOrNull(delegateClass, "getFragmentTabStructure")
                                val fts = ftsMethod?.invoke(delegate)
                                if (fts != null) {
                                    val typeField = try {
                                        fts.javaClass.getDeclaredField("type").apply { isAccessible = true }
                                    } catch (_: Throwable) { null }
                                    val type = typeField?.getInt(fts) ?: -1
                                    // type 5 = RetailStore (小卖部), type 14 = MemberCenter (会员中心)
                                    if (type == 5 || type == 14) {
                                        XposedCompat.log("[MainTabBottomHook] Blocked addFragment for: ${delegate.javaClass.name} (type: $type)")
                                        return@intercept null
                                    }
                                }
                            } catch (t: Throwable) {
                                XposedCompat.log("[MainTabBottomHook] type check error: ${t.message}")
                            }
                        }
                    }
                    chain.proceed()
                }
            }

            // Hook getList() to be absolutely safe
            val getListMethod = XposedCompat.findMethodOrNull(clazz, "getList")
            if (getListMethod != null) {
                mod.hook(getListMethod).intercept { chain ->
                    val result = chain.proceed()
                    if (ConfigManager.isBottomTabSimplifyEnabled) {
                        @Suppress("UNCHECKED_CAST")
                        val list = result as? java.util.ArrayList<Any>
                        if (list != null) {
                            val it = list.iterator()
                            while (it.hasNext()) {
                                val delegate = it.next()
                                try {
                                    val ftsMethod = XposedCompat.findMethodOrNull(delegateClass, "getFragmentTabStructure")
                                    val fts = ftsMethod?.invoke(delegate)
                                    if (fts != null) {
                                        val typeField = try {
                                            fts.javaClass.getDeclaredField("type").apply { isAccessible = true }
                                        } catch (_: Throwable) { null }
                                        val type = typeField?.getInt(fts) ?: -1
                                        if (type == 5 || type == 14) {
                                            it.remove()
                                            XposedCompat.log("[MainTabBottomHook] Removed from getList: ${delegate.javaClass.name} (type: $type)")
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                    result
                }
            }

            XposedCompat.log("[MainTabBottomHook] hook INSTALLED on $className")
        } catch (t: Throwable) {
            XposedCompat.log("[MainTabBottomHook] FAILED ($className): ${t.message}")
            XposedCompat.log(t)
        }
    }
}
