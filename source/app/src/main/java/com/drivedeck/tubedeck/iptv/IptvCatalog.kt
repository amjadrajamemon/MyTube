package com.drivedeck.tubedeck.iptv

import android.net.Uri
import org.json.JSONArray
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.io.ByteArrayOutputStream

/** Private, user-provided IPTV sources only. The application contains no channel bundles. */
data class IptvChannel(
    val name: String,
    val url: String,
    val group: String = "Other",
    val logo: String = "",
    val tvgId: String = ""
)

data class IptvPalette(
    val name: String,
    val background: Long,
    val surface: Long,
    val accent: Long,
    val foreground: Long,
    val wallpaper: String
)

/** Original artwork/color recipes, rather than copied assets from commercial players. */
object IptvPalettes {
    val all = listOf(
        IptvPalette("Ocean Glass", 0xFF061C32L, 0xFF102A42L, 0xFF14C8F5L, 0xFFF0FAFFL, "Waves"),
        IptvPalette("Midnight Cinema", 0xFF0B0D18L, 0xFF1B2032L, 0xFFBEA3FFL, 0xFFF8F6FFL, "Spotlight"),
        IptvPalette("Aurora Borealis", 0xFF05251FL, 0xFF123F39L, 0xFF54F1A9L, 0xFFE8FFF6L, "Aurora"),
        IptvPalette("Ruby Theatre", 0xFF290B15L, 0xFF461B2CL, 0xFFFF6380L, 0xFFFFEFF3L, "Spotlight"),
        IptvPalette("Royal Sapphire", 0xFF0E1742L, 0xFF1C2C63L, 0xFF65A7FFL, 0xFFF2F5FFL, "Waves"),
        IptvPalette("Golden Lounge", 0xFF252012L, 0xFF3F3520L, 0xFFF9CF69L, 0xFFFFF8E5L, "Glow"),
        IptvPalette("Forest Retreat", 0xFF10251BL, 0xFF203A2BL, 0xFF9EE38AL, 0xFFF2FFF0L, "Aurora"),
        IptvPalette("Arctic Frost", 0xFFDBEBF4L, 0xFFEDF6FBL, 0xFF126B9AL, 0xFF102F40L, "Waves"),
        IptvPalette("Cyber Neon", 0xFF110B28L, 0xFF27153FL, 0xFFFF5CDAL, 0xFFF7EEFFL, "Grid"),
        IptvPalette("Sunset Ember", 0xFF321622L, 0xFF543027L, 0xFFFFAC5BL, 0xFFFFF5EFL, "Glow")
    )
    fun byName(value: String) = all.firstOrNull { it.name == value } ?: all.first()
}

object IptvCatalog {
    private val attribute = Regex("([A-Za-z0-9-]+)\\s*=\\s*\"([^\"]*)\"")

    fun parseM3u(text: String, playlistUrl: String = ""): List<IptvChannel> {
        require(text.length <= 4_000_000) { "Playlist exceeds the 4 MB safety limit" }
        val found = ArrayList<IptvChannel>()
        var name = ""
        var group = "Other"
        var logo = ""
        var tvgId = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = line.substringAfter(",", "").trim().ifBlank { attrs["tvg-name"].orEmpty() }
                    group = attrs["group-title"].orEmpty().ifBlank { "Other" }
                    logo = attrs["tvg-logo"].orEmpty()
                    tvgId = attrs["tvg-id"].orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(":").trim()
                line.isNotBlank() && !line.startsWith("#") -> {
                    val target = resolveStream(playlistUrl, line)
                    if (target != null) {
                        found += IptvChannel(name.ifBlank { "Channel " + (found.size + 1) }, target,
                            group.take(90), logo, tvgId)
                        if (found.size == 8000) break
                    }
                    name = ""
                    group = "Other"
                    logo = ""
                    tvgId = ""
                }
            }
        }
        return found
    }

    /** Only schemes supported by the IPTV player are accepted; no file/javascript URLs. */
    fun resolveStream(base: String, item: String): String? = runCatching {
        val url = if (base.isNotBlank()) URI(base).resolve(item.trim()) else URI(item.trim())
        url.toString().takeIf {
            url.scheme?.lowercase() in setOf("https", "http", "rtsp") &&
                url.host != null && it.length < 4096
        }
    }.getOrNull()

    /** Xtream API response: live channels only, with credentials kept in memory. */
    fun parseXtreamLive(body: String, server: String, user: String, password: String): List<IptvChannel> {
        require(body.length < 4_000_000) { "Provider response is too large" }
        val root = JSONArray(body)
        val safeServer = server.trimEnd('/')
        val username = Uri.encode(user)
        val secret = Uri.encode(password)
        val results = ArrayList<IptvChannel>()
        for (index in 0 until minOf(root.length(), 8000)) {
            val item = root.optJSONObject(index) ?: continue
            val id = item.optString("stream_id")
            if (!id.all { it.isDigit() } || id.isBlank()) continue
            val stream = safeServer + "/live/" + username + "/" + secret + "/" + id + ".ts"
            if (resolveStream("", stream) == null) continue
            results += IptvChannel(
                item.optString("name").ifBlank { "Live " + id },
                stream,
                item.optString("category_id").ifBlank { "Live TV" },
                item.optString("stream_icon"),
                id
            )
        }
        return results
    }

    fun xtreamApi(server: String, user: String, password: String): String {
        val root = server.trim().trimEnd('/')
        require(resolveStream("", root) != null && !root.startsWith("rtsp:")) {
            "Use a valid HTTPS or HTTP provider server URL"
        }
        require(user.isNotBlank() && password.isNotBlank()) { "Username and password required" }
        return root + "/player_api.php?username=" + Uri.encode(user) +
            "&password=" + Uri.encode(password) + "&action=get_live_streams"
    }

    /** Bounded network access; callers must dispatch on Dispatchers.IO. */
    fun downloadText(address: String): String {
        require(resolveStream("", address) != null && !address.startsWith("rtsp:", true)) {
            "Only HTTP(S) playlist and API URLs are supported"
        }
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 18_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json, application/x-mpegURL, text/plain, */*")
        try {
            require(conn.responseCode in 200..299) { "Provider returned HTTP " + conn.responseCode }
            val output = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(16_384)
                var amount = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    amount += count
                    require(amount <= 4_000_000) { "Source exceeds the 4 MB safety limit" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            conn.disconnect()
        }
    }
}
