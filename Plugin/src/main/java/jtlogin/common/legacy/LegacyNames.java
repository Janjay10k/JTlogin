/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.legacy;

import java.util.List;
import java.util.Map;

/**
 * Identifiers inherited from LibreLogin (and its predecessor LibrePremium) that are persisted in users' data,
 * configuration files or permission plugins. JTLogin keeps honouring them so that an existing installation
 * keeps working after the rebrand.
 * <p>
 * Nothing in here may be derived by string manipulation (for example "replace the prefix"): every legacy
 * permission node is listed explicitly so that a wildcard granted for one legacy namespace can never silently
 * widen access to a node that never existed in that namespace.
 */
public final class LegacyNames {

    /**
     * The database table name is part of the on-disk/in-database schema and is intentionally NOT renamed.
     * Renaming it would orphan every existing account.
     */
    public static final String DATABASE_TABLE = "librepremium_data";

    /**
     * The legacy command alias, still registered next to {@code /jtlogin}.
     */
    public static final String COMMAND_ALIAS = "librelogin";

    /**
     * Legacy LuckPerms context keys. They are still published next to the new {@code jtlogin-*} keys, because
     * permission rules written for LibreLogin (for example "deny unless librelogin-authorized=true") would
     * otherwise stop matching and could silently stop restricting unauthenticated players.
     */
    public static final String CONTEXT_AUTHORIZED = "librelogin-authorized";
    public static final String CONTEXT_AWAITING_2FA = "librelogin-awaiting2fa";

    /**
     * Legacy database provider ids as they appear in {@code database.type} and in the migration settings.
     */
    public static final String DATABASE_TYPE_MYSQL = "librelogin-mysql";
    public static final String DATABASE_TYPE_SQLITE = "librelogin-sqlite";
    public static final String DATABASE_TYPE_POSTGRESQL = "librelogin-postgresql";

    /**
     * Canonical (new) permission node -> the exact node LibreLogin used for it.
     * Historic quirk: staff permissions lived under "librepremium.", a few others under "librelogin.".
     */
    public static final Map<String, String> PERMISSIONS = Map.ofEntries(
            Map.entry("jtlogin.dump", "librelogin.dump"),
            Map.entry("jtlogin.email.test", "librelogin.email.test"),
            Map.entry("jtlogin.limbo.access", "librelogin.limbo.access"),
            Map.entry("jtlogin.user.alts", "librelogin.user.alts"),
            Map.entry("jtlogin.reload.configuration", "librepremium.reload.configuration"),
            Map.entry("jtlogin.reload.messages", "librepremium.reload.messages"),
            Map.entry("jtlogin.user.2faoff", "librepremium.user.2faoff"),
            Map.entry("jtlogin.user.cracked", "librepremium.user.cracked"),
            Map.entry("jtlogin.user.delete", "librepremium.user.delete"),
            Map.entry("jtlogin.user.emailoff", "librepremium.user.emailoff"),
            Map.entry("jtlogin.user.info", "librepremium.user.info"),
            Map.entry("jtlogin.user.login", "librepremium.user.login"),
            Map.entry("jtlogin.user.migrate", "librepremium.user.migrate"),
            Map.entry("jtlogin.user.pass-change", "librepremium.user.pass-change"),
            Map.entry("jtlogin.user.premium", "librepremium.user.premium"),
            Map.entry("jtlogin.user.register", "librepremium.user.register"),
            Map.entry("jtlogin.user.setemail", "librepremium.user.setemail"),
            Map.entry("jtlogin.user.unregister", "librepremium.user.unregister")
    );

    /**
     * Names of data folders, in the order they are searched, for a JTLogin data folder with the given name.
     * The case convention follows the new folder: Velocity uses the lowercase plugin id, Paper/BungeeCord use
     * the plugin name as written in plugin.yml/bungee.yml.
     */
    public static List<String> legacyFolderNames(String newFolderName) {
        var lower = newFolderName.equals(newFolderName.toLowerCase());
        return lower
                ? List.of("librelogin", "librepremium", "LibreLogin", "LibrePremium")
                : List.of("LibreLogin", "LibrePremium", "librelogin", "librepremium");
    }

    private LegacyNames() {
    }
}
