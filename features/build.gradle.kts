plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.forbidad4tieba.features"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
dependencies {
    implementation(project(":contracts"))
    implementation(project(":host"))
    implementation(project(":runtime"))
    compileOnly(libs.xposed.api)
    compileOnly(libs.androidx.annotation)
    testImplementation(libs.xposed.api)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
}

val featureBuildDirectory = layout.buildDirectory
tasks.withType<Test>().configureEach {
    doFirst {
        val outputPath = featureBuildDirectory.get().asFile.toPath()
        systemProperty("features.production.classpath", classpath.filter {
            it.toPath().startsWith(outputPath) && it !in testClassesDirs.files
        }.asPath)
    }
}
