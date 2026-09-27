# Verification during the refactor

## Module extraction

The source now builds with the five-module dependency graph. Application startup
delegates to a feature lifecycle factory; BuildConfig identity, logging policy and
module APK/native paths are injected at the app boundary. Host code has no
libxposed, feature, configuration or UI dependency. Runtime has no host or feature
dependency. The explicit scan's toasts and clear-data choice belong to settings.

Fresh test reports: 82 host tests, 9 runtime tests, 231 feature tests (322 total,
zero failures/errors/skips). This includes the 317 existing tests and five Konsist
architecture checks. Tests that previously accessed the host's internal builder
now provide cache JSON through its public boundary.

Negative graph check: an init script injected `:host -> :features`; the
`verifyModuleGraph` task rejected it with the expected forbidden-dependency error.
Konsist also rejects aliased imports of feature configuration/runtime from host,
and Android/host types from contracts.

The structural symbol move advances rule59 to rule60; schema60 and the persisted
JSON layout are preserved. Revert the rule version together with this migration.
These are compilation and unit/architecture results. Current-host scans, installed
hook behavior, final Release packaging and visual acceptance are separate gates.

## Symbol contracts

49 explicitly registered contracts now own 382 typed cache fields, scan dispatch,
restoration, cache validation, capabilities and diagnostic points. The resolver
only coordinates the scan session and cache. Descriptor keys provide the codec;
there are no parallel feature-field lists in the snapshot or its builder. Stable
supplemental scan candidates are declared by their owner. Input meme bar and forum
entry use shared required/optional dependency groups for capability and diagnostics.

All existing unit tests pass after migration. Five additional checks cover unique
contract registration, complete capability ownership, default/empty cache values,
required dependencies and exceptions during restoration. A temporary differential
oracle compared the prior and current capability/diagnostic implementations on 400
deterministic full, empty and partial snapshots, including scan errors. All states,
missing dependency sets and target descriptions agree; only ordering is normalized.
The old implementations are kept solely in local review evidence, not in the build.

This migration advances rule60 to rule61 while retaining schema60 and cache JSON
keys/layout. Roll back the migration and rule increment together. These checks do
not replace current-host scanning, installation or visual acceptance.

## Runtime ownership

All 169 native installation sites now use the owned runtime bridge. Registrations
are identified by feature, executable and purpose; explicit cooperating-group and
optional-point rollback policies remain in their owners. Installation records
measure retained native handles instead of treating dispatch as success. Uninstall
invalidates captured callbacks before calling the framework and retains ownership
on failure. An old handle cannot release a newer registration at the same site.

Runtime and feature unit tests pass, including seven new bridge tests for missing
targets, restoration failure, partial installation, distinct purposes, duplicate
installation, rollback failure, stale handles and callback replacement. The API
artifact is additionally available only to runtime tests; it remains compile-only
in production and is not packaged into the APK.

## Feature registration

The catalog explicitly orders 63 definitions covering the 70 existing installation
entries. Definitions own process, phase, settings/capability eligibility and lazy
installers. Performance installers share one preparation scope as before. Shared
browser/profile navigation is injected at composition; feature domains do not
import each other's installers. Symbol path readiness comes from the host
contracts, including partial post-ad paths and bottom-banner constructor paths.

Existing feature tests and three new registration/laziness checks pass. A temporary
oracle compared the old and new ordered installation lists for 400 deterministic
settings/symbol combinations across three processes and three phases (3,600
comparisons). Every list agrees. The oracle and its report remain local evidence;
the duplicate legacy implementations are excluded from production and tests.

The path-readiness migration advances rule61 to rule62, keeping schema60 and the
cache layout. Roll back this migration and the rule version together. Device
preflight succeeded after reconnection; it is not installation acceptance.

## Effective settings

Immutable saved choices, captured host capability states and remote policy are
separate inputs to `EffectiveSettingsPolicy`. It returns a runtime snapshot and
any required bottom-tab normalization. ConfigManager continues to own storage,
remote-policy writes, listeners, normalization writes and snapshot publication;
their existing refresh/save boundary is unchanged. Read-only value adapters reuse
the existing tab and liquid-glass codecs without making the pure policy read disk.

Five tests cover immutable input capture, remote restriction, unknown/partial
capabilities, gated normalization and publication isolation. Existing settings
tests pass. A temporary 400-case comparison against the old builder matched every
snapshot field and resulting preference write; the oracle remains local only.
The first test worker hit a JBR native-memory allocation failure. Limiting worker
and processor counts and selecting a higher heap base for this local process
allowed the complete feature suite to run successfully; no gate was removed.

## Settings ownership

SettingsMenuHook now owns only installation and the host entry callback. The menu
controller dispatches to feature forms; the scan controller owns progress, scan
choices and the queued post-scan warning. Export, clear-data and restart effects
have a separate owner. Glass editing state, palette controls and preview controls
are local to the glass form; shared input styling and keyboard handling are named
for their actual consumers. FeatureLifecycle and the sidebar call the controllers
directly, and the obsolete scan forwarding object is removed.

The feature suite passes with two additional architecture tests rejecting form
imports of installers, host discovery and menu/scan controllers, including aliased
imports. A local comparison matched 77 migrated function bodies after accounting
for visibility and shared-helper names. The removed version forwarding method and
the inlined post-scan dialog queue were reviewed separately. Preference keys, write
order, dialog countdowns, preview cleanup and restart timing are preserved. These
are source and test checks; device and visual acceptance remain separate.

## Glass page and view sessions

Ten view roles now declare their refresh and attach timing together. A weak view
entry owns its listener, queued tasks, bootstrap state and tracked-view membership,
replacing 17 parallel maps. Post activities also own their cached type, weak content
host and pending refresh in one page entry. Render callbacks resolve the current
view/page and published style; queued work does not strongly retain an Activity.

Eleven tests cover immediate versus deferred chrome refresh, reentrant host writes,
constructor posts, one-time bootstrap, repeated attach, detached late work, stale
cancelled tasks, current page/theme use, disablement and required style restoration.
The 0/160 ms bootstrap sequence and the separate refresh on search-box attachment
remain intact. Existing recycler/pre-draw observers, complex multi-pass input-bar
refreshes, tint and background owners retain their policies. This change does not
move host symbol rules; schema60/rule62 remain unchanged.

## Collection page sessions

CollectionSearchPageSession owns query intent, source account, full-load and disk
tokens, and pending first-page requests. The page registry owns weak presenter and
adapter associations. Account replacement retains query intent while discarding
old indexes and data readiness. Network results, disk restores, memory-cache
restores and dialog actions check their originating session before updating a page.
Completed requests cannot consume a later request's state.

Seventeen new tests cover page/account replacement, duplicate and stale completion,
disk invalidation and merged follow-up intent, association cleanup, forced first-page
FIFO behavior, and complete-cache eligibility after page exit. The source wiring
check still requires disk reads on their executor and rejection before UI delivery.
All 269 feature tests pass. Collection search remains capability-gated baseline
behavior; no new setting or background scan was introduced.

The full loader checks ownership before each page request and may persist an
already completed final response after page exit. Its immutable request retains
the original account and raw user ID; the cache context is captured before loading.
Page size, the three-account memory limit, cache JSON, first-page merge behavior
and executor order are unchanged. Transient network/disk closures retain their
existing host references; the session and request objects contain no host objects.
Symbol schema/rule versions remain 60/62. Device installation and visual acceptance
are separate from these tests.

## Final build and completeness checks

The complete `./gradlew verify` gate passes on the final source: module-graph
validation, 372 tests (87 host, 16 runtime, 269 features), all four Android modules'
Debug and Release Lint tasks, and the Release build. There are no test failures,
errors or skips and no Lint errors. Existing warning categories remain visible;
in particular, DexKit reports its bridge-creation and SortSwitch selection warnings.
No baseline, disabled check or manifest permission was added to pass the gate.

Two annotation boundaries required correction after extraction. The API 29
annotation follows the libxposed parameter access back to MainHook. Notification
posting retains the existing host-permission check and declares a binary-retained
permission requirement so app Lint can recognize it through the features library.
AndroidX annotation 1.9.1 is compile-only. The Release runtime dependency report
and APK class definitions contain no AndroidX annotation, Konsist, JUnit or
libxposed API implementation classes.

Negative registration probes temporarily removed CollectionContract and
CollectionSearchFeature from their actual registries. Their respective completeness
tests failed as expected. Both files were restored byte-for-byte before the final
positive gate. This supplements the forbidden module-dependency probe above.

The signed Release APK is 1,203,007 bytes. Its Xposed entry remains MainHook, its
certificate matches the previously installed module, and its sole native entry is
the uncompressed arm64-v8a DexKit library. Packaging inspection used the final
rebuilt artifact, not the earlier candidate. APKs, signatures, logs and machine
details remain in ignored local review evidence.

## Current-host symbols and cache

On Tieba 22.12.1.0 (369885440), an isolated Android process ran 29 checks using the
production contracts/host bytecode and the installed host's classes and resources.
The checks cover a cache miss without scanning, explicit resolution, memory and disk
cache restoration, startup `verifyFull=false`, full structural verification, JSON
round trips, old-rule invalidation without an automatic scan, and scan-session
cleanup. The checked artifact hashes still match the final build.

All 130 diagnostic Hook points are FOUND, all 47 capabilities are full, and there
are zero scan errors. All 382 cached descriptors match the pre-refactor rule59
baseline for the same host APK. Diagnostic and capability lines also match after
normalizing order. Current versions are schema60/rule62; JSON layout is unchanged.

After installing the final Release, the existing first-resume dialog rejected the
old rule59 cache and completed a visible scan. Its persisted result matches the
isolated scan in every JSON value except the creation timestamp. This separately
validates scanning in the actual module process and its optimized Release code.

## Installed behavior and remaining acceptance

The final signed APK was installed on the authorized local device; its installed
hash matches the verified artifact. Restart from the scan dialog succeeded. A
subsequent cold start restored the cache with `verifyFull=false` in both main and
image processes, without another scan. With the existing settings, captured
installation records show 22 successful entries and 91 retained native handles in
main, and three entries and 31 handles in the image process. These are logged
installation counts, not every hook: initialization before logging and later
dynamic adapter hooks are outside this snapshot. No recorded installation failed.

Observed UI checks covered the visible scan and completion, post-scan warning,
sidebar long-press settings menu, and collection search. An unmatched query loaded
the full collection using the existing page size, persisted its complete cache,
and displayed no matches. Clearing the query restored the list. Opening a real
post image entered ImageViewerActivity; swiping advanced from image 1/5 to 2/5,
and returning to the main page succeeded. Captured logs contain no module failure
or fatal exception for the tested processes.

Automatic sign-in was temporarily disabled during interactive checks. Original
user preferences were restored byte-for-byte, and the temporary upload and pending
preference backup are absent. The host's PushJobService restarted it immediately
after the first final stop. With the restored setting, the sign-in worker returned
the already completed daily report before any gateway request; its persisted state
is identical to the pre-test backup. A second stop and subsequent process checks
confirmed that all host processes exited. The final module remains installed.

User visual acceptance is still outstanding. Glass styling was disabled in the
existing settings, so its enabled appearance was not exercised on the device.
Account replacement, page-exit completion races and feature disablement have the
unit/session evidence described above, rather than comprehensive live coverage.
No manual sign-in, like, reply or post action was performed. These limits do not
turn symbol availability or successful installation into proof of every feature's
visible effect.
