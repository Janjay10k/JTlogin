/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.guards;

import jtlogin.common.legacy.LegacyNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cheap static guards over the plugin sources. They do not prove runtime behaviour; they stop specific, known
 * mistakes from coming back (a removed Adventure call, an upstream endpoint, a renamed table, ...).
 */
class SourceGuardsTest {

    private static Path sourceRoot;
    private static List<Path> javaFiles;

    @BeforeAll
    static void locateSources() throws IOException {
        // Gradle runs tests with the project directory (Plugin/) as the working directory.
        var candidates = List.of(Path.of("src/main/java"), Path.of("Plugin/src/main/java"));
        sourceRoot = candidates.stream().filter(Files::isDirectory).findFirst()
                .orElseThrow(() -> new AssertionError("Cannot find src/main/java from " + Path.of("").toAbsolutePath()));
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            javaFiles = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(javaFiles.size() > 100, "suspiciously few source files found: " + javaFiles.size());
    }

    private static List<String> offenders(Pattern pattern) throws IOException {
        var result = new ArrayList<String>();
        for (var file : javaFiles) {
            var lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                var trimmed = lines.get(i).trim();
                // comments and Javadoc may legitimately explain why something is NOT used
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
                if (pattern.matcher(lines.get(i)).find()) {
                    result.add(sourceRoot.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }
        return result;
    }

    @Test
    void noAdventureApisRemovedInAdventure5() throws IOException {
        // Adventure 5 (Velocity 4.x, Paper 26.x) removed these; Title.Times.times(...) is the replacement (since 4.10).
        assertEquals(List.of(), offenders(Pattern.compile("Title\\.Times\\.of\\(|\\bMessageType\\b")));
    }

    @Test
    void noUpstreamEndpointsOrInventedRepositories() throws IOException {
        assertEquals(List.of(), offenders(Pattern.compile(
                "kyngs/JTLogin|kyngs/jtlogin|api\\.github\\.com/repos|raw\\.githubusercontent\\.com|org\\.bstats|bstats\\.org")));
    }

    @Test
    void noMappingDependentServerInternalsLookups() throws IOException {
        // Names of Minecraft classes differ between Spigot-mapped (<= 1.21.11) and Mojang-mapped (26.1+) runtimes.
        assertEquals(List.of(), offenders(Pattern.compile("\"net\\.minecraft|\"org\\.bukkit\\.craftbukkit|MinecraftEncryption")));
    }

    @Test
    void databaseTableNameIsUnchanged() throws IOException {
        assertEquals("librepremium_data", LegacyNames.DATABASE_TABLE);
        assertEquals(List.of(), offenders(Pattern.compile("jtlogin_data|\\bjtlogin_users\\b")));

        var provider = Files.readString(sourceRoot.resolve("jtlogin/common/database/provider/JTLoginSQLDatabaseProvider.java"));
        assertTrue(provider.contains("CREATE TABLE IF NOT EXISTS librepremium_data("), "schema creation must keep the existing table name");
    }

    @Test
    void everyPermissionNodeUsedInCodeHasAnExplicitLegacyMapping() throws IOException {
        var node = Pattern.compile("\"(jtlogin\\.[A-Za-z0-9_.*-]+)\"");
        var used = new ArrayList<String>();
        for (var file : javaFiles) {
            for (var line : Files.readAllLines(file)) {
                if (!line.contains("@CommandPermission(") && !line.contains("hasNode(")) continue;
                var matcher = node.matcher(line);
                while (matcher.find()) used.add(matcher.group(1));
            }
        }
        assertFalse(used.isEmpty(), "no permission literals found, the scan is broken");
        for (var permission : used) {
            assertTrue(LegacyNames.PERMISSIONS.containsKey(permission), "no legacy mapping for " + permission);
        }
        // and the mapping must not contain nodes that no code uses
        for (var canonical : LegacyNames.PERMISSIONS.keySet()) {
            assertTrue(used.contains(canonical), "mapping for unused node " + canonical);
        }
    }

    @Test
    void legacyDatabaseTypeIdsAreStillRegistered() throws IOException {
        var main = Files.readString(sourceRoot.resolve("jtlogin/common/AuthenticJTLogin.java"));
        for (var id : List.of("DATABASE_TYPE_MYSQL", "DATABASE_TYPE_SQLITE", "DATABASE_TYPE_POSTGRESQL")) {
            assertTrue(main.contains("LegacyNames." + id), id + " must stay registered");
        }
        for (var id : List.of("jtlogin-mysql", "jtlogin-sqlite", "jtlogin-postgresql")) {
            assertTrue(main.contains("\"" + id + "\""), id + " must be registered");
        }
    }

    @Test
    void legacyContextKeysAreStillPublished() throws IOException {
        for (var name : List.of("AuthorizedContext", "Awaiting2FAContext")) {
            var source = Files.readString(sourceRoot.resolve("jtlogin/common/integration/luckperms/" + name + ".java"));
            assertTrue(source.contains("LEGACY_KEY"), name);
            assertTrue(source.contains("consumer.accept(LEGACY_KEY"), name + " must keep publishing the legacy key");
        }
        assertEquals("librelogin-authorized", LegacyNames.CONTEXT_AUTHORIZED);
        assertEquals("librelogin-awaiting2fa", LegacyNames.CONTEXT_AWAITING_2FA);
    }
}
