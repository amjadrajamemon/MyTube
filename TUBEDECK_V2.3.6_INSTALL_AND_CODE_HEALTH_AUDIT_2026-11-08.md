# TubeDeck v2.3.6 — Installability, Code Health and UX Audit
**Date:** 8 November 2026  
**Scope:** Feature branch `feature/tubedeck-six-experiences-20261105`; PR #33 (still draft). Android car tablets, phones, Android TV.  
**Evidence:** v2.3.5 published APK manifests/signatures and CI logs, v2.3.6 Gradle/manifest/workflow and selected Kotlin files, source-size inventory, regression tests and GitHub Actions run #42508478777.  
**Status:** Code corrections committed. Final emulator/device outcome must be read from the linked CI run; real OEM hardware remains unverified.

## 1. Why Android said “Package seems to be invalid”
**Observed:** The published v2.3.5 release includes ARM64 (117,368,073 bytes) and Universal (284,591,544 bytes) APKs with SHA-256 digests, complete ZIP archives and a valid APK **v2 signature**. Its CI checked signing and presence of ABIs, but it did **not** use `adb install`, verify the package manager's installation result, or cold-launch the packaged app. The signing tool reported v1=false, v2=true, v3=false.

**Do not claim root cause established:** A clean CI build is not evidence that a specific vehicle firmware accepts the package. Other possible causes: incomplete browser download, mismatched signature with an existing installation signed outside the pinned channel, unsupported OS API (minimum Android 7.0/API 24), insufficient storage, device ABI mismatch, installer/OEM quirks, or unsupported native library loading. We do not have a device-side PackageInstaller error code.

### Corrective changes committed in v2.3.6
1. `source/app/build.gradle.kts`: add a dedicated **32-bit ARMv7** flavor in addition to ARM64 and Universal, preserving application ID `com.aistudio.tubedeck.kxmvd` and increasing versionCode to 116.
2. Enable **v1 + v2 + v3 signatures** on the long-lived debug signing key, keeping certificate SHA1 pinned so existing compatible installations remain updateable.
3. Tighten GitHub Actions to verify package identity, manifest SDK/version, individual ABIs, ZIP alignment, JAR/v2/v3 signatures, and SHA-256 for all three variants.
4. New `ci/apk-install-smoke.sh`: real Android 11 emulator **clean install + reinstall + cold start + logcat**. This is a substantial improvement over ZIP-only checks, although it is **not** a test of all hardware.
5. Modernized `.github/scripts/apk_package_audit.py` to understand ARMv7, require legacy/modern signatures and use dynamic expected version inputs. Keep its report generation reusable for future releases.

### Installation validation still needed
- **OS matrix:** Android 7, 10/11, 13 and 15/16, including OEM firmware, 32-bit-only car head units and 16 KB page-size Android 15 hardware.
- **Upgrade matrix:** Install a genuine v2.3.5 APK first, then update to v2.3.6 without uninstalling or losing history/settings. Installing the same APK twice does not fully exercise old-version upgrade compatibility.
- **Actual download bytes:** Compare SHA-256 after transferring via phone browser, car browser or Local Beam. A truncated 270+ MiB Universal transfer may appear as an invalid package.
- **On failure:** Capture `adb install -r -t file.apk` failure output and `adb logcat -d | grep -i PackageManager`; this distinguishes CERTIFICATE, NO_MATCHING_ABIS, PARSE, VERSION_DOWNGRADE and STORAGE failures.
- **Never instruct users to uninstall without backing up** when app state or downloaded media matters.

## 2. Code architecture audit (from current source)
| Finding | Evidence | Risk | Recommendation |
|---|---|---|---|
| **Player is exceptionally monolithic** | `PlayerScreen.kt` ~6,581 lines; ~85 `remember` usages | Playback, next-video, PiP, resume, audio focus, buffering, gestures and UI can affect one another; hard to regression-test | Extract `PlaybackSessionController`, `QualitySwitchController`, `ResumeDecisionController`, TV focus coordinator and a pure presentation layer, in small tested PRs |
| **Activity coordinates too many concerns** | `MainActivity.kt` ~2,105 lines | Lifecycle, authentication, browser, downloads and playback may share mutable activity state | Move to lifecycle-aware view models and distinct coordinators; make navigation one-way |
| **Home/Search and Settings are still heavy** | `DriveDeckScreen.kt` ~1,790 lines, `SettingsScreen.kt` ~1,285 lines | Layout/IME logic and theme-specific branches create regression risk | Compose separate compact-search host, Home shelf, car-mode toolbar; group settings into Playback, Downloads, Appearance, Accessibility, Car/TV |
| **Repository handles wide functionality** | `TubeDeckRepository.kt` ~1,772 lines | Persistence, network, downloads and UI models are hard to test in isolation | Split into media/search/history/settings/download repositories with interfaces |
| **Historical search implementation remains** | `SearchAssistField.kt` ~16 KB alongside new `CompactSearchPanel.kt` | Duplicate UX paths and misleading maintenance surface | Confirm all references and remove or quarantine unused legacy search component and old compact policy, preserving tested TV focus behaviors |
| **Package footprint is very large** | Prior release ARM64 ~112 MiB, Universal ~271 MiB; prior forensic report lists substantial bundled FFmpeg, Python and Whisper assets | Download truncation, high installation storage, slow car-head-unit copying, higher failure incidence | Move optional transcription/dubbing assets to verified downloadable packs; measure ABI-specific dependencies and use compression/size budgets; **do not remove essential playback components blindly** |
| **Legacy smoke infrastructure has stale paths** | `ci/native-playback-smoke.sh` looks for an older `MyTube-v*-XYAUTO-UNIVERSAL.apk` filename | Useful runtime test isn't naturally runnable against current release | Update its artifact naming, isolate pure packaging smoke from longer media tests, run on emulator in a separate CI job |
| **Broad network setting** | `AndroidManifest.xml` currently sets `usesCleartextTraffic=true` | Enabling cleartext app-wide is broader than strictly necessary for YouTube HTTPS | Audit required local-LAN/OEM endpoints, then scope exceptions with a Network Security Config; treat as a separate compatibility-reviewed task |

**Balanced assessment:** A targeted scan of the seven largest Kotlin files did not find broad use of `GlobalScope` or `runBlocking`, which is positive. Large files alone are not proof of performance failure, and no device-level CPU, heap, frame timing or ANR profiles have been collected.

## 3. Playback and UX audit
- **Auto bitrate:** v2.3.5 fixed weak-network downshifts, but extracted single-bitrate URLs require re-preparation; true seamless adaptation is only possible when the source exposes a supported multi-representation manifest such as HLS/DASH. Log the actual selected height, switch cause, download throughput, buffer length and rebuffer count. Add throttled-network tests and prevent rapid quality oscillation. Maintain manual quality choice.
- **Resume intelligence:** Keep the default 3-second Resume / Start Over prompt adjustable from 1–15 s. Prompt only for meaningful, recent, unfinished progress with known duration; protect against resolver delays, video changes, process death and device reboot; instrument the dismissal/default path on Android TV D-pad.
- **Car landscape search:** On-demand single-row Home search, recent-search comma list, click-to-search and a bounded dialog are now present in source; real car firmware must verify keyboard focus, IME height, cutouts and portrait rotation.
- **Titles:** New centrally managed 15–24sp title scaling is preferable to scattered 12sp hardcodes; verify screenshots at system font scale 1.0/1.3/1.5 and contrast across all 19 themes.
- **Remote parity:** Add Compose UI tests for D-pad focus after modal dismiss, playback Next, mini-PiP close, cursor/air-mouse, keyboard navigation and theme changes.
- **Diagnostic capability:** Add opt-in **Playback Doctor** data export with non-sensitive error codes, selected quality and buffer changes; avoid including cookies/tokens/search history in default logs.

## 4. Prioritized next engineering changes
**P0 — Installation:** Finish the three-APK install/launch verification. If the emulator passes but a car still reports invalid, diagnose **that device's** PackageInstaller error and APK byte checksum instead of guessing.

**P1 — Reliability:** Add an automated **upgrade from the prior signed APK** test, Android 15/16 KB native ELF compatibility and a network-throttling playback test. Add crash/ANR baseline and startup-to-first-frame metrics.

**P1 — Code health:** Break apart PlayerScreen and MainActivity in small behavior-preserving PRs with tests; don't attempt a large rewrite in the same package-install hotfix.

**P2 — UI:** Test true landscape/portrait density on real car tablets, TV remote focus, compact recent searches, titles and theme accessibility. Remove duplicate legacy search code after reference checks.

**P2 — Package size:** Optionalize noncore ML models and heavy toolchains, keep offline playback intact, and track APK-size regressions per flavor.

**P3 — Publishing:** After real-device acceptance, promote the preview in a separate versioned release with proven signing key, store compliance, accessibility, privacy and user-facing migration notes.

## 5. Release acceptance criteria
A green GitHub build is **not** acceptance by itself. Require: all three APKs compile and pass ZIP/signature/manifest checks; one emulator can clean install, reinstall and cold-launch Universal; real ARM64 and a 32-bit car device install; previous-version upgrade preserves data; no startup crash/keyboard activation; Home content visible in landscape; video audio/display works; quality downshifts on throttled network; and the 3-second resume decision behaves correctly.

**Current CI:** https://github.com/amjadrajamemon/MyTube/actions/runs/42508478777  
**Draft PR:** https://github.com/amjadrajamemon/MyTube/pull/33  
**No production/main merge is implied by this report.**
