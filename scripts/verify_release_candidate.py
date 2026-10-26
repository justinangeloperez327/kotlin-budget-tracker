#!/usr/bin/env python3
"""Verify an unsigned, R8-minified Android release candidate without signing secrets.

Reads source manifest AND inspects built Android artifact with the Android SDK's aapt.
No network, third-party dependencies, or access to personal financial data.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "dev.budgettracker.android"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def source_contract() -> tuple[int, str]:
    module = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    def prop(name: str) -> str:
        match = re.search(r"\b" + re.escape(name) + r'\s*=\s*(?:"([^"]+)"|(\d+))', module)
        require(match is not None, f"Missing {name} in Android build configuration")
        return match.group(1) or match.group(2)
    version = int(prop("versionCode"))
    name = prop("versionName")
    require(version > 0 and bool(re.fullmatch(r"0\.9\.0-rc\.\d+", name)),
            "Group 9 is a release candidate, not a production version")
    require(prop("applicationId") == PACKAGE, "Unexpected application ID")
    require(int(prop("minSdk")) == 26 and int(prop("targetSdk")) == 36,
            "Unreviewed Android API level change")

    document = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    manifest = document.getroot()
    app = manifest.find("application")
    require(app is not None, "Android application element missing")
    require(app.get(NS+"allowBackup") == "false", "Android auto-backup must be disabled")
    require(app.get(NS+"usesCleartextTraffic") == "false", "Plain HTTP must be disabled")
    require(app.get(NS+"fullBackupContent") == "@xml/backup_rules",
            "Android 11 backup exclusion policy absent")
    require(app.get(NS+"dataExtractionRules") == "@xml/data_extraction_rules",
            "Android 12+ backup exclusion policy absent")
    for permission in manifest.findall("uses-permission"):
        require(permission.get(NS+"name") == "android.permission.INTERNET",
                "Unexpected manifest permission: "+str(permission.get(NS+"name")))
    activities = app.findall("activity")
    require(len(activities) == 1 and activities[0].get(NS+"name") == ".MainActivity"
            and activities[0].get(NS+"exported") == "true",
            "Application exposure surface changed")
    for field,root_tag,subtags in [
        ("backup_rules.xml", "full-backup-content", []),
        ("data_extraction_rules.xml", "data-extraction-rules", ["cloud-backup","device-transfer"]),
    ]:
        xml = ET.parse(ROOT / "app/src/main/res/xml" / field).getroot()
        require(xml.tag == root_tag, field+": wrong root element")
        items = [xml] if not subtags else [xml.find(tag) for tag in subtags]
        require(all(item is not None for item in items), field+": missing backup policy section")
        for item in items:
            excludes = {(x.get("domain"),x.get("path")) for x in item.findall("exclude")}
            require(("root",".") in excludes and ("device_root",".") in excludes,
                    field+": private app files must be excluded")
    return version,name


def find_aapt() -> str:
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidates = []
    if home:
        candidates += sorted((Path(home)/"build-tools").glob("*/aapt"), reverse=True)
    path = shutil.which("aapt")
    if path:
        candidates.append(Path(path))
    for item in candidates:
        if item.is_file():
            return str(item)
    raise ValueError("Android build-tools aapt not installed")


def run_aapt(aapt: str, *args: str) -> str:
    result = subprocess.run([aapt,*args],capture_output=True,text=True,timeout=60,check=True)
    return result.stdout


def verify_artifacts(version: int, name: str) -> dict:
    apk = ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
    aab = ROOT / "app/build/outputs/bundle/release/app-release.aab"
    require(apk.is_file() and aab.is_file(), "Unsigned APK or AAB not generated")
    require(apk.stat().st_size > 0 and aab.stat().st_size > 0,"Release outputs empty")
    badging = run_aapt(find_aapt(),"dump","badging",str(apk))
    match = re.search(r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'",badging)
    require(match is not None and match.group(1) == PACKAGE and
            int(match.group(2)) == version and match.group(3) == name,
            "Built APK package name/version mismatch")
    require("sdkVersion:'26'" in badging and "targetSdkVersion:'36'" in badging,
            "Built APK SDK version mismatch")
    permissions = run_aapt(find_aapt(),"dump","permissions",str(apk))
    forbidden = ("READ_EXTERNAL_STORAGE","WRITE_EXTERNAL_STORAGE","MANAGE_EXTERNAL_STORAGE",
                 "QUERY_ALL_PACKAGES","READ_SMS","ACCESS_FINE_LOCATION",
                 "READ_CONTACTS","RECORD_AUDIO","CAMERA")
    require(not any(x in permissions for x in forbidden),
            "Unexpected sensitive permission in built APK")
    output = {}
    for label,entry in (("apk",apk),("aab",aab)):
        digest = hashlib.sha256()
        with entry.open("rb") as f:
            for chunk in iter(lambda:f.read(1024*1024),b""):
                digest.update(chunk)
        output[label]={"sha256":digest.hexdigest(),"bytes":entry.stat().st_size,
                       "file":entry.name}
    return {"applicationId":PACKAGE,"versionCode":version,"versionName":name,
            "signing":"UNSIGNED - NOT INSTALLABLE AS A PRODUCTION RELEASE",
            "artifacts":output}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-only",action="store_true")
    args = parser.parse_args()
    try:
        version,name = source_contract()
        print(f"Source security contract passed: {PACKAGE} {name} ({version})")
        if not args.source_only:
            report=verify_artifacts(version,name)
            output=ROOT/"app/build/outputs/release-candidate/audit.json"
            output.parent.mkdir(parents=True,exist_ok=True)
            output.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
            print("Unsigned release artifact verification passed")
            for label,item in report["artifacts"].items():
                print(label.upper()+": "+item["sha256"])
        return 0
    except (ValueError,subprocess.CalledProcessError,OSError,ET.ParseError) as exc:
        print("Release verification FAILED: "+str(exc),file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
