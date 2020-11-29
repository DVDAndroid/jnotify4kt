import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    `maven-publish`
}

group = "com.dvd.kotlin.jnotify4kt"
version = "0.94-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    implementation("net.contentobjects.jnotify:jnotify:0.94")

    testImplementation("junit", "junit", "4.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.2")
}

publishing {
    repositories {
        maven {
            name = "jnotify4kt"
            url = uri("https://maven.pkg.github.com/dvdandroid/jnotify4kt")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register("gpr", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType(Test::class) {
    systemProperties("java.library.path" to "$projectDir/natives")
}