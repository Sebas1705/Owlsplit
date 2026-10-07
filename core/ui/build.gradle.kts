plugins {
    alias(libs.plugins.kmp.core)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.compose.icons.tabler)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            api(project(":core:money"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidUnitTest.dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    "debugImplementation"(libs.androidx.compose.ui.test.manifest)
}

compose.resources {
    packageOfResClass = "es.sebas1705.owlsplit.core.ui.resources"
    publicResClass = false
}

// Compose UI tests need ui-test-manifest, which only the debug variant gets: host tests run on debug.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }
