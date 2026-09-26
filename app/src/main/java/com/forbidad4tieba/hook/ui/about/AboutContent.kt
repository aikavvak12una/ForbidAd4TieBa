package com.forbidad4tieba.hook.ui.about

import android.net.Uri
import java.util.Locale

data class AboutItem(
    val title: String,
    val description: String,
    val url: String?,
)
data class RemoteCustomDialog(
    val id: String,
    val revision: Int,
    val title: String,
    val message: String,
    val urlButton: RemoteCustomDialogUrlButton?,
) {
    val ackKey: String
        get() = "$id:$revision"
}

data class RemoteCustomDialogUrlButton(
    val text: String,
    val url: String,
)

internal fun isHttpOrHttpsUrl(url: String): Boolean {
    return try {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Throwable) {
        false
    }
}
