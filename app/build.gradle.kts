plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.resqlink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.resqlink"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // Keep development/test data separate from installed release app data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.room.runtime)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}


// Export resolved versions for the repeatable OSV audit in scripts/security-audit.ps1.
tasks.register("securityDependencyInventory") {
    group = "verification"
    description = "Export resolved runtime, test, and build dependencies for vulnerability checking."
    doLast {
        val rows = sortedSetOf<String>()
        val scopedRows = sortedSetOf<String>()
        val selected = listOf("releaseRuntimeClasspath", "debugRuntimeClasspath",
            "debugUnitTestRuntimeClasspath", "debugAndroidTestRuntimeClasspath")
        configurations.filter {
            it.isCanBeResolved && (it.name in selected || it.name.contains("ProcessorClasspath") ||
                it.name.contains("CompilerClasspath") || it.name == "kotlinBuildToolsApiClasspath")
        }.forEach { configuration ->
            val resolution = configuration.incoming.resolutionResult
            check(resolution.allDependencies.none { it is org.gradle.api.artifacts.result.UnresolvedDependencyResult }) {
                "Cannot audit unresolved dependencies in ${configuration.name}"
            }
            resolution.allComponents.forEach { component ->
                component.moduleVersion?.let {
                    val row = "${it.group}:${it.name}\t${it.version}"
                    rows += row
                    scopedRows += "$row\t${configuration.name}"
                }
            }
        }
        (listOf(project, rootProject)).forEach { owner ->
            owner.buildscript.configurations.findByName("classpath")?.incoming?.resolutionResult?.allComponents?.forEach { component ->
                component.moduleVersion?.let {
                    val row = "${it.group}:${it.name}\t${it.version}"
                    rows += row
                    scopedRows += "$row\tbuildscript"
                }
            }
        }
        val destination = layout.buildDirectory.file("reports/security/dependencies.tsv").get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(rows.joinToString("\n") + "\n")
        destination.resolveSibling("dependency-scopes.tsv").writeText(scopedRows.joinToString("\n") + "\n")
        println("Wrote ${rows.size} dependencies to ${destination}")
    }
}
