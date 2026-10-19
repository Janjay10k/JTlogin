/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.legacy;

import java.util.function.Predicate;

/**
 * Permission checks that accept either the canonical JTLogin node or the exact node LibreLogin used before.
 * <p>
 * The evaluation of a permission expression mirrors ACF's {@code CommandManager#hasPermission}: the expression
 * is split on commas and EVERY part must be satisfied (comma means AND, not OR). A part is satisfied when the
 * canonical node is held, or when the single legacy node registered for it in {@link LegacyNames#PERMISSIONS}
 * is held. Nodes without a registered legacy counterpart are checked exactly as before.
 */
public final class LegacyPermissions {

    /**
     * @param expression the permission expression as given to {@code @CommandPermission} (may be null/empty)
     * @param has        checks whether the sender holds one concrete node
     */
    public static boolean check(String expression, Predicate<String> has) {
        if (expression == null || expression.isEmpty()) return true;
        for (var part : expression.split(",")) {
            var node = part.trim();
            if (node.isEmpty()) continue;
            if (!hasNode(node, has)) return false;
        }
        return true;
    }

    /**
     * Checks one node: the canonical node, or its explicitly registered legacy counterpart.
     */
    public static boolean hasNode(String node, Predicate<String> has) {
        if (has.test(node)) return true;
        var legacy = LegacyNames.PERMISSIONS.get(node);
        return legacy != null && has.test(legacy);
    }

    private LegacyPermissions() {
    }
}
