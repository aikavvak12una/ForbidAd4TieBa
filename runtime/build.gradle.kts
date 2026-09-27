plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.forbidad4tieba.runtime"
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
    compileOnly(libs.xposed.api)
    testImplementation(libs.junit)
    testImplementation(libs.xposed.api)
}
