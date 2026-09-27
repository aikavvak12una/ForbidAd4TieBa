// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// The same entry point is used locally, on pull requests, and before signing releases.
tasks.register("verify") {
    group = "verification"
    description = "Run architecture and unit tests, both Lint variants, and build the release APK."
    dependsOn("verifyModuleGraph", ":contracts:test", ":app:assembleRelease")
    listOf("app", "host", "runtime", "features").forEach { module ->
        dependsOn(":$module:testDebugUnitTest", ":$module:lintDebug", ":$module:lintRelease")
    }
}

val allowedProjectDependencies = mapOf(
    ":contracts" to emptySet(),
    ":host" to setOf(":contracts"),
    ":runtime" to setOf(":contracts"),
    ":features" to setOf(":contracts", ":host", ":runtime"),
    ":app" to setOf(":contracts", ":host", ":runtime", ":features"),
)

tasks.register("verifyModuleGraph") {
    group = "verification"
    description = "Reject reverse project dependencies, including unused ones."
    doLast {
        subprojects.forEach { owner ->
            val allowed = allowedProjectDependencies.getValue(owner.path)
            owner.configurations.forEach { configuration ->
                configuration.dependencies.withType<org.gradle.api.artifacts.ProjectDependency>().forEach { dependency ->
                    check(dependency.path == owner.path || dependency.path in allowed) {
                        "Forbidden project dependency: ${owner.path} -> ${dependency.path} (${configuration.name})"
                    }
                }
            }
        }
    }
}

subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("project.root", rootProject.projectDir.absolutePath)
    }
}
