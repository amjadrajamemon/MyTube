# MyTube — First-stage package and theme/UI audit
Date: 2026-11-03
Scope requested: inspect the actual APK package and correct theme/UI defects first. Security, licensing and publishing-policy reviews intentionally deferred.

## A. Actual published APK baseline
Release tag: [v2.0.0-modern-tv-suite-20261103](https://github.com/amjadrajamemon/MyTube/releases/tag/v2.0.0-modern-tv-suite-20261103)
Published binaries inspected by [dedicated forensic run #41617842919](https://github.com/amjadrajamemon/MyTube/actions/runs/41617842919) using sha256sum, ZIP CRC, Android aapt2, zipalign and apksigner.
- ARM64 APK: 111.77 MiB; arm64-v8a native libraries; sha256 625fd29ff2a0e72a482fbe7cdf360317d3fa9398aced073da6407255629488cd
- Universal APK: 271.24 MiB; arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries; sha256 f11acf7980652761861c8787c882f6524ba5aec662c9764ef2e1a1e8f0fd9842
- Both: Android applicationId com.aistudio.tubedeck.kxmvd, versionCode 103, versionName 2.0.0, minSdk 24, targetSdk 36.
- Both: release SHA256SUMS comparison, ZIP CRC, zipalign, Android manifest parse, apksigner verification and legacy update certificate check **PASS**.
- Size drivers (ARM64 compressed): native libraries 55.74 MiB, assets/models 30.98 MiB, dex code 18.29 MiB, resources 6.55 MiB. Universal native libraries occupy ~215.21 MiB because of multiple ABIs.
- Largest ARM64 entries: FFmpeg library archive 33.8 MiB, multilingual Whisper tiny model 30.66 MiB, Python library archive 13.4 MiB.
- This validates file integrity and structure, not real-device decoding, installation, UI, memory or transport behavior.

## B. Concrete UI issues found and corrections staged in PR #32
1. **Top navigation overlaps status bar/notches:** Top-mounted theme toolbar now applies safeDrawing top and horizontal display-cutout padding. Browser search toolbar avoids applying the same status inset twice.
2. **History/Relevant/Trending headers wrap across two lines:** Full-width single-line headings now have dedicated first row, with scroll and refresh/clear icons on a separate controls row. Ellipsis remains a last-resort fallback on exceptionally constrained widths.
3. **Three columns squeezed on narrow devices:** Responsive BoxWithConstraints + FlowRow retains three columns when available width is at least 570dp; otherwise all three feed sections remain accessible as full-width stacked shelves. This does not remove feeds.
4. **Color-only redundant themes:** Catalog now defines independent Home presentation, navigation placement and visual style, card geometry, player chrome, font family/density and interaction motion. All 10 profiles have distinct structural signatures.
5. **Theme-specific search and app header:** Desktop command theme places search, application and account actions on a shared horizontal toolbar; minimal theme prioritizes search and moves secondary actions to a compact row; other themes use streaming headers.
6. **Theme-specific navigation:** Side rail, top command row, compact, icon-only, minimal and pill presentation, with theme-dependent icon sets, sizing, alignment and animated selection.
7. **Theme-specific player:** Control shape, spinner shell, play/pause button geometry, overlay opacity, compact HUD control header and motion behavior follow the active theme profile.
8. **Animation differentiation:** Instant, snap, glide and pulse transitions, with no idle animation loop.

## C. Functional preservation and quality gates
- Preserve playback engine, existing blackout/restore behavior, fixed spinner-center offset, Next/Previous remote controls, three Home feeds, PiP and separate ARM64/Universal build flavors.
- Unit test added to assert 10 distinct layouts/control signatures, all Home presentation modes and all navigation/motion modes.
- [Current branch automated build](https://github.com/amjadrajamemon/MyTube/actions/workflows/core-fixes-apk.yml) must complete both variant tests, Android lint, APK assembly, signer/ABI verification and artifact upload before changes are considered package-ready.
- Screenshots and physical acceptance required on Android TV, generic AOSP box with 16:9 and asymmetric cutout, portrait/landscape phone, remote D-pad, air mouse and keyboard; test each theme's Home, browser, settings, player overlays, navigation buttons, focus, and text scaling.
- No released binary has been overwritten and draft PR #32 has not been merged. For any later new release, use a fresh versionCode and non-overwriting version tag rather than replacing v2.0.0.

## D. Open limitations
- The package forensic workflow cannot prove installed runtime behavior or actual notch avoidance on every OEM firmware.
- 10 theme signatures now differ structurally, but user perception, balance of controls, typography, fullscreen and each UI density still require screenshot/manual testing.
- Production release promotion is not part of this first-stage audit.
