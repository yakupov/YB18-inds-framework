"""Build the Maven Central upload bundle from the staging repository written by `mvn -Prelease ... deploy`.

The bundle holds, for every published version directory (group/artifact/version), the POM and the jars, each with its
signature (.asc) and checksums (.md5, .sha1). Everything else in the staging repository (maven-metadata*.xml,
resolver bookkeeping) stays out. Zip entries use '/' on every platform. Fails if a file is missing.

Usage: python scripts/central-bundle.py <staging-dir> <bundle.zip> [--unsigned]
  --unsigned: allow missing signatures (dry runs with -Dgpg.skip; Central rejects such a bundle)
"""
import sys
import zipfile
from pathlib import Path

PRIMARY_SUFFIXES = (".pom", ".jar")
COMPANION_SUFFIXES = (".md5", ".sha1")
SIGNATURE_SUFFIX = ".asc"


def version_directories(staging: Path) -> list[Path]:
    """Directories that contain a POM: one per published artifact version."""
    return sorted({pom.parent for pom in staging.rglob("*.pom")})


def bundle_files(version_dir: Path, signed: bool) -> tuple[list[Path], list[str]]:
    """The files of one artifact version to upload, and the names of the missing ones."""
    files, missing = [], []
    for primary in sorted(f for f in version_dir.iterdir() if f.name.endswith(PRIMARY_SUFFIXES)):
        files.append(primary)
        companions = list(COMPANION_SUFFIXES) + ([SIGNATURE_SUFFIX] if signed else [])
        for suffix in companions:
            companion = primary.with_name(primary.name + suffix)
            if companion.is_file():
                files.append(companion)
            else:
                missing.append(companion.name)
    return files, missing


def main(args: list[str]) -> int:
    if len(args) not in (2, 3) or (len(args) == 3 and args[2] != "--unsigned"):
        print(__doc__)
        return 2
    staging, bundle = Path(args[0]), Path(args[1])
    signed = len(args) == 2
    directories = version_directories(staging)
    if not directories:
        print(f"no POM under {staging}: run the release deploy first")
        return 1
    entries, missing = [], []
    for directory in directories:
        files, absent = bundle_files(directory, signed)
        entries += files
        missing += [f"{directory.relative_to(staging).as_posix()}/{name}" for name in absent]
    if missing:
        print("missing files:\n  " + "\n  ".join(missing))
        return 1
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as zip_file:
        for file in entries:
            zip_file.write(file, file.relative_to(staging).as_posix())
    for directory in directories:
        print(directory.relative_to(staging).as_posix() + ": "
              + ", ".join(f.name for f in entries if f.parent == directory and f.name.endswith(PRIMARY_SUFFIXES)))
    print(f"{bundle}: {len(entries)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
