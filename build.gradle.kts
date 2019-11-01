import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "1.3.50"
    `maven-publish`
}

group = "com.openosrs"
version = "1.0.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://repo.runelite.net")
        url = uri("https://raw.githubusercontent.com/open-osrs/hosting/master")
    }
}

dependencies {
    annotationProcessor("org.projectlombok:lombok:1.18.10")
    compileOnly("org.projectlombok:lombok:1.18.10")

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.openosrs:deobfuscator:1.5.39-SNAPSHOT") {
        exclude("org.slf4j", "slf4j-simple")
    }
    implementation("com.google.guava:guava:28.1-jre")
    implementation("org.ow2.asm:asm:7.2")


    testImplementation("junit:junit:4.12")
    testImplementation("com.openosrs:runelite-mixins:1.5.39-SNAPSHOT")
    testCompileOnly("com.openosrs.rs:runescape-api:1.5.39-SNAPSHOT")
}

gradlePlugin {
    plugins {
        create("injectorPlugin") {
            id = "com.openosrs.injector"
            implementationClass = "com.openosrs.injector.InjectPlugin"
        }
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}