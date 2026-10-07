plugins {
    alias(libs.plugins.kmp.data)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.sqldelight.jvm.driver)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("es.sebas1705.owlsplit.data.local.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Snapshots of every released schema (regenerate with generateCommonMainAppDatabaseSchema,
            // run on its own); verifySqlDelightMigration checks
            // that migrations (n.sqm) turn snapshot n into n + 1.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
