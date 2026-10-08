plugins { alias(libs.plugins.kmp.domain) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:money"))
            implementation(project(":core:common"))
            api(project(":data:models"))
            implementation(project(":data:repository"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
