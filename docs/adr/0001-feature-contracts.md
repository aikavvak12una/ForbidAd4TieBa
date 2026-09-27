# ADR 0001: Feature ownership with five build modules

Status: accepted, 2026-09-26. Implementation proceeds on one refactoring branch.

Adding a host-dependent feature currently repeats its dependencies across the
symbol model, cache, validator, status formatter, capability map and installer.
Those copies can disagree without causing a compilation error. Large entry points
also mix host discovery, user interface work and runtime effects.

Use five Gradle modules: contracts, host, runtime, features and app. Contracts is
pure Kotlin/JDK. Host and runtime each depend only on contracts. Features depends
on those three modules. App composes and packages them. Gradle enforces this graph;
Konsist tests check boundaries within the features module.

Each SymbolContract owns its cached descriptors, codec, restored targets,
structural validation and required/optional dependencies. Status and capability
are projections of those declarations. The registry only aggregates contracts.
FeatureDefinition records process, phase and settings requirements and creates
the installer lazily. Installation reports actual handles independently from
symbol availability and feature effects.

Stored user choices, host capabilities and remote policy are separate inputs to
a pure effective-settings policy. Publishing a snapshot keeps the existing save
and restart boundary. Settings owns dialogs, scan choices and restart prompts;
host scanning returns data. Page resources and requests have explicit owners.

Preserve setting keys, cache JSON layout, hook order, shared hooks, partial
capabilities, callback activation generations, and valid off-page cache writes.
Do not add background scans, compatibility branches or a runtime framework.
Structural symbol migrations increment the rule version and roll back with it.

Design references (no source copied):

- [QAuxiliary IDynamicHook](https://github.com/cinit/QAuxiliary/blob/4791fc9615b766808aae31b28d67854d02f5fac5/app/src/main/java/io/github/qauxv/base/IDynamicHook.kt): distinct user intent, availability, preparation and initialization. Its additional license restrictions preclude treating it as freely reusable implementation code.
- [Now in Android modularization](https://github.com/android/nowinandroid/blob/a49ed253d75e61a2b6ab80a8da677b57437b08eb/docs/ModularizationLearningJourney.md): composition at the application boundary and explicit dependency direction.
- [Konsist](https://github.com/LemonAppDev/konsist/blob/b21a9e15bd7237a611b3d5211cc170362f3ac7cf/README.md): executable architecture rules. Version 0.17.3 is a test-only Apache-2.0 dependency and is excluded from APK runtime dependencies.
