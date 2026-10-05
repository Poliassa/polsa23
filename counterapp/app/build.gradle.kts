plugins { id("com.android.application") }

android {
    namespace = "uz.idesign.multicounter4"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.idesign.multicounter4"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "12.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}