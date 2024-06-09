dependencies {
    implementation(project(":object-container"))
    implementation(project(":"))
}

common {
    publishing {
        publication {
            artifactId = "boot-test"
            commonPom {
                name.set("Boot Test")
                description.set("The test module for YakClient's Boot module")
                url.set("https://github.com/yakclient/boot")
            }
        }
    }
}