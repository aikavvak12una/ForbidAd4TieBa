package com.forbidad4tieba.hook.feature.account

import org.junit.Assert.*
import org.junit.Test

class AccountListDedupTest {
    private val getter = Account::class.java.getMethod("getId")

    @Test fun noDuplicatesPreserveTheOriginalListAndDuplicateIdsKeepTheFirstRow() {
        val first = Account("123")
        val second = Account("456")
        val unique = arrayListOf(first, second)
        assertSame(unique, AccountListDedupHook.dedupeById(unique, Account::class.java, getter))
        val duplicate = Account(" 123 ")
        val input = arrayListOf(first, second, duplicate, first)
        assertEquals(listOf(first, second), AccountListDedupHook.dedupeById(input, Account::class.java, getter))
        assertEquals(listOf(first, second, duplicate, first), input)
    }

    @Test fun unreadableNullEmptyAndUnknownEntriesSurviveAfterTheFirstDuplicate() {
        val first = Account("123")
        val unreadable = Account("private", true)
        val empty = Account("")
        val unknown = Any()
        val input = arrayListOf(first, Account("123"), null, unreadable, empty, empty, unknown)
        assertEquals(listOf(first, null, unreadable, empty, empty, unknown),
            AccountListDedupHook.dedupeById(input, Account::class.java, getter))
        val nonArrayList = listOf(first, first)
        assertSame(nonArrayList, AccountListDedupHook.dedupeById(nonArrayList, Account::class.java, getter))
    }

    class Account(private val value: String?, private val fail: Boolean = false) {
        fun getId(): String? = if (fail) error("fixture unreadable id") else value
    }
}
