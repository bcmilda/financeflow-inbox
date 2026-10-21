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
        versionCode = 3
        versionName = "0.1.2"
    }

    //  Stejný testovací podpis při každém sestavení na GitHubu → nová verze se nainstaluje přes starou
    //  (bez odinstalace). Testovací klíč není tajemství; pro Google Play bude vlastní klíč.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug-sdileny.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
