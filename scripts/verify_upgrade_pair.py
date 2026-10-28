#!/usr/bin/env python3
"""Preflight a manual signed in-place Android upgrade; NEVER uninstall or clear app data.

Requires an older signed APK and a newer signed APK. Check certificates and version
codes before touching an attached device. Device installation is explicit and opt-in.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re
import subprocess
import sys

from verify_release_candidate import PACKAGE, find_aapt, require, run_aapt
from verify_signed_candidate import capture, sdk_tool


def inspect(apk: Path) -> tuple[str, int, str]:
    require(apk.is_file(), "Signed APK not found: " + str(apk))
    metadata=run_aapt(find_aapt(),"dump","badging",str(apk))
    match=re.search(r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'",metadata)
    require(match is not None and match.group(1)==PACKAGE, "Package name is not Budget Tracker")
    signing=capture([sdk_tool("apksigner"),"verify","--verbose","--print-certs",str(apk)])
    require(re.search(r"Verified using v[23] scheme.*:\s*true",signing,re.I) is not None,
            "APK must have a valid Android v2/v3 signature")
    matches=re.findall(r"Signer #(\d+) certificate SHA-256 digest:\s*([a-f0-9:]+)",
                        signing,re.I)
    require(len(matches)==1 and matches[0][0]=="1", "A single APK signer is required")
    return (match.group(1),int(match.group(2)),matches[0][1].replace(":","").lower())


def preflight(old: Path,new: Path) -> dict:
    package,old_code,old_cert=inspect(old)
    next_package,new_code,new_cert=inspect(new)
    require(new_code>old_code,"The new APK must have a higher Android versionCode")
    require(package==next_package==PACKAGE,"In-place upgrades cannot change applicationId")
    require(new_cert==old_cert,"Signing certificate changed: installing would fail or risk data loss")
    return {"applicationId":package,"previousVersionCode":old_code,
            "newVersionCode":new_code,"certificateSha256":new_cert}


def main()->int:
    parser=argparse.ArgumentParser()
    parser.add_argument("--old",required=True,type=Path,help="Previously distributed signed APK")
    parser.add_argument("--new",required=True,type=Path,help="New signed APK")
    parser.add_argument("--install",action="store_true",help="Opt in to adb install -r without clearing data")
    parser.add_argument("--confirm-device-replacement",action="store_true",
                        help="Required with --install; confirms user backed up device data")
    parser.add_argument("--serial",help="Required when installing; explicitly selects connected device")
    args=parser.parse_args()
    try:
        info=preflight(args.old,args.new)
        print("Signed in-place upgrade preflight passed:",info["previousVersionCode"],
              "->",info["newVersionCode"])
        print("Certificate SHA-256:",info["certificateSha256"])
        if args.install:
            require(args.confirm_device_replacement and args.serial,
                    "--install requires --serial and --confirm-device-replacement")
            # -r replaces the APK while preserving app data. NEVER adb uninstall / pm clear.
            process=subprocess.run(["adb","-s",args.serial,"install","-r",str(args.new)],
                                   capture_output=True,text=True,timeout=300,check=False)
            require(process.returncode==0 and "Success" in process.stdout,
                    "Device upgrade installation failed; check adb/Android logs")
            print("Signed APK installed in place; manually verify budgets, linked expenses, "
                  "login and restore before approving the release.")
        else:
            print("Dry run only. No device data was touched.")
        return 0
    except (ValueError,OSError,subprocess.TimeoutExpired) as e:
        print("Upgrade preflight FAILED:",e,file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
