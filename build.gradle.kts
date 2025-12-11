buildscript {
    val kotlin_version by extra("1.6.21")
    val support_lib_version by extra("27.1.1")
    val dokka_version by extra("0.9.16")

    repositories {
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.1")
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.5")
        classpath("org.jetbrains.dokka:dokka-android-gradle-plugin:$dokka_version")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")

    }
}

allprojects {
    repositories {
        jcenter()
        google()
        maven { url = uri("https://jitpack.io") }
    }
}
