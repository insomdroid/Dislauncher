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
val appVersionName = parsedVersion?.value ?: "1.1.0"
val appVersionCode = parsedVersion?.let { match ->
    val (major, minor, patch) = match.destructured
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
} ?: 10_100

// Generate the adaptive-icon foreground from the original transparent PNG.
// The source image remains untouched in Screenshot/dislauncher.png.
val generatedLauncherResources = layout.buildDirectory.dir("generated/res/launcherIcon")
val generateLauncherForeground by tasks.registering(Copy::class) {
    from(rootProject.file("Screenshot/dislauncher.png"))
    into(generatedLauncherResources.map { it.dir("drawable-nodpi") })
    rename { "dislauncher_icon_foreground.png" }
    doLast {
        // Preserve the uploaded source PNG and brighten only the generated adaptive foreground.
        val outputFile = generatedLauncherResources.get()
            .file("drawable-nodpi/dislauncher_icon_foreground.png").asFile
        val sourceImage = javax.imageio.ImageIO.read(outputFile)
            ?: throw GradleException("Unable to read generated launcher icon foreground")
        val image = java.awt.image.BufferedImage(
            sourceImage.width,
            sourceImage.height,
            java.awt.image.BufferedImage.TYPE_INT_ARGB
        )
        for (y in 0 until sourceImage.height) {
            for (x in 0 until sourceImage.width) {
                val argb = sourceImage.getRGB(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha == 0) continue
                fun brighten(channel: Int): Int =
                    (24.0 + 231.0 * Math.pow(channel / 255.0, 0.78)).toInt().coerceIn(0, 255)
                val red = brighten((argb ushr 16) and 0xFF)
                val green = brighten((argb ushr 8) and 0xFF)
                val blue = brighten(argb and 0xFF)
                image.setRGB(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            }
        }
        if (!javax.imageio.ImageIO.write(image, "png", outputFile)) {
            throw GradleException("Unable to write brightened launcher icon foreground")
        }
    }
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
            // Main-branch CI builds remain debug-signed; the tag-release workflow
            // must supply all production signing values or it fails before building.
            signingConfig = if (hasProductionSigning) {
                signingConfigs.getByName("production")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets.getByName("main").res.srcDir(generatedLauncherResources)

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
    dependsOn(generateLauncherForeground)
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
}
