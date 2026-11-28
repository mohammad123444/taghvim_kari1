plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Values injected by GitHub Actions (all optional for local builds).
val ciVersionName: String = System.getenv("APP_VERSION_NAME") ?: "2.8.0"
// Derived from the version (1.4.0 -> 10400) so debug and release builds always compare correctly,
// otherwise Android refuses the update as a "downgrade".
val ciVersionCode: Int = ciVersionName.split(".").map { it.toIntOrNull() ?: 0 }.let {
    it.getOrElse(0) { 0 } * 10000 + it.getOrElse(1) { 0 } * 100 + it.getOrElse(2) { 0 }
}
// Version rule: the 2nd and 3rd numbers only go from 0 to 9 (1.2.9 -> 1.3.0, 1.9.9 -> 2.0.0).
ciVersionName.split(".").map { it.toIntOrNull() ?: 0 }.let { parts ->
    require(parts.getOrElse(1) { 0 } <= 9 && parts.getOrElse(2) { 0 } <= 9) {
        "Invalid version $ciVersionName: the 2nd and 3rd numbers must be 0..9 (e.g. 1.9.9 is followed by 2.0.0)."
    }
}
// "owner/repo" of the GitHub repository whose Releases are checked for updates.
val updateRepo: String = (project.findProperty("updateRepo") as String?) ?: System.getenv("GITHUB_REPOSITORY") ?: ""

android {
    namespace = "ir.taghvimkari.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.taghvimkari.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        // One fixed key for debug AND release builds: Android only installs an update over an
        // installed app when both APKs are signed with the same key.
        // A private key from GitHub Secrets (KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS, set by the workflows)
        // is used when present; otherwise the old key that is stored inside the project.
        create("shared") {
            val secretKey = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
            if (secretKey != null && secretKey.exists()) {
                storeFile = secretKey
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "taghvim"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            } else {
                storeFile = file("signing/taghvim.jks")
                storePassword = "taghvim-shared"
                keyAlias = "taghvim"
                keyPassword = "taghvim-shared"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for plain JVM tests (the Android one is only a stub there): used by the backup file test.
    testImplementation("org.json:json:20240303")
}
