package com.forbidad4tieba.hook.symbol.model

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.forbidad4tieba.hook.symbol.scan.CommentShortcutSymbolScanner
import org.json.JSONObject
import java.lang.reflect.Modifier

class CommentShortcutTargets internal constructor(private val cl: ClassLoader, spec: String) {
    private val json = JSONObject(spec)
    private fun type(name: String) = Class.forName(name, false, cl)
    val viewClass = type(CommentShortcutSymbolScanner.VIEW).also { check(LinearLayout::class.java.isAssignableFrom(it)) }
    val constructor = viewClass.getConstructor(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType)
    val bind = viewClass.getDeclaredMethod(json.getString("bind"), type(json.getString("data"))).apply {
        check(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val bindingClass = type(json.getString("binding"))
    private val binding = viewClass.getDeclaredMethod("getBinding").apply {
        check(returnType == bindingClass && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val host = bindingClass.getDeclaredMethod(json.getString("host")).apply {
        check(returnType == type(CommentShortcutSymbolScanner.TITLE) && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    fun hostTitle(view: View): View = host.invoke(binding.invoke(view)) as View

    private val pageClass = type(CommentShortcutSymbolScanner.PAGE)
    private val commentsClass = type(CommentShortcutSymbolScanner.COMMENTS)
    private val activityClass = type(CommentShortcutSymbolScanner.ACTIVITY)
    val destroy = Activity::class.java.getDeclaredMethod("onDestroy").apply { isAccessible = true }
    private val page = type(CommentShortcutSymbolScanner.EXT).getDeclaredMethod(json.getString("page"), activityClass).apply {
        check(returnType == pageClass && Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val current = pageClass.getDeclaredMethod(json.getString("current")).apply {
        check(returnType == commentsClass && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val busy = commentsClass.getDeclaredMethod(json.getString("busy")).apply {
        check(returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val intentClass = type(json.getString("intent"))
    private val intent = intentClass.getConstructor(Boolean::class.javaPrimitiveType)
    private val refresh = commentsClass.getDeclaredMethod(json.getString("refresh"), intentClass).apply {
        check(returnType == Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val threadClass = type(CommentShortcutSymbolScanner.THREAD)
    private val thread = pageClass.getDeclaredMethod(json.getString("thread")).apply {
        check(returnType == threadClass && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val tid = threadClass.getMethod("getTid").apply { check(returnType == String::class.java) }
    private val wireThread = CommentProtoType("tbclient.ThreadInfo", cl)
    private val wireTid = wireThread.member("id", java.lang.Long::class.java)
    private val responseThreads = listOf("tbclient.PbPage.DataRes", "tbclient.PbFloor.DataRes").map {
        val response = type(it)
        response to response.getField("thread").apply { check(type == wireThread.type) }
    }
    fun responseThreadId(response: Any): String? {
        val field = responseThreads.singleOrNull { it.first.isInstance(response) }?.second ?: return null
        val value = field.get(response) ?: return null
        return wireTid.number(value)?.takeIf { it > 0 }?.toString()
    }
    data class Page(val owner: Activity, val tid: String, val comments: Any?)
    fun page(context: Context): Page? {
        var owner = context
        val seen = HashSet<Context>()
        while (!activityClass.isInstance(owner) && owner is ContextWrapper && seen.add(owner)) owner = owner.baseContext
        if (!activityClass.isInstance(owner)) return null
        val model = page.invoke(null, owner)
        val data = thread.invoke(model) ?: return null
        val id = (tid.invoke(data) as? String)?.takeIf { (it.toLongOrNull() ?: 0) > 0 } ?: return null
        return Page(owner as Activity, id, current.invoke(model))
    }
    fun canRefresh(page: Page): Boolean = page.comments?.let { busy.invoke(it) != true } == true
    fun refreshComments(comments: Any) { refresh.invoke(comments, intent.newInstance(true)) }

    /** Resolve the complete native resource family once during installation. */
    fun icons(context: Context): List<Drawable.ConstantState> = (1..18).map { level ->
        val name = CommentShortcutSymbolScanner.ICON_PREFIX + level
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        check(id != 0 && context.resources.getResourceEntryName(id) == name) { "Missing native level icon $level" }
        requireNotNull(context.getDrawable(id)?.constantState)
    }
}
