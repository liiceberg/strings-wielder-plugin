plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
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
        local("/Applications/Android Studio.app/Contents")
    }
}
