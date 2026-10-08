plugins {
    alias(libs.plugins.kmp.feature)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundles.compose.core)
            implementation(libs.compose.icons.tabler)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":core:common"))
            implementation(project(":core:ui"))
            implementation(project(":core:datastore"))
            implementation(project(":data:models"))
            implementation(project(":domain:usecases"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":core:testing"))
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
    packageOfResClass = "es.sebas1705.owlsplit.feature.home.resources"
    publicResClass = false
}

// Compose UI tests need ui-test-manifest, which only the debug variant gets: host tests run on debug.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }
