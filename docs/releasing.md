# Releasing

Published to Maven Central under the group id `io.github.yakupov`: `inds-parent` (POM only), `inds-core` and
`inds-baselines`, each with sources and Javadoc. The test data and benchmark modules and `inds-core`'s test-jar
(golden records, test infrastructure) are not published. A release on Central is permanent: check before publishing.

## One-time setup (maintainer only)

1. **Central account.** Sign in to [central.sonatype.com](https://central.sonatype.com) with the GitHub account
   `yakupov`; the namespace `io.github.yakupov` is verified with it. Generate a user token (a username and password
   pair, not the login) for each place that uploads: one for `~/.m2/settings.xml` (server id `central`) if releasing
   by hand, one for GitHub.
2. **Signing key.** Central requires an OpenPGP signature (`.asc`) for every file and checks it against the public key
   on a key server. The maintainer's key has a signing subkey for CI, so the primary key never leaves the
   maintainer's machine:

   ```bash
   gpg --quick-add-key <FINGERPRINT> rsa4096 sign 2y                 # signing subkey, 2 years
   gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>   # publish the key with the new subkey
   gpg --armor --export-secret-subkeys <SUBKEY_ID>!                 # only that subkey (note the "!")
   ```

   If the subkey leaks, revoke it (`gpg --edit-key <FINGERPRINT>`, `key <n>`, `revkey`), send the key again and add a
   new one.
3. **GitHub environment.** Settings → Environments → New environment `release`: required reviewer `yakupov`;
   deployment branches: selected branches, `main`; environment secrets `CENTRAL_TOKEN_USERNAME`,
   `CENTRAL_TOKEN_PASSWORD` (the token for GitHub), `GPG_PRIVATE_KEY` (the exported subkey, armored text),
   `GPG_PASSPHRASE`. Jobs of other branches and pull requests never see these secrets.

## Release `X.Y.Z` from GitHub Actions

1. Actions → **Release** → Run workflow on `main`: `version` `X.Y.Z`; `next` defaults to `X.Y+1.0-SNAPSHOT`.
   `dry-run` builds and bundles without signing, uploading or pushing, and shows the release notes in the run summary.
2. The workflow ([release.yml](../.github/workflows/release.yml)) checks the inputs and that the tag is new, runs
   `mvn verify` unless CI has already passed on the same commit (start the release after CI is green to save the
   run), then waits for approval (environment `release`). After approval it commits "Release X.Y.Z" (POM versions and
   the version in the README's dependency snippet), builds and signs the three artifacts, bundles them, uploads the
   bundle ([scripts/central-upload.py](../scripts/central-upload.py)) and waits until Central has validated it. Only
   then it tags `vX.Y.Z`, commits the next development version and pushes both commits and the tag to `main` in one
   atomic push (as `github-actions[bot]`), and creates a draft GitHub release whose notes list the issues closed (as
   completed) since the previous release. It fails without uploading if `main` moved after the start.
3. Publish the validated deployment on central.sonatype.com (Publish → Deployments → Publish), or drop it. Then edit
   the draft release notes on GitHub (a sentence per user-visible change; drop issues that are not changes) and
   publish them. After a drop, delete the tag and the draft release, or release the next version.

## Release by hand (fallback)

```bash
mvn verify                                                    # all tests, on the code to release
mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
git commit -am "Release X.Y.Z" && git tag -a vX.Y.Z -m "Release X.Y.Z"
mvn -Prelease -Dmaven.test.skip=true -pl .,inds-core,inds-baselines deploy   # signs; writes target/central-staging
python scripts/central-bundle.py target/central-staging target/central-bundle.zip
```

The `release` profile deploys into the local directory `target/central-staging`, not to a server, and refuses to run
without `-Dmaven.test.skip=true` (that switch also keeps `inds-core`'s test-jar out). `central-bundle.py` checks that
every POM and jar has its signature and checksums and zips them with the layout Central expects. Upload
`target/central-bundle.zip` on central.sonatype.com (Publish → Publish Component), or with `central-upload.py` (token
in `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD`); wait for the validation, check the three components and
publish. Then push the commit and the tag, and set the next development version
(`mvn versions:set -DnewVersion=X.Y+1.0-SNAPSHOT -DgenerateBackupPoms=false`, commit, push).

Sonatype's `central-publishing-maven-plugin` is not used: on Windows with Maven 3.10 it writes zip entries with
backslashes and puts resolver files into the bundle, which Central rejects. The scripts build and upload the same
bundle on every platform.
