import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

configure<TestExtension> {
    namespace = "com.makd.afinity.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 35
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            allDevices {
                create<ManagedVirtualDevice>("pixel6Api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.junit)
}
