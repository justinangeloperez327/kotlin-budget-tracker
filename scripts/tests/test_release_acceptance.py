"""Offline acceptance tests: no keystore, backend, emulator, or network required."""
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

import verify_release_candidate as release
import verify_signed_candidate as signed
import verify_upgrade_pair as upgrade

CERT = "ab" * 32
OTHER = "cd" * 32
BADGING = (
    "package: name='dev.budgettracker.android' versionCode='9' "
    "versionName='0.9.0-rc.1'\nsdkVersion:'26'\ntargetSdkVersion:'36'\n"
)


class SourceAcceptanceTest(unittest.TestCase):
    def test_current_release_contract_is_explicit(self):
        self.assertEqual(release.source_contract(), (9, "0.9.0-rc.1"))

    def test_source_contract_rejects_sensitive_permission_added_to_manifest(self):
        with tempfile.TemporaryDirectory() as location:
            root = Path(location)
            for relative in (
                "app/build.gradle.kts",
                "app/src/main/AndroidManifest.xml",
                "app/src/main/res/xml/backup_rules.xml",
                "app/src/main/res/xml/data_extraction_rules.xml",
            ):
                dest = root / relative
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(release.ROOT / relative, dest)
            manifest = root / "app/src/main/AndroidManifest.xml"
            data = manifest.read_text(encoding="utf-8")
            data = data.replace("</manifest>", '<uses-permission '
                'android:name="android.permission.READ_SMS"/></manifest>')
            manifest.write_text(data, encoding="utf-8")
            with patch.object(release, "ROOT", root):
                with self.assertRaisesRegex(ValueError, "Unexpected manifest permission"):
                    release.source_contract()

    def test_missing_backup_exclusion_is_a_release_blocker(self):
        with tempfile.TemporaryDirectory() as location:
            root = Path(location)
            for relative in (
                "app/build.gradle.kts",
                "app/src/main/AndroidManifest.xml",
                "app/src/main/res/xml/backup_rules.xml",
                "app/src/main/res/xml/data_extraction_rules.xml",
            ):
                dest = root / relative
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(release.ROOT / relative, dest)
            manifest = root / "app/src/main/AndroidManifest.xml"
            data = manifest.read_text(encoding="utf-8")
            data = data.replace('android:allowBackup="false"', 'android:allowBackup="true"')
            manifest.write_text(data, encoding="utf-8")
            with patch.object(release, "ROOT", root):
                with self.assertRaisesRegex(ValueError, "auto-backup"):
                    release.source_contract()


class SignedReleaseTest(unittest.TestCase):
    def fake_capture(self, argv):
        cmd = argv[0]
        if cmd == "apksigner":
            return ("Verified using v2 scheme (APK Signature Scheme v2): true\n"
                    "Signer #1 certificate SHA-256 digest: " + CERT)
        if cmd == "jarsigner":
            return "jar verified."
        if cmd == "keytool":
            return "SHA256: " + ":".join(CERT[i:i+2] for i in range(0, 64, 2))
        raise AssertionError(cmd)

    def make_fixture(self, root):
        apk = root / "app/build/outputs/apk/release/app-release.apk"
        aab = root / "app/build/outputs/bundle/release/app-release.aab"
        apk.parent.mkdir(parents=True)
        aab.parent.mkdir(parents=True)
        apk.write_bytes(b"signed APK fixture")
        aab.write_bytes(b"signed bundle fixture")
        key = root / "local-only.jks"
        key.write_bytes(b"fake key fixture - not usable for signing")
        return key

    def test_signed_artifacts_must_use_selected_upload_certificate(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            key = self.make_fixture(root)
            env = {
                "BT_RELEASE_STORE_FILE": str(key),
                "BT_RELEASE_STORE_PASSWORD": "test",
                "BT_RELEASE_KEY_ALIAS": "test",
                "BT_RELEASE_KEY_PASSWORD": "test",
            }
            with (
                patch.dict(os.environ, env),
                patch.object(signed, "ROOT", root),
                patch.object(signed, "source_contract", return_value=(9, "0.9.0-rc.1")),
                patch.object(signed, "find_aapt", return_value="aapt"),
                patch.object(signed, "run_aapt", return_value=BADGING),
                patch.object(signed, "sdk_tool", return_value="apksigner"),
                patch.object(signed, "capture", side_effect=self.fake_capture),
            ):
                self.assertEqual(signed.verify()["certificateSha256"], CERT)

    def test_signed_release_rejects_certificate_mismatch(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            key = self.make_fixture(root)
            def mismatch(argv):
                if argv[0] == "keytool":
                    return "SHA256: " + ":".join(OTHER[i:i+2] for i in range(0,64,2))
                return self.fake_capture(argv)
            env = {
                "BT_RELEASE_STORE_FILE": str(key),
                "BT_RELEASE_STORE_PASSWORD": "test",
                "BT_RELEASE_KEY_ALIAS": "test",
                "BT_RELEASE_KEY_PASSWORD": "test",
            }
            with (
                patch.dict(os.environ, env),
                patch.object(signed, "ROOT", root),
                patch.object(signed, "source_contract", return_value=(9,"0.9.0-rc.1")),
                patch.object(signed, "find_aapt", return_value="aapt"),
                patch.object(signed, "run_aapt", return_value=BADGING),
                patch.object(signed, "sdk_tool", return_value="apksigner"),
                patch.object(signed, "capture", side_effect=mismatch),
            ):
                with self.assertRaisesRegex(ValueError,"does not match selected"):
                    signed.verify()


class UpgradeAcceptanceTest(unittest.TestCase):
    def test_signed_upgrade_preserves_package_and_certificate(self):
        with patch.object(upgrade, "inspect", side_effect=[
            (release.PACKAGE, 8, CERT), (release.PACKAGE, 9, CERT),
        ]):
            result=upgrade.preflight(Path("old.apk"),Path("new.apk"))
        self.assertEqual(result["previousVersionCode"], 8)
        self.assertEqual(result["newVersionCode"],9)

    def test_version_code_must_increase(self):
        with patch.object(upgrade, "inspect", side_effect=[
            (release.PACKAGE,9,CERT),(release.PACKAGE,9,CERT),
        ]):
            with self.assertRaisesRegex(ValueError,"higher Android versionCode"):
                upgrade.preflight(Path("old.apk"),Path("new.apk"))

    def test_upgrade_rejects_cert_rotation_without_approved_key_migration(self):
        with patch.object(upgrade, "inspect", side_effect=[
            (release.PACKAGE,8,CERT),(release.PACKAGE,9,OTHER),
        ]):
            with self.assertRaisesRegex(ValueError,"Signing certificate changed"):
                upgrade.preflight(Path("old.apk"),Path("new.apk"))


if __name__=="__main__":
    unittest.main()
