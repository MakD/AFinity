import com.android.build.api.dsl.ApplicationExtension
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
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

configure<ApplicationExtension> {
    namespace = "com.makd.afinity"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.makd.afinity"
        minSdk = 35
        targetSdk = 36
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
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            buildConfigField("boolean", "DEBUG", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            resValue("string", "app_name", "AFinity Nightly")
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
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources { generateLocaleConfig = true }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-Xjvm-default=all",
            "-Xcontext-parameters",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk)
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libmpv)
    implementation(libs.lottie.compose)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.play.services.cast.framework)
    implementation(libs.reorderable)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.richtext.commonmark)
    implementation(libs.richtext.ui)
    implementation(libs.richtext.ui.material3)
    implementation(libs.timber)
    implementation(libs.tink.android)
    implementation(platform(libs.androidx.compose.bom))
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)
    ksp(libs.kotlin.metadata.jvm)
}
