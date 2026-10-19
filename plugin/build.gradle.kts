import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("io.github.goooler.shadow") version "8.1.8"
    id("net.kyori.blossom").version("1.3.1")
    id("java-library")
    id("xyz.kyngs.libby.plugin").version("1.2.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Java 21 is the lowest runtime of the supported range (Minecraft 1.21.11, Velocity 3.x, Paper 1.21.11).
    // Java 21 bytecode also runs on the Java 25 runtimes required by Minecraft 26.1+ and Velocity 4.x.
    options.release.set(21)
}

repositories {
    // mavenLocal()
    maven { url = uri("https://repo.opencollab.dev/maven-snapshots/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://hub.spigotmc.org/nexus/") }
    maven { url = uri("https://repo.kyngs.xyz/public/") }
    maven { url = uri("https://mvn.exceptionflug.de/repository/exceptionflug-public/") }
    maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
    maven { url = uri("https://repo.alessiodp.com/releases/") }
    maven { url = uri("https://jitpack.io/") }
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
}

blossom {
    replaceToken("@version@", version)
}

tasks.withType<ShadowJar> {
    archiveFileName.set("JTLogin.jar")

    manifest {
        // Declares the jar as Mojang-mapped so that Paper 1.21.11 does not run its (Spigot-name) remapper over it.
        // Paper 26.1+ has no remapper at all and ignores this entry. The only server-internals access in the jar
        // is jtlogin.paper.protocol.ConnectionEncryption, which does not depend on mapped names.
        attributes("paperweight-mappings-namespace" to "mojang")
    }

    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("org.checkerframework:.*:.*"))
        exclude(dependency("com.google.errorprone:.*:.*"))
        exclude(dependency("com.google.protobuf:.*:.*"))
    }

    relocate("co.aikar.acf", "jtlogin.lib.acf")
    relocate("com.github.benmanes.caffeine", "jtlogin.lib.caffeine")
    relocate("com.typesafe.config", "jtlogin.lib.hocon")
    relocate("com.zaxxer.hikari", "jtlogin.lib.hikari")
    relocate("org.mariadb", "jtlogin.lib.mariadb")
    relocate("org.intellij", "jtlogin.lib.intellij")
    relocate("org.jetbrains", "jtlogin.lib.jetbrains")
    relocate("io.leangen.geantyref", "jtlogin.lib.reflect")
    relocate("org.spongepowered.configurate", "jtlogin.lib.configurate")
    relocate("net.byteflux.libby", "jtlogin.lib.libby")
    relocate("org.postgresql", "jtlogin.lib.postgresql")
    relocate("com.github.retrooper.packetevents", "jtlogin.lib.packetevents.api")
    relocate("io.github.retrooper.packetevents", "jtlogin.lib.packetevents.platform")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Jar> {
    from("../LICENSE.txt")
}

libby {
    excludeDependency("org.slf4j:.*:.*")
    excludeDependency("org.checkerframework:.*:.*")
    excludeDependency("com.google.errorprone:.*:.*")
    excludeDependency("com.google.protobuf:.*:.*")

    // Often redeploys the same version, so calculating checksum causes false flags
    noChecksumDependency("com.github.retrooper.packetevents:.*:.*")
}

configurations.all {
    // I hate this, but it needs to be done as bungeecord does not support newer versions of adventure, and packetevents includes it
    resolutionStrategy {
        force("net.kyori:adventure-text-minimessage:4.14.0")
        force("net.kyori:adventure-text-serializer-gson:4.14.0")
        force("net.kyori:adventure-text-serializer-legacy:4.14.0")
        force("net.kyori:adventure-text-serializer-json:4.14.0")
        force("net.kyori:adventure-api:4.14.0")
        force("net.kyori:adventure-nbt:4.14.0")
        force("net.kyori:adventure-key:4.14.0")
    }
}

dependencies {
    //API
    implementation(project(":API"))

    //Tests
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    //Velocity
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.2.0-SNAPSHOT-277")

    //MySQL
    libby("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    libby("com.zaxxer:HikariCP:6.2.1")

    //SQLite
    libby("org.xerial:sqlite-jdbc:3.47.1.0")

    //PostgreSQL
    libby("org.postgresql:postgresql:42.7.5")

    //ACF
    libby("com.github.kyngs.commands:acf-velocity:7d5bf7cac0")
    libby("com.github.kyngs.commands:acf-bungee:7d5bf7cac0")
    libby("com.github.kyngs.commands:acf-paper:7d5bf7cac0")

    //Utils
    libby("com.github.ben-manes.caffeine:caffeine:3.2.0")
    libby("org.spongepowered:configurate-hocon:4.1.2")
    libby("at.favre.lib:bcrypt:0.10.2")
    libby("dev.samstevens.totp:totp:1.7.1")
    compileOnly("dev.simplix:protocolize-api:2.4.2")
    libby("org.bouncycastle:bcprov-jdk18on:1.80")
    libby("org.apache.commons:commons-email:1.6.0")
    // DO NOT UPGRADE TO 4.15.0 OR ABOVE BEFORE TESTING WATERFALL AND BUNGEECORD COMPATIBILITY!!!
    libby("net.kyori:adventure-text-minimessage:4.14.0")
    libby("com.github.kyngs:LegacyMessage:0.2.0")

    //Geyser
    compileOnly("org.geysermc.floodgate:api:2.2.0-SNAPSHOT")
    //LuckPerms
    compileOnly("net.luckperms:api:5.4")

    //Bungeecord
    compileOnly("net.md-5:bungeecord-api:1.21-R0.1-SNAPSHOT")
    compileOnly("com.github.ProxioDev.ValioBungee:RedisBungee-Bungee:0.12.5")
    libby("net.kyori:adventure-platform-bungeecord:4.1.2")

    //Paper
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    //compileOnly "com.comphenix.protocol:ProtocolLib:5.1.0"
    // 2.14.0 is the first PacketEvents tag containing the 26.3 client version and packet table
    // (2.11.0 added 1.21.11, 2.12.0 added 26.1, 2.13.0 added 26.2). UNVERIFIED: artifact resolution, runtime behaviour.
    libby("com.github.retrooper:packetevents-spigot:2.14.0")
    compileOnly("io.netty:netty-transport:4.1.108.Final")
    compileOnly("com.mojang:datafixerupper:5.0.28") //I hate this so much
    compileOnly("org.apache.logging.log4j:log4j-core:2.23.1")

    //Libby
    implementation("xyz.kyngs.libby:libby-bukkit:1.6.0")
    implementation("xyz.kyngs.libby:libby-velocity:1.6.0")
    implementation("xyz.kyngs.libby:libby-bungee:1.6.0")
    implementation("xyz.kyngs.libby:libby-paper:1.6.0")

    //NanoLimboPlugin
    compileOnly("com.github.bivashy.NanoLimboPlugin:api:1.0.8")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ProcessResources> {
    outputs.upToDateWhen { false }

    // Bundle the full forbidden-passwords list (the original downloaded it from the author's repository at runtime)
    from(rootProject.file("forbidden-passwords.txt")) {
        rename("forbidden-passwords.txt", "forbidden-passwords-full.txt")
    }
    filesMatching("plugin.yml") {
        expand(mapOf("version" to version))
    }
    filesMatching("bungee.yml") {
        expand(mapOf("version" to version))
    }
    filesMatching("paper-plugin.yml") {
        expand(mapOf("version" to version))
    }
}
