/*
 * Designed and developed by 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */
@file:Suppress("UnstableApiUsage")

import java.io.File
import java.io.FileInputStream
import java.util.*

plugins {
  id("skydoves.android.application")
  id("skydoves.android.application.compose")
  id("skydoves.android.hilt")
  id("skydoves.spotless")
  id("kotlin-parcelize")
  id("dagger.hilt.android.plugin")
  id("com.google.devtools.ksp")
  id(libs.plugins.google.secrets.get().pluginId)
  id(libs.plugins.baseline.profile.get().pluginId)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = File(rootProject.rootDir, "keystore.properties")
if (keystorePropertiesFile.exists()) {
  keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
  namespace = "com.skydoves.chatgpt"
  compileSdk = Configurations.compileSdk

  defaultConfig {
    applicationId = "com.skydoves.chatgpt"
    minSdk = Configurations.minSdk
    targetSdk = Configurations.targetSdk
    versionCode = Configurations.versionCode
    versionName = Configurations.versionName
  }

  packaging {
    resources {
      excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
  }

  signingConfigs {
    create("release") {
      keyAlias = keystoreProperties["releaseKeyAlias"] as String?
      keyPassword = keystoreProperties["releaseKeyPassword"] as String?
      storeFile = file(keystoreProperties["releaseStoreFile"] ?: "release/release-key.jks")
      storePassword = keystoreProperties["releaseStorePassword"] as String?
    }
  }

  buildTypes {
    release {
      if (keystorePropertiesFile.exists()) {
        signingConfig = signingConfigs["release"]
      }
      isShrinkResources = true
      isMinifyEnabled = true
    }

    create("benchmark") {
      initWith(buildTypes.getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      isDebuggable = false
      proguardFiles("benchmark-rules.pro")
    }
  }
}

secrets {
  propertiesFileName = "secrets.properties"
  defaultPropertiesFileName = "secrets.defaults.properties"
}

dependencies {
  // --- Core Design & Compose ---
  implementation("androidx.compose.material3:material3:1.3.1")
  implementation("androidx.compose.foundation:foundation:1.7.5") // Fixes .border() reference
  implementation("androidx.compose.ui:ui:1.7.5")
  
  // --- Project Modules ---
  implementation(project(":core-designsystem"))
  implementation(project(":core-navigation"))
  implementation(project(":core-data"))
  implementation(project(":feature-chat"))
  implementation(project(":feature-login"))

  // --- Room Persistence (Standardized to 2.6.1) ---
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")
 
  // --- Lifecycle & State Management ---
  // Fixes the ExperimentalLifecycleComposeApi unresolved warning
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

  // --- Browser & Support ---
  implementation("androidx.browser:browser:1.5.0")
  implementation(libs.androidx.appcompat)

  // --- Compose Utilities ---
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.constraintlayout)

  // --- Dependency Injection ---
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.hilt.compiler)

  // --- Image & Logging ---
  implementation(libs.landscapist.glide)
  implementation(libs.stream.log)
  implementation(libs.snitcher)

  // --- Firebase ---
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.crashlytics)

  // --- Optimization ---
  baselineProfile(project(":benchmark"))
  implementation(libs.androidx.startup)
}

if (file("google-services.json").exists()) {
  apply(plugin = libs.plugins.gms.googleServices.get().pluginId)
  apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}
