/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    kotlin("jvm") version "1.3.50"
    `maven-publish`
}

val oprsver = "1.5.44-SNAPSHOT"

group = "com.openosrs"
version = "1.0.3.1"

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

    implementation("org.ow2.asm:asm:7.2")
    implementation("org.ow2.asm:asm-util:7.2")
    implementation("org.jetbrains:annotations:18.0.0")
    implementation("com.google.guava:guava:28.1-jre")
    implementation("com.openosrs:deobfuscator:${oprsver}") {
        isTransitive = false
    }

    testCompileOnly("com.openosrs:injection-annotations:1.0")
    testImplementation("junit:junit:4.12")
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

publishing {
    repositories {
        maven {
            url = uri("$buildDir/repo")
        }
    }
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}
