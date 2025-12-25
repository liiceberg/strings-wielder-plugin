plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.liiceberg"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "String-wielder"
        id="com.liiceberg.string-wielder-plugin"
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.android")
        androidStudio(property("ideVersion").toString())
    }
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
}
