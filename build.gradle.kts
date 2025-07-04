import dev.extframework.gradle.common.*
import dev.extframework.gradle.common.extFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"

    application

    id("dev.extframework.common") version "1.1"
}

version = "3.7.2-SNAPSHOT"

sourceSets {
    create("java11")
    create("java11Test")
}

application {
    mainClass.set("dev.extframework.boot.main.BootKt")

    applicationDefaultJvmArgs = listOf(
        "-Xms512m",
        "-Xmx4G",
        "-XstartOnFirstThread",
    )
}

tasks.wrapper {
    gradleVersion = "8.5"
}

dependencies {
    implementation(project(":object-container"))

    implementation(kotlin("reflect"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    "java11Implementation"(archives())
    "java11Implementation"(sourceSets.main.get().output)
    "java11Implementation"("dev.extframework:archives:${dependencyManagement[ARCHIVES]["version"]}:jdk11")

    api(resourceApi())
    api(commonUtil())
    api(archives())
    api(artifactResolver())
    api(artifactResolverMaven())
}

val java11Jar by tasks.creating(Jar::class.java) {
    from(sourceSets.named("java11").get().output)
    archiveClassifier = "jdk11"
}

common {
    publishing {
        publication {
            artifactId = "boot"

            artifact(java11Jar)

            pom {
                name.set("Boot")
                description.set("YakClient's Boot module")
                url.set("https://github.com/extframework/boot")
            }
        }
    }
}

tasks.named<KotlinCompile>("compileJava11Kotlin") {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
}

tasks.named<JavaCompile>("compileJava11Java") {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.named<JavaCompile>("compileJava11TestJava") {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    group = "dev.extframework"

    repositories {
        mavenCentral()
        extFramework()
    }

    common {
        defaultJavaSettings()
        publishing {
            publication {
                withJava()
                withSources()
                withDokka()


                commonPom {
                    packaging = "jar"
                    defaultDevelopers()
                    withExtFrameworkRepo()
                    gnuLicense()
                    extFrameworkScm("boot")
                }
            }
            repositories {
                extFramework(credentials = propertyCredentialProvider)
            }
        }
    }

    kotlin {
        explicitApi()
    }

    dependencies {
        implementation(kotlin("stdlib"))
        testImplementation(kotlin("test"))
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}