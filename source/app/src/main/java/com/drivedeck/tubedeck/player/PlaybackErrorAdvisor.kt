package com.drivedeck.tubedeck.player

/** Human-readable failures; no hidden endless retries or background probes. */
internal object PlaybackErrorAdvisor {
    enum class Cause { NETWORK, RATE_LIMIT, EXPIRED_STREAM, DECODER, RESTRICTED, MISSING_FILE, UNKNOWN }
    data class Advice(val cause: Cause, val message: String, val suggestion: String)

    fun analyze(error: String): Advice {
        val text = error.lowercase(java.util.Locale.ROOT)
        return when {
            listOf("429", "too many requests", "rate limit").any(text::contains) ->
                Advice(Cause.RATE_LIMIT, "YouTube temporarily limited requests.",
                    "Try again later or open the web player; repeated rapid retries can extend the limit.")
            listOf("offline", "no network", "unknownhost", "dns", "connection refused", "unreachable", "timeout").any(text::contains) ->
                Advice(Cause.NETWORK, "The connection to the video service failed.",
                    "Check Wi-Fi or Ethernet, then tap Retry. Existing downloaded videos remain usable.")
            listOf("403", "signature", "expired", "invalid response").any(text::contains) ->
                Advice(Cause.EXPIRED_STREAM, "The video link may have expired.",
                    "Retry to refresh the source; if this fails, try the web player.")
            listOf("codec", "decoder", "format unsupported", "mediacodec").any(text::contains) ->
                Advice(Cause.DECODER, "This device could not decode the selected format.",
                    "Try a lower resolution or the web player, especially on older TV boxes.")
            listOf("private video", "age-restricted", "not available", "unavailable in your country", "login required").any(text::contains) ->
                Advice(Cause.RESTRICTED, "The source reports that this video is restricted or unavailable.",
                    "Use the web player if your account is permitted to view it.")
            listOf("file is missing", "no such file", "enoent").any(text::contains) ->
                Advice(Cause.MISSING_FILE, "The downloaded video file is missing.",
                    "Reconnect external storage or download the file again.")
            else -> Advice(Cause.UNKNOWN, "MyTube could not start this video.",
                "Try refreshing the stream, or use the web player if available.")
        }
    }
}
