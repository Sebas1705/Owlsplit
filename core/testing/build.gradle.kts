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
    }
}
