# Symbol discovery

The key invariant is that renaming a host target or adding a same-shape distractor
must not make the scanner silently choose a different target. A missing or
ambiguous result disables the affected point and preserves independent points.

## Discovery and ownership

DexKit is the only source of Dex class inventory and bytecode evidence. Reflection
checks inheritance, interfaces, modifiers, parameter and return types, fields and
constructors. It also restores previously selected descriptors. There is no
`DexFile` inventory, guessed anonymous-class suffix or hook-side scan fallback.

Each feature's symbol contract owns scanning, descriptor fields, JSON round trips,
restoration, cache validation, points and capabilities. Rules live in feature
scanner files. `ScanRules` and `ScanDexQueries` contain shared mechanisms only.
Hooks consume restored targets during installation and use published settings and
targets in callbacks.

`ScanDexQueries` borrows the active `HookSymbolScanContext` bridge. The existing
scan session owns its lifetime, caches failed opens as well as successful ones,
and closes it when the scan exits. Queries must not open a bridge in a callback
or retain DexKit result objects in the published symbol snapshot.

## Evidence and selection

A rule combines structure with a semantic anchor: a protocol key, meaningful
string, resource name, stable API call, or a field/call relationship traced from
such an anchor. Signature shape alone is insufficient for an obfuscated target.

| Target | Evidence used by the migrated rules |
| --- | --- |
| Home tab fields | Factory writes, JSON parser keys, picture-tab name parser, URL composition and identity reads |
| Feed binding and load-more | Card-data structure, view binding calls and load-more notification/diagnostic path |
| Copy popup content | Dialog/view structure and listener setup reading the content container |
| PB scroll listeners | SDK listener signature, owner field and the owner's construction/registration path |
| Pull gesture state setter | Public refresh API and touch handling reach the same two-boolean setter |
| Crash report method | Uncaught-exception entry calls a Throwable reporter that invokes the SDK dump callbacks |
| AI emoji and game floating bar | Capsule-view access and game-installation checks; no preliminary class-name search |
| Capsule and copy-title fields | Actual View/TextView inheritance, with field reads and writes linking the behavior |
| Nested parser/message owners | JVM enclosing-class metadata, not a binary-name prefix |

Sample obfuscated class, method and field names cannot filter, score or break ties.
Name length, spelling and reflection enumeration order cannot select a winner
either. Stable SDK APIs, verified readable host APIs, protocol/resource names and
module-owned keys remain valid anchors. Sorting selected targets or diagnostic
output by their names is harmless when every selected target is retained.

Names discovered by DexKit or reflection still appear in descriptors and caches.
Restoring that exact descriptor, including its signature, is expected; copying its
current spelling into the rule is not. DexKit does not require sample names to
perform semantic discovery.

Single-target rules require exactly one candidate. Scored rules require both an
absolute minimum score and a positive minimum gap. A name cannot exempt a candidate
from ambiguity rejection. Multi-target rules must validate and retain every target
in their defined scope; they cannot truncate ties to fit a scan budget. Missing
and ambiguous results must include a useful diagnostic.

This migration advances `DEXKIT_RULE_VERSION` from 62 to 64, including the second
audit's removal of type-name suffix and partial-name discovery. The JSON schema stays
at 60 because the contract's existing text fields encode the two added optional
targets. Prior rule caches are rejected; this does not start a background scan.
Rollback of a scanner migration must include its rule version.

## Verification

`SymbolDiscoverySourceTest` scans production Kotlin in `contracts`, `host`, `features`,
`runtime` and `app` during `verify`. Its lexical guard rejects known short-name
filters, reflection hints, constant/alias forms, opaque host class literals,
preferred-name parameters, name-length bias, guessed suffix ranges and `DexFile`.
Scanner/DexKit sources also reject type-name suffix/substring predicates, partial
DexKit name matchers, simple-name identity checks and common alias/regex variants.
Package namespaces, stable resource namespaces and diagnostic formatting are not
target-spelling evidence.
The guard ignores comments and interpolated strings and allows published Kotlin
Metadata members. It is a regression guard, not a complete Kotlin semantic or
data-flow analyzer; passing it does not prove that every rule has sound evidence.

Selection tests cover renamed targets, old-name distractors, ties, insufficient
score/gap, missing semantic evidence and invalid signatures. Contract tests cover
JSON round trips, exact descriptor restoration and optional-point isolation.

Run `./gradlew verify` for architecture/unit tests, Debug and Release Lint and the
Release build. Separately run the production scanner against the current host and
compare every descriptor, including optional ones, with the recorded baseline.
Point and capability counts alone cannot detect lost optional targets. Scanner
and cache checks do not establish actual hook installation or visual acceptance;
record those separately. APKs, raw scan output and investigation runners stay local.

## When reflection validation runs

During an explicit scan, `HookSymbolResolver.scan` creates the scan session and
invokes each feature contract. Its scanner checks the reflected candidate's
inheritance, modifiers and member signatures before publishing descriptors.
For example, capsule and title fields must actually inherit View/TextView, and
the AI bind method must belong to a concrete FrameLayout with the expected
instance signature. DexKit evidence and reflection checks both belong to this
scan; a spelling match is never a substitute for either.

SortSwitch also validates its Paint field and Canvas/Path slide group before
publishing the scan result. Background failure preserves a valid slide group;
invalid slide members preserve a valid background target. Its former Dex-only
scan must not rely on a later cache/installation check to establish this shape.

Cache loading is a separate operation. Normal startup requests lightweight
validation; in-memory snapshots and an already verified fingerprint can also
bypass full cache validation. The full path invokes the contracts' cache
validators when necessary. Therefore a usable cache does not mean that all
reflection checks ran again on that startup.

Before installation, contracts restore selected descriptors and check the
required signatures again. These lookups use discovered names and expected
types; they do not choose a new candidate. Hook callbacks consume the restored
targets. Runtime reflection for stable APIs or view/object access can still
exist, but obfuscated target discovery and DexKit queries do not belong there.
