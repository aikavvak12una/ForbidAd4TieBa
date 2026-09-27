# Architecture and verification

The accepted [decision](../adr/0001-feature-contracts.md) is the contract for this
refactor. The [baseline](baseline.md) identifies the input and separates historical
evidence from checks against current source.

| Module | Owns | Allowed project dependencies |
| --- | --- | --- |
| contracts | JVM values, configuration and diagnostic interfaces | none |
| host | symbol contracts, scanning, cache, restoration, host accessors | contracts |
| runtime | libxposed bridge, handles, installation execution and records | contracts |
| features | hooks, policies, settings, page sessions and feature caches | contracts, host, runtime |
| app | MainHook, composition, build identity, APK packaging | all above |

Keep internals internal. Export boundary types and factories only when another
module consumes them. Dependencies inside features must also follow feature
ownership; shared policy is distinct from another feature's installer or page.

[Symbol discovery](symbol-discovery.md) defines the DexKit/reflection boundary,
semantic evidence, ambiguity policy and source regression gate.

Run `./gradlew verify` (Windows: `./gradlew.bat verify`). It executes architecture
and unit tests, Debug and Release Lint and the Release build. Pull requests and
main-branch commits run the same gate without signing credentials. The release
workflow supplies credentials to that gate, verifies the signature, and uploads
the signed artifact. JNI entries must remain uncompressed for LSPosed.

Local device tests use the authorization and preflight in `AGENTS.local.md`.
No verification task installs an APK or mutates device settings. For the known
JBR Windows loopback issue, a process-local `jdk.net.unixdomain.tmpdir` pointing
at an absent workspace path and `java.net.preferIPv4Stack=true` force TCP fallback;
do not create that path or change system configuration.

For each feature change, verify dependency completeness, JSON round trips,
missing/ambiguous/invalid targets, relevant partial failures, duplicate installs
and stale callbacks. For page work, include stale account/request/page results
and feature disablement. Inspect current-host targets separately from hook
installation and user-visible effects. Visual acceptance belongs to the user.
