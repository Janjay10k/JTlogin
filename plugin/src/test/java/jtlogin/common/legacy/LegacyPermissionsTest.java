/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.legacy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPermissionsTest {

    private static boolean check(String expression, String... held) {
        var set = Set.of(held);
        return LegacyPermissions.check(expression, set::contains);
    }

    @Test
    void canonicalNodeIsAccepted() {
        assertTrue(check("jtlogin.user.info", "jtlogin.user.info"));
    }

    @Test
    void legacyStaffNodeIsAccepted() {
        assertTrue(check("jtlogin.user.info", "librepremium.user.info"));
        assertTrue(check("jtlogin.reload.configuration", "librepremium.reload.configuration"));
    }

    @Test
    void legacyLibreloginNodeIsAccepted() {
        assertTrue(check("jtlogin.dump", "librelogin.dump"));
        assertTrue(check("jtlogin.limbo.access", "librelogin.limbo.access"));
    }

    @Test
    void noPermissionMeansDenied() {
        assertFalse(check("jtlogin.user.delete"));
        assertFalse(check("jtlogin.user.delete", "jtlogin.user.info", "librepremium.user.info"));
    }

    @Test
    void legacyNodesOnlyMapToTheirOwnNode() {
        // a legacy node must never unlock a different canonical node
        assertFalse(check("jtlogin.user.delete", "librepremium.user.info"));
        assertFalse(check("jtlogin.dump", "librepremium.dump"), "librepremium.dump never existed and must not be honoured");
        assertFalse(check("jtlogin.user.info", "librelogin.user.info"), "librelogin.user.info never existed and must not be honoured");
    }

    @Test
    void unknownNodesAreCheckedExactlyAsBefore() {
        assertTrue(check("some.other.node", "some.other.node"));
        assertFalse(check("some.other.node", "librepremium.some.other.node"));
    }

    @Test
    void commaMeansAndLikeAcf() {
        assertTrue(check("jtlogin.user.info,other.node", "librepremium.user.info", "other.node"));
        assertFalse(check("jtlogin.user.info,other.node", "librepremium.user.info"));
        assertFalse(check("jtlogin.user.info,other.node", "other.node"));
    }

    @Test
    void emptyExpressionIsAllowed() {
        assertTrue(check(null));
        assertTrue(check(""));
    }

    @Test
    void everyCanonicalNodeHasExactlyOneDistinctLegacyNode() {
        assertEquals(18, LegacyNames.PERMISSIONS.size());
        assertEquals(LegacyNames.PERMISSIONS.size(), Set.copyOf(LegacyNames.PERMISSIONS.values()).size());
        LegacyNames.PERMISSIONS.forEach((canonical, legacy) -> {
            assertTrue(canonical.startsWith("jtlogin."), canonical);
            assertTrue(legacy.startsWith("librepremium.") || legacy.startsWith("librelogin."), legacy);
            assertEquals(canonical.substring("jtlogin.".length()), legacy.substring(legacy.indexOf('.') + 1), "suffix must be preserved: " + canonical);
        });
    }
}
