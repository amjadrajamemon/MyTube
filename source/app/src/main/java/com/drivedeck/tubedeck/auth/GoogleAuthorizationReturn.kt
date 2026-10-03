package com.drivedeck.tubedeck.auth

/** Android's result code does not replace the Google result/error carried by the Intent. */
internal fun <D : Any, T> parseGoogleAuthorizationReturn(
    data: D?,
    resultCode: Int,
    parse: (D) -> T
): T = parse(requireNotNull(data) {
    "Google authorization closed without a result (Android code $resultCode). Retry sign-in."
})
