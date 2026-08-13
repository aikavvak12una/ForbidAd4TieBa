package com.forbidad4tieba.hook.feature.ui

import android.view.View
import com.forbidad4tieba.hook.core.XposedCompat
import com.forbidad4tieba.hook.symbol.model.CommentAvatarDirectProfileSymbols

/**
 * 评论头像直达主页：帖子页楼层/评论区点击用户头像时跳过"吧友名片"弹窗，
 * 直接打开原生用户主页（与名片中"Ta的主页"一致）。
 *
 * 挂载点是评论楼层适配器的监听装配方法（22.9.1.0 的 `itd.S`、22.0.1.0 的 `w0d.R`），
 * 该方法以 `(PbCommenFloorItemViewHolder, int, PostData, View)` 签名在每次绑定楼层时
 * 设置头像点击监听。在其 after-hook 中把 `holder.p`（HeadImageView）、`holder.D`
 * （HeadPendantView）及其 `getHeadView()` 的点击监听替换为直达主页的监听；
 * 用户 id/名字在绑定时刻从 `PostData` 的用户元数据取一次，不依赖宿主资源 ID。
 *
 * 任何环节失败都 fail closed：不安装或运行时禁用，头像点击回落宿主原行为（名片弹窗）。
 */
object CommentAvatarDirectProfileHook {

    private const val TAG = "[CommentAvatarDirectProfileHook]"
    private const val PERSON_INFO_MSG_ID = 2002003

    @Volatile
    private var hooked = false

    @Volatile
    private var runtimeDisabled = false

    internal fun hook(targets: CommentAvatarDirectProfileSymbols) {
        val mod = XposedCompat.module ?: return
        if (!tryMarkHooked()) return
        try {
            mod.hook(targets.wireMethod).intercept { chain ->
                if (runtimeDisabled) return@intercept chain.proceed()
                val holder = chain.args.getOrNull(0)
                val postData = chain.args.getOrNull(2)
                val result = chain.proceed()
                if (holder != null && postData != null) {
                    try {
                        attachDirectProfileListeners(targets, holder, postData)
                    } catch (t: Throwable) {
                        runtimeDisabled = true
                        XposedCompat.log("$TAG listener attach FAILED, disabled: ${t.message}")
                        XposedCompat.log(t)
                    }
                }
                result
            }
            XposedCompat.log(
                "$TAG hook INSTALLED: ${targets.wireMethod.declaringClass.name}." +
                    "${targets.wireMethod.name}(PbCommenFloorItemViewHolder,int,PostData,View)",
            )
        } catch (t: Throwable) {
            resetHooked()
            XposedCompat.log("$TAG install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    private fun attachDirectProfileListeners(
        targets: CommentAvatarDirectProfileSymbols,
        holder: Any,
        postData: Any,
    ) {
        if (runtimeDisabled) return
        val meta = invokeOrNull { targets.postDataUserMethod.invoke(postData) } ?: return
        val userId = invokeOrNull { targets.getUserIdMethod.invoke(meta) } as? String
        val userName = invokeOrNull { targets.getUserNameMethod.invoke(meta) } as? String
        if (userId.isNullOrBlank()) return

        val headImage = invokeOrNull { targets.headImageField.get(holder) } as? View
        val pendant = invokeOrNull { targets.headPendantField.get(holder) } as? View
        val headView = if (pendant != null) {
            invokeOrNull { targets.headViewMethod.invoke(pendant) } as? View
        } else {
            null
        }
        val views = listOfNotNull(headImage, pendant, headView).distinct()
        if (views.isEmpty()) return

        val listener = DirectProfileClickListener(targets, userId, userName.orEmpty())
        views.forEach { view -> view.setOnClickListener(listener) }
        XposedCompat.logD { "$TAG swapped ${views.size} avatar listener(s), uid=$userId" }
    }

    private class DirectProfileClickListener(
        private val targets: CommentAvatarDirectProfileSymbols,
        private val userId: String,
        private val userName: String,
    ) : View.OnClickListener {

        override fun onClick(view: View) {
            if (runtimeDisabled) return
            try {
                val context = view.context ?: return
                val config = targets.personInfoConfigConstructor.newInstance(context, userId, userName)
                val manager = targets.messageManagerGetInstanceMethod.invoke(null)
                val message = targets.customMessageConstructor.newInstance(PERSON_INFO_MSG_ID, config)
                targets.messageManagerSendMethod.invoke(manager, message)
                XposedCompat.logD { "$TAG direct profile jump: uid=$userId" }
            } catch (t: Throwable) {
                runtimeDisabled = true
                XposedCompat.log("$TAG click FAILED, disabled for this process: ${t.message}")
                XposedCompat.log(t)
            }
        }
    }

    private inline fun <T> invokeOrNull(block: () -> T): T? {
        return try {
            block()
        } catch (_: Throwable) {
            null
        }
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
}
