"""Check a release before it is published: its tag, its version, and the key that signed it.

    python3 scripts/check_release_artifact.py tag 0.2.0
    python3 scripts/check_release_artifact.py apk app/build/outputs/apk/release/app-release.apk 0.2.0

Android installs an update only when it is signed with the same key as the installed app, so an
APK signed with any other key would strand everyone who installed an earlier release.
"""

from pathlib import Path
import os
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "com.numbered.app"

# The certificate that signed every published release, starting with 0.0.1 (CN=Numbered, O=Numbered, C=SG).
SIGNING_CERTIFICATE_SHA256 = "47be49001e91f32980511462b3d8b3a9a98bc804bb4279dc7341fdb603b67434"

VERSION = re.compile(r"(\d+)\.(\d+)\.(\d+)")


def version_code(version: str) -> int:
    """The same rule as versionCodeOf in app/build.gradle.kts."""
    match = VERSION.fullmatch(version)
    if not match:
        raise ValueError(f"Version must be major.minor.patch, not {version!r}")
    major, minor, patch = (int(part) for part in match.groups())
    if minor >= 1000 or patch >= 1000:
        raise ValueError("Minor and patch must stay below 1000")
    return major * 1_000_000 + minor * 1_000 + patch


def project_version(properties: Path = ROOT / "gradle.properties") -> str:
    for line in properties.read_text(encoding="utf-8").splitlines():
        key, _, value = line.partition("=")
        if key.strip() == "numberedVersion":
            return value.strip()
    raise ValueError("numberedVersion is missing from gradle.properties")


def check_tag(tag: str, version: str) -> list[str]:
    errors = []
    try:
        version_code(tag)
    except ValueError as error:
        errors.append(f"Tag {tag!r}: {error}")
    if tag != version:
        errors.append(f"Tag {tag} does not match numberedVersion {version} in gradle.properties")
    return errors


def signer_digests(apksigner_output: str) -> list[str]:
    return re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})", apksigner_output)


def badging(aapt2_output: str) -> dict[str, str]:
    first = aapt2_output.splitlines()[0] if aapt2_output else ""
    # Whole attributes only: compileSdkVersionCodename also ends in name='...'.
    return dict(re.findall(r"(?<![\w])(name|versionCode|versionName)='([^']*)'", first))


def check_apk(apksigner_output: str, aapt2_output: str, version: str) -> list[str]:
    errors = []
    digests = signer_digests(apksigner_output)
    if digests != [SIGNING_CERTIFICATE_SHA256]:
        errors.append(f"Signed by {digests or 'no certificate'}, not the release certificate {SIGNING_CERTIFICATE_SHA256}")
    app = badging(aapt2_output)
    if app.get("name") != PACKAGE:
        errors.append(f"Package is {app.get('name')}, not {PACKAGE}")
    if app.get("versionName") != version:
        errors.append(f"versionName is {app.get('versionName')}, not {version}")
    if app.get("versionCode") != str(version_code(version)):
        errors.append(f"versionCode is {app.get('versionCode')}, not {version_code(version)}")
    return errors


def build_tool(name: str) -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or str(Path.home() / "Library/Android/sdk")
    versions = sorted(
        (Path(sdk) / "build-tools").glob("*"),
        key=lambda path: [int(part) for part in re.findall(r"\d+", path.name)],
    )
    for directory in reversed(versions):
        if (directory / name).is_file():
            return str(directory / name)
    raise FileNotFoundError(f"No {name} in {sdk}/build-tools")


def run(*command: str) -> str:
    return subprocess.run(command, check=True, capture_output=True, text=True).stdout


def main(arguments: list[str]) -> int:
    if len(arguments) == 2 and arguments[0] == "tag":
        errors = check_tag(arguments[1], project_version())
    elif len(arguments) == 3 and arguments[0] == "apk":
        apk, tag = arguments[1], arguments[2]
        errors = check_tag(tag, project_version()) + check_apk(
            run(build_tool("apksigner"), "verify", "--print-certs", apk),
            run(build_tool("aapt2"), "dump", "badging", apk),
            tag,
        )
    else:
        print(__doc__, file=sys.stderr)
        return 2
    for error in errors:
        print(f"Release check failed: {error}", file=sys.stderr)
    if errors:
        return 1
    print("Release tag, version, and signing certificate match.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
