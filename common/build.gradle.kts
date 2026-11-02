architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

loom {
    accessWidenerPath.set(file("src/main/resources/chatplus.accesswidener"))
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    // Remove the next line if you don't want to depend on the API
    modApi("dev.architectury:architectury:${rootProject.property("architectury_version")}")

    // Tests for the moderation tools. Nothing here ships in the jar.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // A real server profile and samples can be checked locally with -Pstafftools.profile=<file> -Pstafftools.samples=<file>.
    // Those files are never part of this repository.
    listOf("stafftools.profile", "stafftools.samples").forEach { key ->
        project.findProperty(key)?.let { systemProperty(key, it.toString()) }
    }
}