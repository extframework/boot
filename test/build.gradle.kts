dependencies {
    implementation(project(":"))
    implementation(project(":object-container"))

    implementation("net.yakclient:common-util:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("net.yakclient:archives:1.1-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("com.durganmcbroom:artifact-resolver-simple-maven:1.0-SNAPSHOT") {
        isChanging = true
    }
    implementation("io.arrow-kt:arrow-core:1.1.2")
    implementation("com.durganmcbroom:jobs:1.0-SNAPSHOT")
    implementation("com.durganmcbroom:jobs-logging:1.0-SNAPSHOT")
    implementation("com.durganmcbroom:jobs-coroutines:1.0-SNAPSHOT")
    implementation("com.durganmcbroom:jobs-progress:1.0-SNAPSHOT")
    implementation("com.durganmcbroom:jobs-progress-simple:1.0-SNAPSHOT")
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
        create<MavenPublication>("boot-test-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "boot-test"

            pom {
                name.set("Boot Test")
                description.set("The test module for YakClient's Boot module")
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

