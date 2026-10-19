/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.velocity;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.byteflux.libby.VelocityLibraryManager;
import org.slf4j.Logger;
import jtlogin.api.JTLoginPlugin;
import jtlogin.api.provider.JTLoginProvider;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Plugin(
        id = "jtlogin",
        name = "JTLogin",
        version = "@version@",
        authors = "kyngs",
        description = "JTLogin - authentication plugin. A rebrand and port of LibreLogin by kyngs (MPL-2.0); contains code derived from FastLogin.",
        dependencies = {
                @Dependency(id = "floodgate", optional = true),
                @Dependency(id = "luckperms", optional = true),
                @Dependency(id = "protocolize", optional = true),
                @Dependency(id = "redisbungee", optional = true),
                @Dependency(id = "nanolimbovelocity", optional = true)
        }
)
public class VelocityBootstrap implements JTLoginProvider<Player, RegisteredServer> {

    ProxyServer server;
    private final VelocityJTLogin jtLogin;

    @Inject
    public VelocityBootstrap(ProxyServer server, Injector injector, Logger logger, PluginContainer container) {
        this.server = server;

        // This is a very ugly hack to be able to load libraries in the constructor
        // We cannot pass this as a parameter to the constructor because the plugin is technically still not loaded
        // And, we cannot past the container as a parameter to the constructor because the proxy still did not assign the instance to it.
        // So, we have to "mock" the container and pass this as the instance. I'm kinda surprised this works, but in theory could break in the future.
        var libraryManager = new VelocityLibraryManager<>(logger, Path.of("plugins", "jtlogin"), server.getPluginManager(), new PluginContainer() {
            @Override
            public PluginDescription getDescription() {
                return container.getDescription();
            }

            @Override
            public Optional<?> getInstance() {
                return Optional.of(this);
            }

            @Override
            public ExecutorService getExecutorService() {
                return Executors.newSingleThreadExecutor();
            }
        });

        logger.info("Loading libraries...");

        libraryManager.configureFromJSON();

        jtLogin = new VelocityJTLogin(this);
        injector.injectMembers(jtLogin);
    }

    @Subscribe
    public void onInitialization(ProxyInitializeEvent event) {
        jtLogin.enable();

        server.getEventManager().register(this, new Blockers(jtLogin.getAuthorizationProvider(), jtLogin.getConfiguration(), jtLogin.getMessages()));
        server.getEventManager().register(this, new VelocityListeners(jtLogin));
    }

    @Override
    public JTLoginPlugin<Player, RegisteredServer> getJTLogin() {
        return jtLogin;
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        jtLogin.disable();
    }
}
