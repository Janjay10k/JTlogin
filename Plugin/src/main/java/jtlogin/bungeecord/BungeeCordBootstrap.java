/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.bungeecord;

import net.byteflux.libby.BungeeLibraryManager;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import jtlogin.api.provider.JTLoginProvider;

public class BungeeCordBootstrap extends Plugin implements JTLoginProvider<ProxiedPlayer, ServerInfo> {

    private BungeeCordJTLogin jtLogin;

    @Override
    public void onLoad() {
        var libraryManager = new BungeeLibraryManager(this);

        getLogger().info("Loading libraries...");

        libraryManager.configureFromJSON();

        jtLogin = new BungeeCordJTLogin(this);
    }

    @Override
    public void onEnable() {
        jtLogin.enable();
    }

    @Override
    public void onDisable() {
        jtLogin.disable();
    }

    @Override
    public BungeeCordJTLogin getJTLogin() {
        return jtLogin;
    }

}
