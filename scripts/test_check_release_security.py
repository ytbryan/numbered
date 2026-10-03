"""Safe mutation tests: all malicious examples are written to temporary files."""

from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from check_release_security import ANDROID, ROOT, check


MANIFEST = ROOT / "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
BACKUP = ROOT / "app/src/main/res/xml/backup_rules.xml"
EXTRACTION = ROOT / "app/src/main/res/xml/data_extraction_rules.xml"
REVIEW = ROOT / "app/src/main/res/xml/review_paths.xml"


class ReleaseSecurityTest(unittest.TestCase):
    def test_current_release_matches_reviewed_boundaries(self):
        self.assertTrue(MANIFEST.is_file(), "Build the release before running security tests")
        self.assertEqual([], check(MANIFEST, BACKUP, EXTRACTION, REVIEW))

    def test_network_permission_is_rejected(self):
        root = ET.parse(MANIFEST).getroot()
        ET.SubElement(root, "uses-permission", {ANDROID + "name": "android.permission.INTERNET"})
        self.assertRejected(root, "Permissions changed")

    def test_unprotected_exported_service_is_rejected(self):
        root = ET.parse(MANIFEST).getroot()
        ET.SubElement(root.find("application"), "service", {
            ANDROID + "name": "com.numbered.app.HiddenService",
            ANDROID + "exported": "true",
        })
        self.assertRejected(root, "Exported components changed")

    def test_debuggable_release_is_rejected(self):
        root = ET.parse(MANIFEST).getroot()
        root.find("application").set(ANDROID + "debuggable", "true")
        self.assertRejected(root, "enables debuggable")

    def test_broad_file_sharing_is_rejected(self):
        root = ET.parse(REVIEW).getroot()
        root[0].set("path", ".")
        with tempfile.TemporaryDirectory() as directory:
            changed = Path(directory) / "review_paths.xml"
            ET.ElementTree(root).write(changed)
            self.assertIn("Review FileProvider paths changed", check(MANIFEST, BACKUP, EXTRACTION, changed))

    def test_backing_up_private_preferences_is_rejected(self):
        root = ET.parse(BACKUP).getroot()
        ET.SubElement(root, "include", {"domain": "sharedpref", "path": "."})
        with tempfile.TemporaryDirectory() as directory:
            changed = Path(directory) / "backup_rules.xml"
            ET.ElementTree(root).write(changed)
            self.assertIn("Legacy backup includes data beyond the database", check(MANIFEST, changed, EXTRACTION, REVIEW))

    def assertRejected(self, changed_manifest, expected_message):
        with tempfile.TemporaryDirectory() as directory:
            changed = Path(directory) / "AndroidManifest.xml"
            ET.ElementTree(changed_manifest).write(changed)
            self.assertTrue(any(expected_message in error for error in check(changed, BACKUP, EXTRACTION, REVIEW)))


if __name__ == "__main__":
    unittest.main()
