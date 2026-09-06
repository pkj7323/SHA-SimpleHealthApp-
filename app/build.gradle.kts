import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 서명 정보는 keystore.properties(gitignore 대상)에서만 읽는다. 커밋되는 파일엔 비밀번호를 두지 않는다.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("app/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.pkj.leanbulk"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pkj.leanbulk"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room이 버전별 스키마를 JSON으로 남긴다. 마이그레이션을 쓰려면 "이전 버전이 어떤 모양이었나"의
    // 기준이 있어야 하는데 그게 이 파일들이다. 반드시 커밋해야 한다.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // 계측 테스트에서 옛 버전 DB를 만들어 마이그레이션을 검증할 수 있도록 스키마를 넣어준다.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // JSON 백업/복원 · 동기화 스냅샷 포맷
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // xlsx 내보내기 전용 (쓰기 전용, 안드로이드에서 가볍게 동작)
    implementation("org.dhatim:fastexcel:0.17.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // JSON 왕복이 무손실인지 검증하는 계측 테스트용
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    // 컴포저블을 직접 렌더링해 조작·검증하는 테스트용. 화면 하나 바꿀 때마다 에뮬레이터로
    // 스크린샷을 찍어 눈으로 확인하는 대신, 여기서 자동으로 반복 검증한다.
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // ui-test-junit4가 딸려오는 espresso-core 3.5.0은 API 37(에뮬레이터)의 InputManager와
    // 안 맞아 클릭·입력이 전부 NoSuchMethodException으로 죽는다. 명시로 버전을 올려 덮어쓴다.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

/**
 * 스키마를 만드는 ksp 태스크가, 그 폴더를 테스트 에셋으로 가져가는 태스크보다 먼저 돌게 한다.
 *
 * 스키마 폴더(`app/schemas`)는 빌드 산출물 바깥이라 Gradle이 둘의 관계를 모른다. 안 걸어두면
 * 버전을 올린 직후 첫 실행에서 새 스키마가 테스트 APK에 안 들어가고, 마이그레이션 테스트가
 * 엉뚱한 이유("스키마 파일 없음")로 실패한다. **틀린 이유로 실패하는 안전장치는 곧 무시하게
 * 되므로** 첫 실행부터 제대로 된 이유가 나와야 한다.
 */
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("AndroidTestAssets") }
    .configureEach {
        // mergeDebugAndroidTestAssets -> kspDebugKotlin
        val variant = name.removePrefix("merge").removeSuffix("AndroidTestAssets")
        dependsOn("ksp${variant}Kotlin")
    }
