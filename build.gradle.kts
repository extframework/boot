
plugins {
    kotlin("jvm") version "1.9.21"

    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
    application

    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "net.yakclient"
version = "2.1-SNAPSHOT"

application {
    mainClass.set("net.yakclient.boot.main.BootKt")

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

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

dependencies {
    implementation(project(":object-container"))

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.22")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

}

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}

publishing {
    publications {
        create<MavenPublication>("boot-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "boot"

            pom {
                name.set("Boot")
                description.set("YakClient's Boot module")
                url.set("https://github.com/yakclient/boot")

                packaging = "jar"

                developers {
                    developer {
                        id.set("Chestly")
                        name.set("Durgan McBroom")
                    }
                }

                licenses {
                    license {
                        name.set("GNU General Public License")
                        url.set("https://opensource.org/licenses/gpl-license")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yakclient/boot")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/boot.git")
                    url.set("https://github.com/yakclient/boot")
                }
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    group = "net.yakclient"

    repositories {
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
    }

    publishing {
        repositories {
            if (project.hasProperty("maven-user") && project.hasProperty("maven-secret")) maven {
                logger.quiet("Maven user and password found.")
                val repo = if ((version as String).endsWith("-SNAPSHOT")) "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-secret") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            } else logger.quiet("Maven user and password not found.")
        }
    }

    kotlin {
        explicitApi()
    }

    val jobsApiVersion = "1.2-SNAPSHOT"

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))

        api("net.yakclient:common-util:1.1-SNAPSHOT") {
            isChanging = true
        }

        api("net.yakclient:archives:1.2-SNAPSHOT") {
            isChanging = true
        }

        implementation("com.durganmcbroom:artifact-resolver:1.1-SNAPSHOT") {
            isChanging = true
        }
        implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.1-SNAPSHOT") {
            isChanging = true
        }
        api("com.durganmcbroom:jobs:$jobsApiVersion") {
            isChanging = true
        }
        implementation("com.durganmcbroom:jobs-logging:$jobsApiVersion") {
            isChanging = true
        }
        implementation("com.durganmcbroom:jobs-progress:$jobsApiVersion") {
            isChanging = true
        }
        implementation("com.durganmcbroom:jobs-progress-simple:$jobsApiVersion") {
            isChanging = true
        }
    }

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())

        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.compileJava {
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}