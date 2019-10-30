import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "1.3.50"
    `maven-publish`
}

group = "com.openosrs"
version = "1.0.0"
extra["mainver"] = "1.5.37-SNAPSHOT"

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

    implementation(kotlin("stdlib-jdk8"))
    implementation("com.openosrs:deobfuscator:${extra["mainver"]}") {
        exclude("org.slf4j", "slf4j-simple")
    }
    implementation("com.google.guava:guava:28.1-jre")
    implementation("org.ow2.asm:asm:7.2")
    implementation("org.projectlombok:lombok:1.18.10")

    testImplementation("junit:junit:4.12")
    testImplementation("com.openosrs:mixins:${extra["mainver"]}")
    testCompileOnly("com.openosrs.rs:runescape-api:${extra["mainver"]}")
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
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}