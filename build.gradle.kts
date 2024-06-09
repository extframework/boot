import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.commonUtil
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "1.9.21"

    application

    id("dev.extframework.common") version "1.0.4"
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

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

common {
    publishing {
        publication {
            artifactId = "boot"
            pom {
                name.set("Boot")
                description.set("YakClient's Boot module")
                url.set("https://github.com/yakclient/boot")
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    group = "dev.extframework"
    version = "2.1.1-SNAPSHOT"

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
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))

        commonUtil(configurationName = "api")
        archives(configurationName = "api")
        artifactResolver()
        jobs(logging = true, progressSimple = true)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}