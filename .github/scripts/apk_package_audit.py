#!/usr/bin/env python3
"""Inspect installable APK bytes (not just source). No device-runtime claims."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

def run(args):
    process = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return process.returncode == 0, process.stdout.strip()

def size_mb(amount):
    return round(amount / (1024 * 1024), 2)

def audit(apk, args):
    problems = []
    notes = []
    size = apk.stat().st_size
    digest = hashlib.sha256()
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    result = {"file": apk.name, "size_bytes": size, "sha256": digest.hexdigest()}
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
        duplicate = [name for name, n in collections.Counter(e.filename for e in entries).items() if n > 1]
        if duplicate:
            problems.append("Duplicate ZIP entry names: " + ", ".join(duplicate[:8]))
        crc_failure = archive.testzip()
        if crc_failure:
            problems.append("ZIP CRC failure: " + crc_failure)
        names = {e.filename for e in entries}
        for required in ("AndroidManifest.xml", "resources.arsc", "classes.dex"):
            if required not in names:
                problems.append("Missing mandatory APK entry " + required)
        sofiles = [e for e in entries if e.filename.startswith("lib/") and e.filename.endswith(".so")]
        abis = collections.Counter(e.filename.split("/")[1] for e in sofiles)
        expected = ("arm64-v8a",) if "ARM64" in apk.name else ("arm64-v8a", "armeabi-v7a")
        for abi in expected:
            if abi not in abis:
                problems.append("Missing required native ABI: " + abi)
        if "ARM64" in apk.name and any(abi != "arm64-v8a" for abi in abis):
            problems.append("ARM64-only APK contains additional native ABIs")
        if "Universal" in apk.name and ("x86" not in abis or "x86_64" not in abis):
            notes.append("Universal APK has no native libraries for every x86 ABI; verify actual supported CPUs")
        groups = collections.Counter()
        for e in entries:
            if e.filename.startswith("lib/"):
                group = "Native .so libraries"
            elif e.filename.endswith(".dex"):
                group = "Dex bytecode"
            elif e.filename.startswith("assets/"):
                group = "Assets and offline models"
            elif e.filename.startswith("res/") or e.filename == "resources.arsc":
                group = "Android resources"
            else:
                group = "Other APK entries"
            groups[group] += e.compress_size
        top = sorted(entries, key=lambda e: e.compress_size, reverse=True)[:12]
        result.update({
            "zip_entry_count": len(entries), "native_abis": dict(abis),
            "category_compressed_mib": {k: size_mb(v) for k,v in groups.items()},
            "largest_entries": [{"name": e.filename, "compressed_mib": size_mb(e.compress_size),
                                  "original_mib": size_mb(e.file_size)} for e in top]
        })
    good, data = run([args.apksigner, "verify", "--verbose", "--print-certs", str(apk)])
    if not good:
        problems.append("APK signature verification failed: " + data[-250:])
    result["signature_excerpt"] = [line.strip() for line in data.splitlines()
        if ("Signer #1 certificate" in line or "Verified using" in line)][:15]
    expected_sha1 = re.sub("[^0-9a-f]", "", args.sha1_file.read_text().lower())
    found = re.search(r"Signer #1 certificate SHA-1 digest:\s*([0-9a-f:]+)", data, re.I)
    if not found:
        problems.append("Unable to read signing certificate SHA-1 from apksigner")
    elif re.sub("[^0-9a-f]", "", found.group(1).lower()) != expected_sha1:
        problems.append("APK signing certificate SHA-1 differs from expected update identity")
    else:
        notes.append("Signing certificate matches the existing update channel")
    good, data = run([args.aapt2, "dump", "badging", str(apk)])
    if not good:
        problems.append("AAPT2 manifest/badging validation failed: " + data[-500:])
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", data)
    min_sdk = re.search(r"(?:minSdkVersion|sdkVersion):\s*'([^']+)'", data)
    target_sdk = re.search(r"targetSdkVersion:'([^']+)'", data)
    result["package"] = package.groups() if package else None
    result["min_sdk"] = min_sdk.group(1) if min_sdk else None
    result["target_sdk"] = target_sdk.group(1) if target_sdk else None
    if not package or package.groups() != ("com.aistudio.tubedeck.kxmvd", "103", "2.0.0"):
        problems.append("Unexpected package name or v2.0.0 manifest version")
    if not min_sdk or min_sdk.group(1) != "24":
        problems.append("Unexpected minSdk (expected Android API 24)")
    if not target_sdk or target_sdk.group(1) != "36":
        problems.append("Unexpected targetSdk (expected API 36)")
    good, data = run([args.zipalign, "-c", "-v", "4", str(apk)])
    if not good:
        problems.append("APK ZIP alignment check failed: " + data[-250:])
    result["zipalign_passed"] = good
    result["issues"] = problems
    result["notes"] = notes
    result["package_audit_passed"] = not problems
    return result

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--aapt2", required=True)
    p.add_argument("--apksigner", required=True)
    p.add_argument("--zipalign", required=True)
    p.add_argument("--sha1-file", type=Path, required=True)
    p.add_argument("--out-dir", type=Path, required=True)
    p.add_argument("apks", nargs="+", type=Path)
    args = p.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    results = [audit(path, args) for path in args.apks]
    (args.out_dir / "apk-package-audit.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    lines = ["# MyTube v2.0.0 — Published APK Package Forensics", "",
        "Scope: downloaded release bytes; manifest, ABI, ZIP/CRC, signing, hash, size and resource contents.",
        "Not covered: actual device playback, UI, installer upgrade paths or performance benchmarks.", "",
        "| Package | Size | Native ABIs | Manifest version | Result |",
        "|---|---:|---|---|---|"]
    for item in results:
        pkg = item.get("package") or ["unknown", "?", "?"]
        lines.append("| " + item["file"] + " | " + str(size_mb(item["size_bytes"])) +
            " MiB | " + ", ".join(item["native_abis"]) + " | " +
            pkg[2] + " (" + pkg[1] + ") | " +
            ("PASS" if item["package_audit_passed"] else "FAIL") + " |")
    for item in results:
        lines.extend(["", "## " + item["file"], "",
            "SHA-256: " + item["sha256"], "",
            "Manifest package: " + str(item["package"]) +
            "; minSdk " + str(item["min_sdk"]) + "; targetSdk " + str(item["target_sdk"]), "",
            "### Compressed-space allocation"])
        lines.extend("- " + group + ": " + str(value) + " MiB"
                     for group, value in item["category_compressed_mib"].items())
        lines.extend(["", "### Largest individual entries"])
        lines.extend("- " + e["name"] + ": " + str(e["compressed_mib"]) + " MiB"
                     for e in item["largest_entries"])
        lines.extend(["", "### Package issues"])
        lines.extend("- " + issue for issue in item["issues"])
        if not item["issues"]:
            lines.append("- No package-structure defect detected by the automated audit")
        lines.extend(["", "### Caveats"])
        lines.extend("- " + note for note in item["notes"])
    (args.out_dir / "APK_PACKAGE_FORENSICS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    return 0 if all(item["package_audit_passed"] for item in results) else 1

if __name__ == "__main__":
    sys.exit(main())
