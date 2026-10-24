import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasProductionSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

// Prefer the release workflow's requested tag, then the pushed tag.
// Accept both "v1.2.0" and the existing "v.1.1.0" tag style.
val releaseTag = (
    System.getenv("RELEASE_TAG_INPUT")?.takeIf { it.isNotBlank() }
        ?: System.getenv("GITHUB_REF_NAME")
    ).orEmpty().removePrefix("v").removePrefix(".")
val parsedVersion = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(releaseTag)

// Reserve 10,000 versionCode values per patch version.
// A production release uses the final slot of its patch range. Development builds
// use the next patch-range slot plus the workflow run number: this lets a dev APK
// upgrade the current release, while the next patch release can upgrade that dev APK.
val developmentVersionName = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "1.2.0-dev.0"
val developmentBuildNumber = System.getenv("APP_BUILD_NUMBER")?.toIntOrNull() ?: 0
val appVersionName = parsedVersion?.value ?: developmentVersionName
val appVersionCode = (parsedVersion?.value ?: developmentVersionName)
    .let { versionName ->
        val version = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-.*)?$").matchEntire(versionName)
            ?: error("Invalid app version name: $versionName")
        val (major, minor, patch) = version.destructured
        val baseCode = major.toInt() * 100_000_000 +
            minor.toInt() * 1_000_000 +
            patch.toInt() * 10_000
        if (parsedVersion != null) {
            baseCode + 9_999
        } else {
            require(developmentBuildNumber in 0..9_998) {
                "Development build number must be between 0 and 9998"
            }
            baseCode + 10_000 + developmentBuildNumber
        }
    }

// Generate the adaptive-icon foreground from the original transparent PNG.
// The source image remains untouched in Screenshot/Minimalist Dislauncher Logo on Black.png.
val generatedLauncherResources = layout.buildDirectory.dir("generated/res/launcherIcon")
val generateLauncherForeground by tasks.registering(Copy::class) {
    from(rootProject.file("Screenshot/Minimalist Dislauncher Logo on Black.png"))
    into(generatedLauncherResources.map { it.dir("drawable-nodpi") })
    rename { "dislauncher_icon_foreground.png" }
}

// Keep font sources checked into the repository and decode them locally into Android resources.
// This avoids runtime and build-time font downloads.
val generatedBundledFonts = layout.buildDirectory.dir("generated/res/bundledFonts")
val generateBundledFonts by tasks.registering {
    val fontSourceFiles = fileTree(layout.projectDirectory.dir("font-source")) {
        include("*.ttf.b64")
    }
    inputs.files(fontSourceFiles)
    outputs.dir(generatedBundledFonts)
    doLast {
        val outputDir = generatedBundledFonts.get().dir("font").asFile
        outputDir.mkdirs()
        fontSourceFiles.files.forEach { source ->
            val outputFile = outputDir.resolve(source.name.removeSuffix(".b64"))
            outputFile.writeBytes(Base64.getMimeDecoder().decode(source.readText()))
        }
    }
}

val generatedChangelogAssets = layout.buildDirectory.dir("generated/assets/changelog")
val prepareChangelogAsset by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(generatedChangelogAssets)
    rename { "changelog.md" }
}

android {
    namespace = "com.insomdroid.dislauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.insomdroid.dislauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasProductionSigning) {
            create("production") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Main-branch and release builds use the production key when supplied.
            // Pull-request CI builds use debug signing because production secrets are unavailable.
            signingConfig = if (hasProductionSigning) {
                signingConfigs.getByName("production")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets.getByName("main").res.srcDir(generatedLauncherResources)
    sourceSets.getByName("main").res.srcDir(generatedBundledFonts)
    sourceSets.getByName("main").assets.srcDir(generatedChangelogAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateLauncherForeground, prepareChangelogAsset, generateBundledFonts)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.kizitonwose.calendar:compose:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
