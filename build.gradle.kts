import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import groovy.json.JsonSlurper

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

// Keep generated files, signing keys and caches out of the source/owner's home.
val registry = rootDir.resolve("versions.json")
check(registry.isFile) { "Missing central versions.json; do not guess release versions" }
val versions = JsonSlurper().parse(registry) as Map<*, *>
val appVersions = versions["apps"] as? Map<*, *> ?: emptyMap<Any, Any>()

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<BaseAppModuleExtension> {
            namespace = "dev.wonderdesk.${project.name}"
            compileSdk = 35
            defaultConfig {
                applicationId = "dev.wonderdesk.${project.name}"
                minSdk = 29
                targetSdk = 35
                // The launcher, every APK, and the ecosystem are independently versioned.
                val versionKey = mapOf("controlroom" to "control-room", "apphub" to "app-hub")[project.name] ?: project.name
                versionName = appVersions[versionKey] as? String ?: error("Missing independent app version: $versionKey")
                require(versionName!!.matches(Regex("0\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) { "App versions must be numeric 0.x.x" }
                val parts = versionName!!.split('.').map { it.toInt() }
                require(parts[1] <= 2_000_000 && parts[2] < 1000) { "Android versionCode limit; bump the minor version before patch 1000" }
                versionCode = parts[0] * 1_000_000 + parts[1] * 1000 + parts[2]
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildFeatures { compose = true }
            composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
            // Speed: the APK people install is optimised (R8, not debuggable, so Android runs it at full speed and applies
            // the Compose baseline profiles). It stays signed with the same key, so it updates over earlier versions.
            // Names are kept (no obfuscation) so crash reports stay readable.
            buildTypes {
                getByName("debug") {
                    isDebuggable = false
                    isMinifyEnabled = true
                    isShrinkResources = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootDir.resolve("wonderdesk-rules.pro"))
                }
            }
            dependencies {
                add("implementation", project(":shared"))
                add("androidTestImplementation", "androidx.test:runner:1.6.2")
                add("androidTestImplementation", "androidx.test.ext:junit:1.2.1")
                add("androidTestImplementation", "androidx.test.uiautomator:uiautomator:2.3.0")
            }
        }
    }
    plugins.withId("com.android.base") {
        extensions.configure<BaseExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
        // Strong skipping: screens skip recomposing when nothing they use has changed.
        kotlinOptions.freeCompilerArgs += listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true")
    }
}
