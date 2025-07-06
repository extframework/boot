import com.kaolinmc.gradle.common.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.20"

    id("com.kaolinmc.common") version "0.1"
}

version = "3.7.2-SNAPSHOT"

sourceSets {
    create("java11")
    create("java11Test")
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
    "java11Implementation"("com.kaolinmc:archives:${dependencyManagement[ARCHIVES]["version"]}:jdk11")

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
                description.set("Kaolin's Boot module")
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
    apply(plugin = "com.kaolinmc.common")

    group = "com.kaolinmc"

    repositories {
        mavenCentral()
        kaolin()
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
                    withKaolinRepo()
                    gnuLicense()
                    kaolinScm("boot")
                }
            }
            repositories {
                kaolin(credentials = propertyCredentialProvider)
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