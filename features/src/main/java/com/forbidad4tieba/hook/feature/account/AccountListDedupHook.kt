package com.forbidad4tieba.hook.feature.account

import com.forbidad4tieba.hook.contracts.MemberAccess
import com.forbidad4tieba.hook.core.StableTiebaHookPoints
import com.forbidad4tieba.hook.core.XposedCompat
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Collapses duplicate rows of the same account in the host "我的账号" list.
 *
 * Host defect (22.10.1.0): `account_data` carries no unique constraint on `id`, and
 * `AccountStorage.saveAccountData` writes with a non-atomic `delete from account_data where id=?`
 * followed by `Insert into account_data(...)`. It is reachable from ten call sites, several of them
 * background runnables and login callbacks, across the four host processes. Two interleaved saves
 * therefore leave two rows with the same `id` and `isactive=1`, and `getAllAccountData()`
 * (`select * from account_data order by time desc`) hands both to `AccountActivity`, which renders
 * one row per entry.
 *
 * The dedupe runs on the read side because it stays correct whether the duplicate is a stale
 * leftover or is being regenerated, and because it never writes to the host account table.
 * `AccountStorage.getAllAccountData()` has exactly one consumer (`AccountActivity.k0()`), so the
 * blast radius is the account list alone.
 */
object AccountListDedupHook {
    private const val TAG = "[AccountListDedupHook]"
    private const val FEATURE_ID = "AccountListDedupHook"

    @Volatile
    private var installed = false

    fun hook(classLoader: ClassLoader) {
        if (!markInstalled()) return
        try {
            val storageClass = MemberAccess.findClassOrNull(
                StableTiebaHookPoints.ACCOUNT_STORAGE_CLASS,
                classLoader,
            ) ?: return failClosed("class not found: ${StableTiebaHookPoints.ACCOUNT_STORAGE_CLASS}")

            val accountDataClass = MemberAccess.findClassOrNull(
                StableTiebaHookPoints.ACCOUNT_DATA_CLASS,
                classLoader,
            ) ?: return failClosed("class not found: ${StableTiebaHookPoints.ACCOUNT_DATA_CLASS}")

            val getAllMethod = MemberAccess.findMethodOrNull(
                storageClass,
                StableTiebaHookPoints.METHOD_GET_ALL_ACCOUNT_DATA,
            ) ?: return failClosed("method not found: ${StableTiebaHookPoints.METHOD_GET_ALL_ACCOUNT_DATA}")
            if (!Modifier.isStatic(getAllMethod.modifiers) ||
                !List::class.java.isAssignableFrom(getAllMethod.returnType)
            ) {
                return failClosed(
                    "unexpected signature: ${StableTiebaHookPoints.METHOD_GET_ALL_ACCOUNT_DATA} " +
                        "static=${Modifier.isStatic(getAllMethod.modifiers)} " +
                        "returns=${getAllMethod.returnType.name}",
                )
            }

            val getIdMethod = MemberAccess.findMethodOrNull(
                accountDataClass,
                StableTiebaHookPoints.METHOD_GET_ID,
            ) ?: return failClosed("method not found: ${StableTiebaHookPoints.METHOD_GET_ID}")
            if (getIdMethod.returnType != String::class.java) {
                return failClosed(
                    "unexpected signature: ${StableTiebaHookPoints.METHOD_GET_ID} " +
                        "returns=${getIdMethod.returnType.name}",
                )
            }

            val handle = XposedCompat.interceptHook(FEATURE_ID, getAllMethod) { chain ->
                val original = chain.proceed()
                dedupeById(original, accountDataClass, getIdMethod)
            }
            if (handle == null) {
                return failClosed("hook handle unavailable")
            }
            XposedCompat.log("$TAG hook INSTALLED: AccountStorage.getAllAccountData")
        } catch (t: Throwable) {
            resetInstalled()
            XposedCompat.log("$TAG hook install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    /**
     * Keeps the first entry per account id. The host query is ordered by `time desc`, so the first
     * occurrence is the freshest row. Entries whose identity cannot be read are always kept: an
     * unreadable id must never cost the user a real account.
     */
    private fun dedupeById(
        original: Any?,
        accountDataClass: Class<*>,
        getIdMethod: Method,
    ): Any? {
        val source = original as? ArrayList<*> ?: return original
        if (source.size < 2) return original

        val seenIds = HashSet<String>(source.size)
        val deduped = ArrayList<Any?>(source.size)
        for (entry in source) {
            if (entry == null || !accountDataClass.isInstance(entry)) {
                deduped.add(entry)
                continue
            }
            val id = try {
                (getIdMethod.invoke(entry) as? String)?.trim()
            } catch (t: Throwable) {
                XposedCompat.logD { "$TAG id read failed, entry kept: ${t.message}" }
                null
            }
            if (id.isNullOrEmpty() || seenIds.add(id)) {
                deduped.add(entry)
            }
        }

        if (deduped.size == source.size) return original
        XposedCompat.logD {
            "$TAG duplicate accounts collapsed: ${source.size} -> ${deduped.size}"
        }
        return deduped
    }

    private fun failClosed(reason: String) {
        resetInstalled()
        XposedCompat.logW("$TAG disabled: $reason")
    }

    private fun markInstalled(): Boolean {
        synchronized(this) {
            if (installed) return false
            installed = true
            return true
        }
    }

    private fun resetInstalled() {
        synchronized(this) { installed = false }
    }
}
