group = "net.yakclient"
version = "1.0-SNAPSHOT"

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

            artifactId = "object-container"

            pom {
                packaging = "jar"

                developers {
                    developer {
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