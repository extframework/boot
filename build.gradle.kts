import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.commonUtil
import dev.extframework.gradle.common.dm.artifactResolver
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.dm.resourceApi
import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "1.9.21"

    application

    id("dev.extframework.common") version "1.0.19"
}


tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
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

    testImplementation(project(":blackbox-test"))
}

common {
    publishing {
        publication {
            artifactId = "boot"
            pom {
                name.set("Boot")
                description.set("YakClient's Boot module")
                url.set("https://github.com/extframework/boot")
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.extframework.common")

    group = "dev.extframework"
    version = "3.2.2-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
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

        resourceApi(configurationName = "api")
        commonUtil(configurationName = "api")
        archives(configurationName = "api")
        artifactResolver(configurationName = "api")
        jobs(configurationName = "api", logging = true, progressSimple = true)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}