plugins {
    id("java-library")
    id("maven-publish")
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    api("javax.annotation:javax.annotation-api:1.3.2")
    compileOnly("net.kyori:adventure-platform-bungeecord:4.1.2")
    compileOnly("com.google.guava:guava:30.0-jre")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

// No remote repository is configured on purpose: JTLogin has no publishing credentials or repository of its own.
// Use `./gradlew :API:publishToMavenLocal` to install the API into the local Maven repository.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
