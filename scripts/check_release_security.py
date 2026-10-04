"""Check the release manifest and data-sharing rules for unexpected access paths.

Run after ``./gradlew assembleRelease`` so library manifests are included.
This is a regression guard for specific security boundaries, not a proof that
arbitrary application or dependency code is free of malicious behavior.
"""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_PERMISSIONS = {
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.USE_BIOMETRIC",
    "android.permission.USE_FINGERPRINT",
    "android.permission.VIBRATE",
    "android.permission.WAKE_LOCK",
    "com.numbered.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
# Every component reachable outside the app needs an explicit review here.
EXPORTED_COMPONENTS = {
    ("activity", "com.numbered.app.MainActivity"): None,
    ("activity", "com.numbered.app.CaptureActivity"): None,
    ("receiver", "com.numbered.app.widget.ThisWeekWidgetReceiver"): None,
    ("receiver", "com.numbered.app.reminders.RescheduleReceiver"): None,
    ("service", "androidx.glance.appwidget.GlanceRemoteViewsService"): "android.permission.BIND_REMOTEVIEWS",
    ("service", "androidx.work.impl.background.systemjob.SystemJobService"): "android.permission.BIND_JOB_SERVICE",
    ("receiver", "androidx.work.impl.diagnostics.DiagnosticsReceiver"): "android.permission.DUMP",
    ("receiver", "androidx.profileinstaller.ProfileInstallReceiver"): "android.permission.DUMP",
}
COMPONENT_TAGS = {"activity", "activity-alias", "provider", "receiver", "service"}


def check(manifest: Path, backup_rules: Path, extraction_rules: Path, review_paths: Path) -> list[str]:
    errors = []
    root = ET.parse(manifest).getroot()
    if root.get("package") != "com.numbered.app":
        errors.append("Unexpected application ID")

    permissions = {
        node.get(ANDROID + "name")
        for node in root
        if node.tag.startswith("uses-permission")
    }
    if permissions != EXPECTED_PERMISSIONS:
        errors.append(f"Permissions changed: added {sorted(permissions - EXPECTED_PERMISSIONS)}, removed {sorted(EXPECTED_PERMISSIONS - permissions)}")

    app = root.find("application")
    if app is None:
        return errors + ["Missing application"]
    for attribute in ("debuggable", "testOnly", "usesCleartextTraffic"):
        if app.get(ANDROID + attribute) == "true":
            errors.append(f"Release application enables {attribute}")

    exported = {}
    review_provider_found = False
    for component in app:
        if component.tag not in COMPONENT_TAGS:
            continue
        name = component.get(ANDROID + "name")
        # Intent filters make components implicitly exported on older Android versions.
        externally_reachable = component.get(ANDROID + "exported") == "true" or (
            component.get(ANDROID + "exported") is None and component.find("intent-filter") is not None
        )
        if externally_reachable:
            exported[(component.tag, name)] = component.get(ANDROID + "permission")
        if component.tag == "provider" and name == "androidx.core.content.FileProvider":
            review_provider_found = True
            if component.get(ANDROID + "exported") != "false":
                errors.append("Review FileProvider is exported")
            if component.get(ANDROID + "authorities") != "com.numbered.app.reviews":
                errors.append("Review FileProvider authority changed")
            if component.get(ANDROID + "grantUriPermissions") != "true":
                errors.append("Review FileProvider URI grants changed")
    if not review_provider_found:
        errors.append("Review FileProvider is missing")
    if exported != EXPORTED_COMPONENTS:
        added = {key: value for key, value in exported.items() if EXPORTED_COMPONENTS.get(key, object()) != value}
        removed = {key: value for key, value in EXPORTED_COMPONENTS.items() if exported.get(key, object()) != value}
        errors.append(f"Exported components changed: added/changed {added}, removed/changed {removed}")

    if app.get(ANDROID + "allowBackup") != "true" or app.get(ANDROID + "fullBackupContent") != "@xml/backup_rules" or app.get(ANDROID + "dataExtractionRules") != "@xml/data_extraction_rules":
        errors.append("Backup rule references changed")
    backup = ET.parse(backup_rules).getroot()
    if backup.tag != "full-backup-content" or [(child.tag, child.attrib) for child in backup] != [("include", {"domain": "database", "path": "."})]:
        errors.append("Legacy backup includes data beyond the database")
    extraction = ET.parse(extraction_rules).getroot()
    expected_branch = [("include", {"domain": "database", "path": "."})]
    if extraction.tag != "data-extraction-rules" or [child.tag for child in extraction] != ["cloud-backup", "device-transfer"] or any(
        [(child.tag, child.attrib) for child in branch] != expected_branch for branch in extraction
    ):
        errors.append("Device backup or transfer includes data beyond the database")

    paths = ET.parse(review_paths).getroot()
    if paths.tag != "paths" or [(child.tag, child.attrib) for child in paths] != [
        ("cache-path", {"name": "reviews", "path": "year-reviews/"})
    ]:
        errors.append("Review FileProvider paths changed")
    return errors


def main() -> int:
    manifest = ROOT / "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
    if not manifest.is_file():
        print("Build the release first: ./gradlew assembleRelease", file=sys.stderr)
        return 2
    errors = check(
        manifest,
        ROOT / "app/src/main/res/xml/backup_rules.xml",
        ROOT / "app/src/main/res/xml/data_extraction_rules.xml",
        ROOT / "app/src/main/res/xml/review_paths.xml",
    )
    for error in errors:
        print(f"Security check failed: {error}", file=sys.stderr)
    if errors:
        return 1
    print("Release manifest and data-sharing boundaries match the reviewed baseline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
