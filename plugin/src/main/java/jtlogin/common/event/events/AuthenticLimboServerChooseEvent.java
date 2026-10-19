/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.event.events;

import org.jetbrains.annotations.Nullable;
import jtlogin.api.JTLoginPlugin;
import jtlogin.api.database.User;
import jtlogin.api.event.events.LimboServerChooseEvent;
import jtlogin.common.event.AuthenticServerChooseEvent;

public class AuthenticLimboServerChooseEvent<P, S> extends AuthenticServerChooseEvent<P, S> implements LimboServerChooseEvent<P, S> {
    public AuthenticLimboServerChooseEvent(@Nullable User user, P player, JTLoginPlugin<P, S> plugin) {
        super(user, player, plugin);
    }
}
