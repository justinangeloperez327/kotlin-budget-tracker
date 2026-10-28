#!/usr/bin/env python3
"""Verify a locally signed release APK and AAB without printing signing credentials.

The CI signing key is ephemeral and NEVER a production upload key.
The real upload key must be provided privately on the release operator's machine.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from verify_release_candidate import ROOT, PACKAGE, find_aapt, require, run_aapt, source_contract


def sdk_tool(name: str) -> str:
    root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if root:
        candidates = sorted((Path(root) / "build-tools").glob("*/" + name), reverse=True)
        for candidate in candidates:
            if candidate.is_file():
                return str(candidate)
    path = shutil.which(name)
    require(path is not None, name + " must be installed with Android SDK build tools")
    return path


def capture(args: list[str]) -> str:
    # No shell=True, and no secrets in the command line.
    proc = subprocess.run(args, capture_output=True, text=True, timeout=180, check=False)
    require(proc.returncode == 0, args[0] + " verification failed")
    return proc.stdout + "\n" + proc.stderr


def digest_file(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open("rb") as reader:
        for chunk in iter(lambda: reader.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def verify() -> dict:
    version, version_name = source_contract()
    key_path = os.environ.get("BT_RELEASE_STORE_FILE")
    alias = os.environ.get("BT_RELEASE_KEY_ALIAS")
    password = os.environ.get("BT_RELEASE_STORE_PASSWORD")
    keypass = os.environ.get("BT_RELEASE_KEY_PASSWORD")
    require(all((key_path, alias, password, keypass)), "Four BT_RELEASE_* variables required")
    require(Path(key_path).is_file(), "Upload key file does not exist")

    apk = ROOT / "app/build/outputs/apk/release/app-release.apk"
    bundle = ROOT / "app/build/outputs/bundle/release/app-release.aab"
    require(apk.is_file() and bundle.is_file(), "Signed APK and AAB must both exist")
    require(apk.stat().st_size > 0 and bundle.stat().st_size > 0, "Empty signed artifact")

    manifest = run_aapt(find_aapt(), "dump", "badging", str(apk))
    actual = re.search(
        r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'",
        manifest,
    )
    require(actual is not None, "Unable to inspect APK package metadata")
    require(
        actual.group(1) == PACKAGE and int(actual.group(2)) == version
        and actual.group(3) == version_name, "Signed package identity mismatch",
    )
    require("sdkVersion:'26'" in manifest and "targetSdkVersion:'36'" in manifest,
            "Signed SDK contract mismatch")

    # Android supports different signature schemes across API 26+; check that APK is valid.
    signature = capture([sdk_tool("apksigner"), "verify", "--verbose", "--print-certs", str(apk)])
    require(re.search(r"Verified using v[23] scheme.*:\s*true", signature, re.I) is not None,
            "APK must have a valid v2 or v3 signature")
    matches = re.findall(r"Signer #(\d+) certificate SHA-256 digest:\s*([a-f0-9:]+)",
                         signature, re.I)
    require(len(matches) == 1 and matches[0][0] == "1",
            "Expected exactly one APK signer")
    apk_fingerprint = matches[0][1].replace(":", "").lower()

    # -storepass:env prevents accidentally exposing passwords as process arguments.
    keystore_info = capture([
        "keytool", "-list", "-v", "-keystore", key_path,
        "-alias", alias, "-storepass:env", "BT_RELEASE_STORE_PASSWORD",
    ])
    match = re.search(r"SHA256:\s*((?:[0-9a-f]{2}:){31}[0-9a-f]{2})", keystore_info, re.I)
    require(match is not None, "Unable to read expected signing certificate fingerprint")
    expected = match.group(1).replace(":", "").lower()
    require(apk_fingerprint == expected, "Signed APK does not match selected upload key")

    jar_info = capture(["jarsigner", "-verify", str(bundle)])
    require("jar verified" in jar_info.lower(), "Android App Bundle signature verification failed")
    # Confirm the bundle is signed with exactly the same chosen certificate.
    with_bundle = capture(["keytool", "-printcert", "-jarfile", str(bundle)])
    bundle_match = re.search(r"SHA256:\s*((?:[0-9a-f]{2}:){31}[0-9a-f]{2})",
                             with_bundle, re.I)
    require(bundle_match is not None and
            bundle_match.group(1).replace(":", "").lower() == expected,
            "App Bundle certificate does not match signed APK / upload key")

    return {
        "applicationId": PACKAGE,
        "versionCode": version,
        "versionName": version_name,
        "certificateSha256": apk_fingerprint,
        "apkSha256": digest_file(apk),
        "bundleSha256": digest_file(bundle),
        "verification": "SIGNED APK AND AAB VERIFIED; not automatically published",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=None)
    args = parser.parse_args()
    try:
        result = verify()
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print("Signed release verified with upload-key certificate.")
        print("Certificate SHA-256:", result["certificateSha256"])
        print("APK SHA-256:", result["apkSha256"])
        print("AAB SHA-256:", result["bundleSha256"])
        return 0
    except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
        # Intentionally omit subprocess stdout/stderr, which could contain sensitive paths.
        print("Signed release verification FAILED:", str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
