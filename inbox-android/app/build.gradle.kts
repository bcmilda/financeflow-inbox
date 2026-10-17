//  FinanceFlow Inbox · Android appka, která čte notifikace vybraných bankovních aplikací
//  a jejich text posílá do FinanceFlow (users/{uid}/inbox). Žádné knihovny navíc – jen Android.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.financeflow.inbox"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.financeflow.inbox"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
