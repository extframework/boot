import dev.extframework.gradle.common.ARCHIVES_VERSION
import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.commonUtil
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.dm.resourceApi
import dev.extframework.gradle.common.extFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.21"

    application

    id("dev.extframework.common") version "1.0.38"
}

version = "3.4.5-SNAPSHOT"

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

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

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation(project(":object-container"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    archives(configurationName = "java11Implementation", version = "1.5-SNAPSHOT")
    jobs(configurationName = "java11Implementation")
    "java11Implementation"(sourceSets.main.get().output)
    "java11Implementation"("dev.extframework:archives:${"1.5-SNAPSHOT"}:jdk11")

    testImplementation(project(":blackbox-test"))


    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.5.2")
    testImplementation("io.projectreactor.tools:blockhound:1.0.6.RELEASE")
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
        mavenLocal()
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
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))

        resourceApi(configurationName = "api", version = "1.2-SNAPSHOT")
        commonUtil(configurationName = "api", version = "1.2.1-SNAPSHOT")
        archives(configurationName = "api", version = "1.5-SNAPSHOT")
        artifactResolver(configurationName = "api", version = "1.3-SNAPSHOT",  mavenVersion = "1.3-SNAPSHOT")
        jobs(configurationName = "api", logging = true, progressSimple = true)
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}