plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.selfator.lock"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.selfator.lock"
        minSdk = 24; targetSdk = 34
        versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
// الشعار: لو رفعت logo.png في أول المستودع يُستخدم، وإلا الشعار الافتراضي
val userLogo = File(rootDir.parentFile, "logo.png")
val copyLogo = tasks.register<Copy>("copyLogo") {
    from(if (userLogo.exists()) userLogo else file("default_logo.png"))
    rename { "logo.png" }
    into(layout.buildDirectory.dir("userlogo/res/drawable"))
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("userlogo/res"))
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyLogo) }

dependencies { implementation("androidx.appcompat:appcompat:1.6.1") }
