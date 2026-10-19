// Vendored copy of kyngs/libby-gradle-plugin 1.2.1 (MPL-2.0), see README-JTLOGIN.md.
// The Java sources next to this file are unchanged. This build script is new: it is the original plugin/build.gradle.kts
// reduced to what is needed to build the plugin locally (no publishing, no license-header plugin, no javadoc/sources jars).

plugins {
    `java-gradle-plugin`
}

version = "1.2.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.grack:nanojson:1.7")

    // Only needed to compile ShadowPluginIntegration. At build time the shadow plugin of the consuming project is used.
    compileOnly("com.github.johnrengelman:shadow:8.1.1")
}

gradlePlugin {
    plugins {
        create("xyz.kyngs.libby.plugin") {
            id = "xyz.kyngs.libby.plugin"
            implementationClass = "xyz.kyngs.libby.plugin.LibbyGradlePlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
