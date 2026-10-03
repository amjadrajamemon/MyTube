# MyTube OAuth registration — v1.4.5

Project: `metal-scholar-510509-b2`

| Registration | Value |
| --- | --- |
| Android client ID | `452637572013-pr2nnh5tm0hfvnlf7bqij95tnkk6ba5t.apps.googleusercontent.com` |
| Application ID | `com.aistudio.tubedeck.kxmvd` |
| Signing SHA-1 | `16:00:0A:DB:6C:43:BD:9D:58:DA:29:EB:50:89:14:50:A8:70:94:0F` |
| Web client ID | `452637572013-j7n0rvi7bkgtei3c979tr004h4da31nh.apps.googleusercontent.com` |

The user supplied the two client registrations. The JSON exports confirm the same project; the Android export does not contain package/certificate fields, so confirm those values in Google Cloud.

The public Web client ID lives in gradle.properties and is compiled into both editions. The Web client secret is not needed and must not be added to source, CI or the APK. Android authorization resolves the registered Android client from the application ID and signing certificate; its client ID is not passed as the Credential Manager server client ID.

## Google Cloud checklist

- Enable YouTube Data API v3 in this project.
- Configure Google Auth Platform branding and audience.
- For External / Testing, add each account used for testing under Audience > Test users.
- Configure the requested YouTube read-only scope: https://www.googleapis.com/auth/youtube.readonly.
- For wider distribution, satisfy Google's applicable consent-screen and verification requirements.

## Verification still required on a device

Fresh install: use Sign in with Google, select an account and grant YouTube read-only access. Confirm Google + YouTube connected, then test switching Web/Exo modes, restart and sign-out. Test cancellation and permission denial without reporting YouTube-connected state.

CI validates compilation, tests, lint, embedded client ID and pinned signing identity; it cannot approve the user's Google consent or establish that Cloud-side configuration is complete.

Web playback uses MyTube's shared app account state. OAuth tokens do not sign the YouTube website into a WebView cookie session.

References:
- https://codelabs.developers.google.com/sign-in-with-google-android
- https://developers.google.com/youtube/v3/getting-started
- https://developers.google.com/identity/protocols/oauth2/limited-input-device
