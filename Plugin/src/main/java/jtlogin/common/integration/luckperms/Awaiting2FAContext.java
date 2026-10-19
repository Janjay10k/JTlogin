/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.integration.luckperms;

import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import jtlogin.common.AuthenticJTLogin;
import jtlogin.common.legacy.LegacyNames;

public class Awaiting2FAContext<P> implements ContextCalculator<P> {

    private final AuthenticJTLogin<P, ?> plugin;

    public Awaiting2FAContext(AuthenticJTLogin<P, ?> plugin) {
        this.plugin = plugin;
    }

    private static final String KEY = "jtlogin-awaiting2fa";
    // LibreLogin's key, still published so that existing LuckPerms rules keep matching after the rebrand
    private static final String LEGACY_KEY = LegacyNames.CONTEXT_AWAITING_2FA;

    @Override
    public void calculate(@NonNull P player, @NonNull ContextConsumer consumer) {
        var value = Boolean.toString(plugin.getAuthorizationProvider().isAwaiting2FA(player));
        consumer.accept(KEY, value);
        consumer.accept(LEGACY_KEY, value);
    }

    @Override
    public @NonNull ContextSet estimatePotentialContexts() {
        return ImmutableContextSet.builder()
                .add(KEY, "true")
                .add(KEY, "false")
                .add(LEGACY_KEY, "true")
                .add(LEGACY_KEY, "false")
                .build();
    }
}
