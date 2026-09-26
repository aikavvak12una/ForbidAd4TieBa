package com.forbidad4tieba.hook.ui.about

import com.forbidad4tieba.hook.core.XposedCompat
import org.json.JSONObject

internal data class AboutPayload(
    val items: List<AboutItem>,
    val telemetry: List<TelemetryConfig>,
    val controls: RemoteControls,
)

internal object AboutPayloadParser {
    fun parse(payload: String, warn: (String) -> Unit = XposedCompat::logW): AboutPayload? {
        try {
            val root = JSONObject(payload)
            val schema = root.optInt("schema", -1)
            if (schema != 1) {
                warn("[AboutInfo] parse failed: schema=$schema")
                return null
            }

            val itemsArray = root.optJSONArray("items")
            if (itemsArray == null || itemsArray.length() == 0) {
                warn("[AboutInfo] parse failed: empty items")
                return null
            }

            val parsed = ArrayList<AboutItem>(itemsArray.length())
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i)
                if (item == null) {
                    warn("[AboutInfo] parse failed: items[$i] is not object")
                    return null
                }

                val title = item.optString("title", "").trim()
                val description = item.optString("description", "").trim()
                if (title.isEmpty() || description.isEmpty()) {
                    warn("[AboutInfo] parse failed: items[$i] title/description empty")
                    return null
                }

                val hasLink = item.optBoolean("hasLink", false)
                val itemUrl = item.optString("url", "").trim().ifEmpty { null }
                if (hasLink && (itemUrl == null || !isHttpOrHttpsUrl(itemUrl))) {
                    warn("[AboutInfo] parse failed: items[$i] invalid url")
                    return null
                }

                parsed.add(
                    AboutItem(
                        title = title,
                        description = description,
                        url = if (hasLink) itemUrl else null,
                    )
                )
            }
            return AboutPayload(
                items = parsed,
                telemetry = AboutTelemetry.parseConfig(root),
                controls = RemoteControlPolicy.parse(root),
            )
        } catch (t: Throwable) {
            warn("[AboutInfo] parse exception: ${t.message}")
            return null
        }
    }
}
