package com.forbidad4tieba.hook.symbol.model

import android.content.Context
import android.app.Activity
import com.forbidad4tieba.hook.symbol.scan.CommentFilterSymbolScanner
import com.forbidad4tieba.hook.symbol.scan.InlineReplySymbolScanner
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** All reflective members are validated at restoration, never discovered by a callback. */
class InlineReplyTargets internal constructor(private val cl: ClassLoader, spec: String) {
    private val json = JSONObject(spec)
    private fun type(name: String) = Class.forName(name, false, cl)
    private val post = type(InlineReplySymbolScanner.POST)
    private val thread = type(InlineReplySymbolScanner.THREAD)
    private val forum = type(InlineReplySymbolScanner.FORUM)
    private val row = type(json.getString("row"))
    private val view = type(InlineReplySymbolScanner.VIEW)
    private val adapter = type(InlineReplySymbolScanner.ADAPTER)
    private fun method(owner: Class<*>, name: String, returns: Class<*>, vararg params: Class<*>): Method =
        owner.getMethod(name, *params).apply {
            check(returnType == returns && !Modifier.isAbstract(modifiers)); isAccessible = true
        }
    private fun field(owner: Class<*>, name: String, expected: Class<*>): Field = owner.getDeclaredField(name).apply {
        check(type == expected && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private fun typed(owner: Class<*>, expected: Class<*>): Field = owner.declaredFields.single {
        it.type == expected && !Modifier.isStatic(it.modifiers)
    }.apply { isAccessible = true }
    private val intType = Int::class.javaPrimitiveType!!
    private val longType = Long::class.javaPrimitiveType!!
    private val voidType = Void.TYPE
    val bind = method(view, json.getString("bind"), voidType, row)
    val attach = view.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }
    val detach = view.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }
    val windowFocus = Activity::class.java.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType)
    val rowPost = typed(row, post)
    val rowThread = typed(row, thread)
    val rowForum = typed(row, forum)
    val rowComponents = typed(row, List::class.java)
    val rowAdapter = view.getDeclaredField(json.getString("adapter")).apply {
        check(adapter.isAssignableFrom(type) && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    val setComponents = method(adapter, "setList", voidType, List::class.java)
    val children = method(post, json.getString("children"), ArrayList::class.java)
    val count = method(post, json.getString("count"), intType)
    val build = method(type(InlineReplySymbolScanner.SUB), json.getString("build"), voidType,
        post, List::class.java, thread, forum).apply { check(Modifier.isStatic(modifiers)) }
    val wrapper = type(json.getString("wrapper"))
    val postId = method(post, "getId", String::class.java)
    val threadId = method(thread, "getTid", String::class.java)
    val forumId = method(forum, "getId", String::class.java)
    val account = method(type("com.baidu.tbadk.core.TbadkCoreApplication"), "getCurrentAccount", String::class.java)

    private val requestClass = type("com.baidu.tieba.pb.pb.sub.SubPbRequestMessage")
    val request = requestClass.getConstructor(Context::class.java, longType, longType, longType,
        intType, intType, intType, Double::class.javaPrimitiveType, String::class.java, intType,
        Integer::class.java, String::class.java)
    val setForum = method(requestClass, "setForumId", voidType, longType)
    private val uniqueId = type("com.baidu.adp.BdUniqueId")
    val newTag = method(uniqueId, "gen", uniqueId)
    val setTag = method(requestClass, "setTag", voidType, uniqueId)
    private val manager = type("com.baidu.adp.framework.MessageManager")
    private val message = type("com.baidu.adp.framework.message.Message")
    private val response = type("com.baidu.adp.framework.message.ResponsedMessage")
    val managerInstance = method(manager, "getInstance", manager)
    val send = method(manager, "sendMessage", Boolean::class.javaPrimitiveType!!, requestClass.superclass)
    val cancel = method(manager, "removeMessage", voidType, uniqueId)
    val dispatch = method(manager, "dispatchResponsedMessage", voidType, response)
    val original = method(response, "getOrginalMessage", message)
    val extra = method(message, "getExtra", Any::class.java)
    val transportError = method(response, "getError", intType)
    private val parser = CommentFilterSymbolScanner.restore(json.getString("parser"), CommentFilterSymbolScanner.Path.FLOOR_NATIVE, cl)
    val replies = field(parser.returnType, json.getString("replies"), ArrayList::class.java)
    private val responseFields = listOf("SubPbSocketResponseMessage", "SubPbHttpResponseMessage").map {
        val owner = type("com.baidu.tieba.pb.pb.sub.$it")
        check(response.isAssignableFrom(owner))
        Triple(owner, field(owner, "pbFloorData", parser.returnType), field(owner, "floorJson", JSONObject::class.java))
    }
    fun result(response: Any): Pair<Any, JSONObject>? {
        val fields = responseFields.singleOrNull { it.first.isInstance(response) } ?: return null
        val data = fields.second.get(response) ?: return null
        val json = fields.third.get(response) as? JSONObject ?: return null
        return data to json
    }
}
