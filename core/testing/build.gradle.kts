plugins { alias(libs.plugins.kmp.core) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            api(project(":data:repository"))
            api(project(":domain:usecases"))
        }
        // Screen audits for the Robolectric screenshot tests (androidUnitTest of each feature).
        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.test.junit4)
        }
    }
}
