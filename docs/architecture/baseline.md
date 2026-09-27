# Refactoring baseline

Frozen starting point: `c2c19acba320d538627e2cf794463d3cbe35a008`.

- Host: com.baidu.tieba 22.12.1.0 / 369885440.
- Module at that commit: 26092602 / 46; cache schema60 / rule59.
- AGP 9.2.1, Gradle 9.4.1, JBR 21; libxposed API 102.0.0 and DexKit 2.2.0.
- 317 existing tests passed in saved reports. The last planning run reported
  UP-TO-DATE; those results were not freshly executed in that run.
- Debug / Release Lint: zero errors, 65 / 64 warnings in the planning run.
- Earlier current-host evidence: 130 formal points FOUND, 47 capabilities full,
  zero scan errors. This is historical evidence, not validation of new code.
- Preflight on 2026-09-26 at 18:31 +08:00: MT 0.2.0 initialized, 49 tools
  discovered and APK listing succeeded. LSPilot 1.1.2 initialized, 40 adapter
  tools discovered, status and target manifest reads succeeded. Device host
  version matches the baseline. No settings or business actions were changed.

Current versions must always be read from `app/build.gradle.kts` and
`HookSymbols.CACHE_SCHEMA_VERSION` / `DEXKIT_RULE_VERSION`. Historical acceptance
reports retain their original meaning. The baseline does not imply acceptance of
later source, installation state, or visual behavior.

Raw logs, device addresses/configuration, APKs and exploratory scripts remain
local under ignored directories. Commit only portable rules and concise evidence.
