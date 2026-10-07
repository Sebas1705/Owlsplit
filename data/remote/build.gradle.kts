import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kmp.data)
}

kotlin {
    androidTarget {
        // Instrumented tests share the `test` source-set tree so they can live in
        // androidInstrumentedTest and run on a device with connectedDebugAndroidTest.
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
            api(project(":data:repository"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidInstrumentedTest.dependencies {
            implementation(project(":data:local"))
            implementation(libs.sqldelight.android.driver)
            implementation(kotlin("test"))
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
