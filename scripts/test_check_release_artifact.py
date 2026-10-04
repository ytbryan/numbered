"""Release checks against recorded tool output, so they run without a signed APK."""

from pathlib import Path
import tempfile
import unittest

from check_release_artifact import SIGNING_CERTIFICATE_SHA256, check_apk, check_tag, project_version, version_code


def apksigner(*digests: str) -> str:
    lines = ["Verifies", "Verified using v2 scheme (APK Signature Scheme v2): true", f"Number of signers: {len(digests)}"]
    for index, digest in enumerate(digests, start=1):
        lines.append(f"Signer #{index} certificate DN: CN=Numbered, O=Numbered, C=SG")
        lines.append(f"Signer #{index} certificate SHA-256 digest: {digest}")
    return "\n".join(lines)


def aapt2(name: str = "com.numbered.app", code: int = 2_000, version: str = "0.2.0") -> str:
    # As aapt2 prints it, with attributes that only end like the ones checked.
    return (
        f"package: name='{name}' versionCode='{code}' versionName='{version}' platformBuildVersionName='16' "
        f"platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'\nsdkVersion:'26'"
    )


class ReleaseArtifactTest(unittest.TestCase):
    def test_version_codes_rise_with_every_release(self):
        self.assertEqual(1_000, version_code("0.1.0"))
        self.assertEqual(2_000, version_code("0.2.0"))
        self.assertEqual(1_002_003, version_code("1.2.3"))
        releases = ["0.1.0", "0.1.1", "0.2.0", "0.10.0", "1.0.0", "1.0.10", "2.0.0"]
        self.assertEqual(sorted(map(version_code, releases)), list(map(version_code, releases)))
        # The first release shipped versionCode 1, so every derived code updates over it.
        self.assertGreater(version_code("0.1.0"), 1)

    def test_versions_outside_the_scheme_are_refused(self):
        for bad in ["1.2", "v1.2.3", "1.2.3-beta", "0.1000.0", "0.0.1000"]:
            with self.assertRaises(ValueError, msg=bad):
                version_code(bad)

    def test_the_tag_must_match_the_project_version(self):
        self.assertEqual([], check_tag("0.2.0", "0.2.0"))
        self.assertIn("does not match", check_tag("0.2.1", "0.2.0")[0])
        self.assertTrue(check_tag("v0.2.0", "0.2.0"))

    def test_the_project_version_is_read_from_gradle_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            properties = Path(directory) / "gradle.properties"
            properties.write_text("org.gradle.caching=true\nnumberedVersion = 0.3.1\n", encoding="utf-8")
            self.assertEqual("0.3.1", project_version(properties))
        self.assertIsNotNone(version_code(project_version()))

    def test_an_apk_signed_with_the_release_key_passes(self):
        self.assertEqual([], check_apk(apksigner(SIGNING_CERTIFICATE_SHA256), aapt2(), "0.2.0"))

    def test_an_apk_signed_with_another_key_is_refused(self):
        errors = check_apk(apksigner("0" * 64), aapt2(), "0.2.0")
        self.assertEqual(1, len(errors))
        self.assertIn("not the release certificate", errors[0])

    def test_an_unsigned_or_doubly_signed_apk_is_refused(self):
        self.assertTrue(check_apk(apksigner(), aapt2(), "0.2.0"))
        self.assertTrue(check_apk(apksigner(SIGNING_CERTIFICATE_SHA256, "0" * 64), aapt2(), "0.2.0"))

    def test_a_wrong_package_or_version_is_refused(self):
        signed = apksigner(SIGNING_CERTIFICATE_SHA256)
        self.assertTrue(check_apk(signed, aapt2(name="com.numbered.app.debug"), "0.2.0"))
        self.assertTrue(check_apk(signed, aapt2(version="0.1.0"), "0.2.0"))
        self.assertTrue(check_apk(signed, aapt2(code=1), "0.2.0"))


if __name__ == "__main__":
    unittest.main()
