/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.event.events;

import org.jetbrains.annotations.Nullable;
import jtlogin.api.JTLoginPlugin;
import jtlogin.api.crypto.HashedPassword;
import jtlogin.api.database.User;
import jtlogin.api.event.events.PasswordChangeEvent;
import jtlogin.common.event.AuthenticPlayerBasedEvent;

public class AuthenticPasswordChangeEvent<P, S> extends AuthenticPlayerBasedEvent<P, S> implements PasswordChangeEvent<P, S> {
    private final HashedPassword oldPassword;

    public AuthenticPasswordChangeEvent(@Nullable User user, @Nullable P player, JTLoginPlugin<P, S> plugin, HashedPassword oldPassword) {
        super(user, player, plugin);
        this.oldPassword = oldPassword;
    }

    @Override
    public HashedPassword getOldPassword() {
        return oldPassword;
    }
}
