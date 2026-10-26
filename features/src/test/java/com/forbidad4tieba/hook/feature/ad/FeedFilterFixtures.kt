package com.forbidad4tieba.hook.feature.ad

import com.forbidad4tieba.hook.symbol.model.CustomPostCardFilterSymbols
import java.lang.reflect.Method

/** Like the host's template interface, both cards and their components dispatch virtually. */
interface FeedTemplateFixture {
    fun templateKey(): String?
    fun payload(): Any? = null
}

internal fun feedFilterFixture(data: Class<*>, head: Class<*>, schema: Method? = null) = CustomPostCardFilterSymbols(
    dataListField = data.getField("dataList"),
    templateKeyMethod = FeedTemplateFixture::class.java.getMethod("templateKey"),
    templatePayloadMethod = FeedTemplateFixture::class.java.getMethod("payload"),
    headParamsField = head.getField("params"),
    recommendNestedDataMethod = null,
    recommendNestedDataListField = null,
    schemaGetter = schema,
)
