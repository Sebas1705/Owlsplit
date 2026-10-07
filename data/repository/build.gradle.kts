plugins { alias(libs.plugins.kmp.data) }

kotlin {
    compilerOptions {
        optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":data:models"))
            implementation(project(":data:local"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
