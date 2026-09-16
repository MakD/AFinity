import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties
import java.util.regex.Pattern
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.androidx.baselineprofile)
    id("kotlin-parcelize")
}

val appName = project.property("app.name") as String
val appVersionName = project.property("app.versionName") as String
val appVersionCode = project.property("app.versionCode") as String

base { archivesName.set("afinity-v${appVersionName}") }

aboutLibraries {
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.GROUP

        exclusionPatterns.addAll(
            Pattern.compile("org\\.jetbrains\\.compose.*"),
            Pattern.compile("org\\.jetbrains\\.androidx.*"),
        )
    }
}

configure<ComposeCompilerGradlePluginExtension> {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_compiler_config.conf"))

    if (providers.gradleProperty("composeMetrics").orNull == "true") {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

configure<ApplicationExtension> {
    namespace = "com.makd.afinity"
    compileSdk = 37

    val keyPropertiesFile = rootProject.file("key.properties")
    val hasReleaseKeystore = keyPropertiesFile.exists()

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                val keyProperties = Properties().apply { load(keyPropertiesFile.inputStream()) }
                storeFile = file(keyProperties["storeFile"] as String)
                storePassword = keyProperties["storePassword"] as String
                keyAlias = keyProperties["keyAlias"] as String
                keyPassword = keyProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.makd.afinity"
        minSdk = 35
        targetSdk = 37
        versionCode = appVersionCode.toInt()
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APP_NAME", "\"${appName}\"")
        buildConfigField("String", "VERSION_NAME", "\"${appVersionName}\"")
        buildConfigField("int", "VERSION_CODE", appVersionCode)
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField("boolean", "DEBUG", "true")
            buildConfigField("boolean", "IS_NIGHTLY", "false")
            buildConfigField("String", "BUILD_TIME", "\"\"")
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("boolean", "DEBUG", "false")
            buildConfigField("boolean", "IS_NIGHTLY", "false")
            buildConfigField("String", "BUILD_TIME", "\"\"")
            signingConfig =
                if (hasReleaseKeystore) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            resValue("string", "app_name", "AFinity Nightly")
            buildConfigField("boolean", "IS_NIGHTLY", "true")
            buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
            excludes += "/META-INF/INDEX.LIST"
        }
        jniLibs { pickFirsts += "**/libc++_shared.so" }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    androidResources { generateLocaleConfig = true }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

room { schemaDirectory("$projectDir/schemas") }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-jvm-default=no-compatibility",
        )
    }
}

configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        val buildType = variant.buildType ?: return@onVariants
        if (buildType != "release" && buildType != "nightly") return@onVariants

        val mappingFile = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
        val variantName = variant.name.replaceFirstChar { it.uppercase() }

        val archiveMapping =
            tasks.register<Zip>("archive${variantName}Mapping") {
                group = "reporting"
                description = "Archives the R8 mapping for $buildType builds."
                from(mappingFile)
                archiveFileName.set(
                    "afinity-v$appVersionName-$appVersionCode-${variant.name}-mapping.zip"
                )
                destinationDirectory.set(rootProject.layout.projectDirectory.dir("mapping-archive"))
                onlyIf { mappingFile.orNull?.asFile?.exists() == true }
            }

        tasks
            .matching { it.name == "assemble$variantName" }
            .configureEach { finalizedBy(archiveMapping) }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk)
    debugImplementation(libs.leakcanary.android)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.window.size.class1)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.compose.material3)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.ass.media)
    implementation(libs.blurhash)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.cache.control)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.compose.pager.indicator)
    implementation(libs.hilt.android)
    implementation(libs.jellyfin.core)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libmpv)
    implementation(libs.lottie.compose)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.play.services.cast.framework)
    implementation(libs.reorderable)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.richtext.commonmark)
    implementation(libs.richtext.ui)
    implementation(libs.richtext.ui.material3)
    implementation(libs.slf4j.api)
    implementation(libs.socketio) { exclude(group = "org.json", module = "json") }
    implementation(libs.timber)
    implementation(libs.tink.android)
    implementation(platform(libs.androidx.compose.bom))
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)
    ksp(libs.kotlin.metadata.jvm)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)

    testImplementation(libs.junit)

    baselineProfile(project(":benchmark"))
}

val cancellationRethrowAllowlist =
    setOf(
        "data/database/AfinityTypeConverters.kt",
        "data/manager/DownloadNotificationManager.kt",
        "data/models/jellyseerr/JellyseerrRequest.kt",
        "data/models/jellyseerr/SearchResultItem.kt",
        "data/repository/audiobookshelf/AbsProgressSyncScheduler.kt",
        "data/repository/audiobookshelf/AudiobookshelfEpisodeMappers.kt",
        "data/storage/StorageLocationProvider.kt",
        "data/sync/UserDataSyncScheduler.kt",
        "di/NetworkModule.kt",
        "player/common/AudioEqualizerManager.kt",
        "player/mpv/MPVPlayer.kt",
        "ui/audiobookshelf/libraries/AudiobookshelfLibrariesViewModel.kt",
        "ui/components/AsyncImage.kt",
        "ui/components/HeroCarousel.kt",
        "ui/person/PersonViewModel.kt",
        "ui/player/Extensions.kt",
        "ui/player/PlayerActivity.kt",
        "ui/player/utils/VolmeManager.kt",
        "ui/requests/SeerrMediaDetailScreen.kt",
        "ui/settings/player/PlayerOptionsScreen.kt",
        "ui/settings/servers/utils/Formatters.kt",
        "ui/utils/IntentUtils.kt",
    )

tasks.register("checkCancellationRethrow") {
    group = "verification"
    description =
        "Fails if a catch (e: Exception) is missing a CancellationException rethrow above it."

    val sourceRoot = layout.projectDirectory.dir("src/main/java/com/makd/afinity").asFile
    val allowlist = cancellationRethrowAllowlist
    inputs.dir(sourceRoot)

    doLast {
        val violations = mutableListOf<String>()
        sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relative = file.relativeTo(sourceRoot).invariantSeparatorsPath
                if (relative in allowlist) return@forEach
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    if (!line.contains("catch (e: Exception)")) return@forEachIndexed
                    val guarded =
                        index >= 2 &&
                            lines[index - 2].contains("catch (e: CancellationException)") &&
                            lines[index - 1].trim() == "throw e"
                    if (!guarded) violations += "$relative:${index + 1}"
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "Cancellation rethrow missing at ${violations.size} site(s). " +
                            "Catching Exception swallows coroutine cancellation, so the coroutine " +
                            "keeps running until its next suspension point."
                    )
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("Add directly above each catch:")
                    appendLine("    } catch (e: CancellationException) {")
                    appendLine("        throw e")
                    appendLine()
                    appendLine(
                        "If the file contains no coroutines, add it to " +
                            "cancellationRethrowAllowlist in app/build.gradle.kts instead."
                    )
                }
            )
        }
    }
}
