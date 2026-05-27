package com.forbidad4tieba.hook.feature.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.forbidad4tieba.hook.HookSymbols
import com.forbidad4tieba.hook.config.ConfigManager
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.utils.ReflectionUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

object PbLikeAutoReplyHook {
    private const val TAG = "[PbLikeAutoReplyHook]"
    private const val AGREE_TYPE_LIKE = 2
    private const val MAX_INPUT_SEARCH_DEPTH = 10
    private const val MAX_INPUT_SEARCH_NODES = 220
    private const val MIN_TRIGGER_INTERVAL_MS = 1200L
    private const val SEND_DELAY_MS = 80L
    private const val SEND_AFTER_TEXT_DELAY_MS = 80L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val triggerDebounce = Collections.synchronizedMap(WeakHashMap<Any, Long>())

    @Volatile private var hooked = false
    @Volatile private var runtimeDisabled = false

    fun hook(cl: ClassLoader, symbols: HookSymbols) {
        if (!ConfigManager.isPbLikeAutoReplyEnabled) {
            XposedCompat.log("$TAG skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return

        try {
            val agreeViewClass = resolveClass(cl, symbols.pbLikeAutoReplyAgreeViewClass, "agreeViewClass")
            val inputContainerClass = resolveClass(cl, symbols.pbLikeAutoReplyInputContainerClass, "inputContainerClass")
            val agreeClickMethod = resolveAgreeClickMethod(
                agreeViewClass,
                symbols.pbLikeAutoReplyAgreeClickMethod,
            )
            val getDataMethod = resolveNoArgMethod(
                agreeViewClass,
                symbols.pbLikeAutoReplyAgreeViewGetDataMethod,
                "agreeViewGetDataMethod",
            )
            val agreeDataClass = resolveClass(cl, symbols.pbLikeAutoReplyAgreeDataClass, "agreeDataClass")
            if (!agreeDataClass.isAssignableFrom(getDataMethod.returnType)) {
                error("agreeViewGetDataMethod return mismatch: ${getDataMethod.returnType.name}")
            }
            val hasAgreeField = resolveField(agreeDataClass, symbols.pbLikeAutoReplyAgreeDataHasAgreeField, "hasAgreeField")
            val agreeTypeField = resolveField(agreeDataClass, symbols.pbLikeAutoReplyAgreeDataAgreeTypeField, "agreeTypeField")
            val isInThreadField = resolveField(
                agreeDataClass,
                symbols.pbLikeAutoReplyAgreeDataIsInThreadField,
                "isInThreadField",
            )
            val getInputViewMethod = resolveNoArgMethod(
                inputContainerClass,
                symbols.pbLikeAutoReplyInputContainerGetInputViewMethod,
                "inputContainerGetInputViewMethod",
            )
            if (
                !EditText::class.java.isAssignableFrom(getInputViewMethod.returnType) &&
                !View::class.java.isAssignableFrom(getInputViewMethod.returnType)
            ) {
                error("inputContainerGetInputViewMethod return mismatch: ${getInputViewMethod.returnType.name}")
            }
            val getSendViewMethod = resolveNoArgMethod(
                inputContainerClass,
                symbols.pbLikeAutoReplyInputContainerGetSendViewMethod,
                "inputContainerGetSendViewMethod",
            )
            if (!View::class.java.isAssignableFrom(getSendViewMethod.returnType)) {
                error("inputContainerGetSendViewMethod return mismatch: ${getSendViewMethod.returnType.name}")
            }

            mod.hook(agreeClickMethod).intercept { chain ->
                val agreeView = chain.thisObject
                val clicked = chain.args.getOrNull(0) as? View
                val replyText = ConfigManager.pbLikeAutoReplyText
                var beforeKnown = false
                var wasSelected = false
                var isThread = false
                if (
                    ConfigManager.isPbLikeAutoReplyEnabled &&
                    !runtimeDisabled &&
                    replyText.isNotBlank() &&
                    agreeView != null &&
                    clicked != null
                ) {
                    try {
                        val agreeData = getDataMethod.invoke(agreeView)
                        isThread = isThreadLike(agreeData, isInThreadField)
                        wasSelected = isSelectedLike(
                            data = agreeData,
                            hasAgreeField = hasAgreeField,
                            agreeTypeField = agreeTypeField,
                        )
                        beforeKnown = true
                    } catch (t: Throwable) {
                        beforeKnown = false
                        XposedCompat.logD { "$TAG skip click: before state unavailable: ${t.message}" }
                    }
                }

                val result = chain.proceed()
                if (agreeView == null || clicked == null || !beforeKnown) {
                    return@intercept result
                }
                try {
                    if (
                        !ConfigManager.isPbLikeAutoReplyEnabled ||
                        runtimeDisabled ||
                        replyText.isBlank() ||
                        !isThread ||
                        wasSelected
                    ) {
                        return@intercept result
                    }
                    val agreeData = getDataMethod.invoke(agreeView)
                    if (
                        isThreadLike(agreeData, isInThreadField) &&
                        isSelectedLike(agreeData, hasAgreeField, agreeTypeField) &&
                        markTriggered(agreeData ?: agreeView)
                    ) {
                        scheduleSendPresetReply(
                            clicked = clicked,
                            replyText = replyText,
                            inputContainerClass = inputContainerClass,
                            getInputViewMethod = getInputViewMethod,
                            getSendViewMethod = getSendViewMethod,
                        )
                    }
                } catch (t: Throwable) {
                    runtimeDisabled = true
                    XposedCompat.log("$TAG runtime FAILED, disabled for this process: ${t.message}")
                    XposedCompat.log(t)
                }
                result
            }
            XposedCompat.log("$TAG hook INSTALLED: ${agreeViewClass.name}.${agreeClickMethod.name}(View)")
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun scheduleSendPresetReply(
        clicked: View,
        replyText: String,
        inputContainerClass: Class<*>,
        getInputViewMethod: Method,
        getSendViewMethod: Method,
    ) {
        mainHandler.postDelayed({
            try {
                sendPresetReply(
                    clicked = clicked,
                    replyText = replyText,
                    inputContainerClass = inputContainerClass,
                    getInputViewMethod = getInputViewMethod,
                    getSendViewMethod = getSendViewMethod,
                )
            } catch (t: Throwable) {
                runtimeDisabled = true
                XposedCompat.log("$TAG send FAILED, disabled for this process: ${t.message}")
                XposedCompat.log(t)
            }
        }, SEND_DELAY_MS)
    }

    private fun sendPresetReply(
        clicked: View,
        replyText: String,
        inputContainerClass: Class<*>,
        getInputViewMethod: Method,
        getSendViewMethod: Method,
    ) {
        val normalizedText = replyText.trim()
        if (!ConfigManager.isPbLikeAutoReplyEnabled || runtimeDisabled || normalizedText.isBlank()) return
        val activity = ReflectionUtils.findActivityFromContext(clicked.context) ?: return
        val inputContainer = findInputContainer(
            activity = activity,
            inputContainerClass = inputContainerClass,
            getInputViewMethod = getInputViewMethod,
            getSendViewMethod = getSendViewMethod,
        ) ?: return
        val inputView = getInputViewMethod.invoke(inputContainer) as? EditText ?: return
        if (inputView.text?.toString() != normalizedText) {
            inputView.setText(normalizedText)
            inputView.setSelection(inputView.text?.length ?: 0)
        }
        val sendView = getSendViewMethod.invoke(inputContainer) as? View ?: return
        inputView.postDelayed({
            try {
                if (!ConfigManager.isPbLikeAutoReplyEnabled || runtimeDisabled) return@postDelayed
                if (!sendView.isEnabled) return@postDelayed
                sendView.performClick()
                XposedCompat.logD { "$TAG sent preset reply after thread like" }
            } catch (t: Throwable) {
                runtimeDisabled = true
                XposedCompat.log("$TAG send click FAILED, disabled for this process: ${t.message}")
                XposedCompat.log(t)
            }
        }, SEND_AFTER_TEXT_DELAY_MS)
    }

    private fun findInputContainer(
        activity: Activity,
        inputContainerClass: Class<*>,
        getInputViewMethod: Method,
        getSendViewMethod: Method,
    ): Any? {
        val root = activity.window?.decorView ?: return null
        val queue = java.util.ArrayDeque<SearchNode>()
        queue.add(SearchNode(root, 0))
        var visited = 0
        var attachedCandidate: Any? = null
        while (!queue.isEmpty() && visited < MAX_INPUT_SEARCH_NODES) {
            val node = queue.removeFirst()
            visited++
            val view = node.view
            if (inputContainerClass.isInstance(view)) {
                val input = getInputViewMethod.invoke(view) as? EditText
                val send = getSendViewMethod.invoke(view) as? View
                if (input != null && send != null) {
                    if (view.visibility == View.VISIBLE && view.isShown) return view
                    if (attachedCandidate == null && view.windowToken != null) {
                        attachedCandidate = view
                    }
                }
            }
            if (node.depth >= MAX_INPUT_SEARCH_DEPTH || view !is ViewGroup) continue
            for (index in view.childCount - 1 downTo 0) {
                queue.addLast(SearchNode(view.getChildAt(index), node.depth + 1))
            }
        }
        return attachedCandidate
    }

    private fun isSelectedLike(data: Any?, hasAgreeField: Field, agreeTypeField: Field): Boolean {
        data ?: return false
        val hasAgree = hasAgreeField.get(data) as? Boolean ?: return false
        val agreeType = (agreeTypeField.get(data) as? Number)?.toInt() ?: return false
        return hasAgree && agreeType == AGREE_TYPE_LIKE
    }

    private fun isThreadLike(data: Any?, isInThreadField: Field): Boolean {
        data ?: return false
        return isInThreadField.get(data) as? Boolean ?: false
    }

    private fun markTriggered(key: Any): Boolean {
        val now = SystemClock.uptimeMillis()
        synchronized(triggerDebounce) {
            val last = triggerDebounce[key] ?: 0L
            if (now - last < MIN_TRIGGER_INTERVAL_MS) return false
            triggerDebounce[key] = now
            return true
        }
    }

    private fun resolveClass(cl: ClassLoader, className: String?, label: String): Class<*> {
        val name = className?.takeIf { it.isNotBlank() } ?: error("missing $label")
        return XposedCompat.findClassOrNull(name, cl) ?: error("class not found: $name")
    }

    private fun resolveAgreeClickMethod(clazz: Class<*>, methodName: String?): Method {
        val name = methodName?.takeIf { it.isNotBlank() } ?: error("missing agreeClickMethod")
        return clazz.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.name == name &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == View::class.java
        }?.apply { isAccessible = true } ?: error("method not found: ${clazz.name}.$name(View)")
    }

    private fun resolveNoArgMethod(clazz: Class<*>, methodName: String?, label: String): Method {
        val name = methodName?.takeIf { it.isNotBlank() } ?: error("missing $label")
        return clazz.methods.firstOrNull { method ->
            method.name == name && method.parameterTypes.isEmpty()
        }?.apply { isAccessible = true } ?: error("method not found: ${clazz.name}.$name()")
    }

    private fun resolveField(clazz: Class<*>, fieldName: String?, label: String): Field {
        val name = fieldName?.takeIf { it.isNotBlank() } ?: error("missing $label")
        return XposedCompat.findField(clazz, name)
    }

    private fun tryMarkHooked(): Boolean {
        synchronized(this) {
            if (hooked) return false
            hooked = true
            runtimeDisabled = false
            return true
        }
    }

    private fun resetHooked() {
        synchronized(this) { hooked = false }
    }

    private data class SearchNode(
        val view: View,
        val depth: Int,
    )
}
