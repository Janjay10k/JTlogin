/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.legacy;

import jtlogin.common.legacy.LegacyDataMigrator.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDataMigratorTest {

    @TempDir
    Path plugins;

    private final List<String> infos = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private LegacyDataMigrator migrator(String targetName) {
        return new LegacyDataMigrator(plugins, targetName, infos::add, warnings::add);
    }

    private void write(String relative, String content) throws IOException {
        var path = plugins.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private String read(String relative) throws IOException {
        return Files.readString(plugins.resolve(relative), StandardCharsets.UTF_8);
    }

    private boolean exists(String relative) {
        return Files.exists(plugins.resolve(relative));
    }

    private void createLegacy(String folder) throws IOException {
        write(folder + "/config.conf", "database.type = \"librelogin-sqlite\"\n");
        write(folder + "/messages.conf", "info-about = \"hello\"\n");
        Files.write(plugins.resolve(folder).resolve("user-data.db"), new byte[]{1, 2, 3, 4, 5, (byte) 200, 0, 7});
        write(folder + "/dumps/a.txt", "dump");
        write(folder + "/lib/library.jar", "cache that must not be copied");
        write(folder + "/LICENSE.txt", "license that must not be copied");
    }

    @Test
    void noLegacyFolderMeansNothingToDoAndNothingCreated() {
        var result = migrator("JTLogin").migrate();
        assertEquals(Outcome.NO_LEGACY_DATA, result.outcome());
        assertFalse(exists("JTLogin"));
    }

    @Test
    void legacyFolderWithOnlyACacheIsIgnored() throws IOException {
        write("LibreLogin/lib/library.jar", "x");
        assertEquals(Outcome.NO_LEGACY_DATA, migrator("JTLogin").migrate().outcome());
    }

    @Test
    void copiesDataVerbatimAndLeavesLegacyUntouched() throws IOException {
        createLegacy("LibreLogin");
        var dbBefore = Files.readAllBytes(plugins.resolve("LibreLogin/user-data.db"));

        var result = migrator("JTLogin").migrate();

        assertEquals(Outcome.MIGRATED, result.outcome());
        assertEquals(4, result.copied());
        assertArrayEquals(dbBefore, Files.readAllBytes(plugins.resolve("JTLogin/user-data.db")));
        assertEquals("database.type = \"librelogin-sqlite\"\n", read("JTLogin/config.conf"));
        assertEquals("info-about = \"hello\"\n", read("JTLogin/messages.conf"));
        assertEquals("dump", read("JTLogin/dumps/a.txt"));
        assertFalse(exists("JTLogin/lib"), "library cache must not be copied");
        assertFalse(exists("JTLogin/LICENSE.txt"), "license is rewritten by the plugin");
        assertTrue(exists("JTLogin/" + LegacyDataMigrator.MARKER_FILE));

        // rollback guarantee: the legacy folder is byte-for-byte what it was
        assertArrayEquals(dbBefore, Files.readAllBytes(plugins.resolve("LibreLogin/user-data.db")));
        assertTrue(exists("LibreLogin/config.conf"));
        assertTrue(exists("LibreLogin/lib/library.jar"));
        assertFalse(exists("LibreLogin/" + LegacyDataMigrator.MARKER_FILE));
        try (var all = Files.walk(plugins.resolve("JTLogin"))) {
            assertTrue(all.noneMatch(p -> p.toString().endsWith(".jtlogin-tmp")), "no temp files left behind");
        }
    }

    @Test
    void secondRunDoesNothing() throws IOException {
        createLegacy("LibreLogin");
        migrator("JTLogin").migrate();

        // the server owner edits the new config afterwards
        write("JTLogin/config.conf", "changed after migration\n");

        var result = migrator("JTLogin").migrate();
        assertEquals(Outcome.ALREADY_MIGRATED, result.outcome());
        assertEquals("changed after migration\n", read("JTLogin/config.conf"));
    }

    @Test
    void neverOverwritesExistingFilesAndReportsConflicts() throws IOException {
        createLegacy("LibreLogin");
        write("JTLogin/config.conf", "already configured for JTLogin\n");

        var result = migrator("JTLogin").migrate();

        assertEquals(Outcome.MIGRATED, result.outcome());
        assertEquals("already configured for JTLogin\n", read("JTLogin/config.conf"), "existing file must be kept");
        assertEquals(List.of("config.conf"), result.conflicts());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("config.conf")));
        assertEquals("dump", read("JTLogin/dumps/a.txt"));
        assertTrue(exists("JTLogin/user-data.db"));
    }

    @Test
    void rerunWithoutMarkerOnlyFillsInMissingFiles() throws IOException {
        createLegacy("LibreLogin");
        migrator("JTLogin").migrate();
        Files.delete(plugins.resolve("JTLogin/" + LegacyDataMigrator.MARKER_FILE));
        Files.delete(plugins.resolve("JTLogin/messages.conf"));

        var result = migrator("JTLogin").migrate();

        assertEquals(Outcome.MIGRATED, result.outcome());
        assertEquals(1, result.copied());
        assertEquals(3, result.identical());
        assertTrue(result.conflicts().isEmpty());
    }

    @Test
    void velocityStyleLowercaseFolders() throws IOException {
        createLegacy("librelogin");
        var result = migrator("jtlogin").migrate();
        assertEquals(Outcome.MIGRATED, result.outcome());
        assertEquals("librelogin", result.source().getFileName().toString());
        assertTrue(exists("jtlogin/user-data.db"));
    }

    @Test
    void fallsBackToTheOlderLibrePremiumFolder() throws IOException {
        createLegacy("LibrePremium");
        var result = migrator("JTLogin").migrate();
        assertEquals(Outcome.MIGRATED, result.outcome());
        assertEquals("LibrePremium", result.source().getFileName().toString());
    }

    @Test
    void prefersLibreLoginOverLibrePremiumWhenBothExist() throws IOException {
        createLegacy("LibrePremium");
        write("LibreLogin/config.conf", "newer\n");
        var result = migrator("JTLogin").migrate();
        assertEquals("LibreLogin", result.source().getFileName().toString());
        assertEquals("newer\n", read("JTLogin/config.conf"));
        assertFalse(exists("JTLogin/user-data.db"), "only one legacy folder is used");
    }

    @Test
    void failureRollsBackAndWritesNoMarker() throws IOException {
        createLegacy("LibreLogin");
        // a FILE named "dumps" in the target makes copying the legacy "dumps" directory impossible
        write("JTLogin/dumps", "i am a file");

        var result = migrator("JTLogin").migrate();

        assertEquals(Outcome.FAILED, result.outcome());
        assertNotNull(result.error());
        assertFalse(exists("JTLogin/" + LegacyDataMigrator.MARKER_FILE), "no marker after a failure");
        assertFalse(exists("JTLogin/user-data.db"), "files created by the failed run are removed again");
        assertFalse(exists("JTLogin/config.conf"));
        assertEquals("i am a file", read("JTLogin/dumps"), "pre-existing target data is untouched");
        assertTrue(exists("LibreLogin/user-data.db"), "legacy data is untouched");
    }
}
