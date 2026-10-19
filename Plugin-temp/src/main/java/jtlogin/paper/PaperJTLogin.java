/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.paper;

import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import co.aikar.commands.PaperCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import jtlogin.api.Logger;
import jtlogin.api.database.User;
import jtlogin.api.event.exception.EventCancelledException;
import jtlogin.common.AuthenticJTLogin;
import jtlogin.common.legacy.LegacyPermissions;
import jtlogin.common.SLF4JLogger;
import jtlogin.common.image.AuthenticImageProjector;
import jtlogin.common.util.CancellableTask;
import jtlogin.paper.protocol.PacketListener;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

import static jtlogin.common.config.ConfigurationKeys.DEBUG;

public class PaperJTLogin extends AuthenticJTLogin<Player, World> {

    private final PaperBootstrap bootstrap;
    private PaperListeners listeners;
    private boolean started;

    public PaperJTLogin(PaperBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.started = false;

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(bootstrap));

        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .bStats(false);

        PacketEvents.getAPI().load();
    }

    public PaperBootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return bootstrap.getResource(name);
    }

    @Override
    public File getDataFolder() {
        return bootstrap.getDataFolder();
    }

    @Override
    public String getVersion() {
        return bootstrap.getDescription().getVersion();
    }

    @Override
    public boolean isPresent(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null;
    }

    @Override
    public boolean multiProxyEnabled() {
        return false;
    }

    @Override
    public Player getPlayerForUUID(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    protected PaperPlatformHandle providePlatformHandle() {
        return new PaperPlatformHandle(this);
    }

    @Override
    protected Logger provideLogger() {
        return new SLF4JLogger(bootstrap.getSLF4JLogger(), () -> getConfiguration().get(DEBUG));
    }

    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> provideManager() {
        // Honour the permission nodes LibreLogin used before the rebrand (see LegacyPermissions)
        return new PaperCommandManager(bootstrap) {
            @Override
            public boolean hasPermission(CommandIssuer issuer, String permission) {
                return LegacyPermissions.check(permission, issuer::hasPermission);
            }
        };
    }

    @Override
    protected boolean mainThread() {
        return Bukkit.isPrimaryThread() && started;
    }

    @Override
    public Player getPlayerFromIssuer(CommandIssuer issuer) {
        var bukkitIssuer = (BukkitCommandIssuer) issuer;

        return bukkitIssuer.getPlayer();
    }

    @Override
    protected void disable() {
        PacketEvents.getAPI().terminate();
        if (getDatabaseProvider() == null) return; //Not initialized

        super.disable();
    }

    @Override
    protected void enable() {

        logger = provideLogger();

        if (Bukkit.getOnlineMode()) {
            getLogger().error("!!!The server is running in online mode! JTLogin won't start unless you set it to false!!!");
            disable();
            return;
        }

        if (Bukkit.spigot().getSpigotConfig().getBoolean("settings.bungeecord") || Bukkit.spigot().getPaperConfig().getBoolean("settings.velocity-support.enabled")) {
            getLogger().error("!!!This server is running under a proxy, JTLogin won't start!!!");
            getLogger().error("If you want to use JTLogin under a proxy, place it on the proxy and remove it from the server.");
            disable();
            return;
        }

        try {
            super.enable();
        } catch (ShutdownException e) {
            return;
        }

        var provider = getEventProvider();

        provider.subscribe(provider.getTypes().authenticated, event -> {
            var player = event.getPlayer();
            if (player == null) return;
            player.setInvisible(false);
        });

        listeners = new PaperListeners(this);

        Bukkit.getPluginManager().registerEvents(listeners, bootstrap);
        Bukkit.getPluginManager().registerEvents(new Blockers(this), bootstrap);
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(listeners));

        started = true;
    }

    @Override
    public void authorize(Player player, User user, Audience audience) {
        try {

            var location = listeners.getSpawnLocationCache().getIfPresent(player);

            if (location == null) {
                var world = getServerHandler().chooseLobbyServer(user, player, true, false);

                if (world == null) {
                    getPlatformHandle().kick(player, getMessages().getMessage("kick-no-lobby"));
                    return;
                }

                location = world.getSpawnLocation();
            } else {
                listeners.getSpawnLocationCache().invalidate(player);
            }

            var finalLocation = location;
            PaperUtil.runSyncAndWait(() -> player.teleportAsync(finalLocation), this);

        } catch (EventCancelledException ignored) {}
    }

    @Override
    public CancellableTask delay(Runnable runnable, long delayInMillis) {
        var task = Bukkit.getScheduler().runTaskLaterAsynchronously(bootstrap, runnable, delayInMillis / 50);
        return task::cancel;
    }

    @Override
    public CancellableTask repeat(Runnable runnable, long delayInMillis, long repeatInMillis) {
        var task = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(bootstrap, runnable, delayInMillis / 50, repeatInMillis / 50);
        return task::cancel;
    }

    @Override
    public boolean pluginPresent(String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    protected AuthenticImageProjector<Player, World> provideImageProjector() {
        return null;
    }

    @Override
    protected void shutdownProxy(int code) {
        bootstrap.disable();
        bootstrap.getServer().shutdown();
        throw new ShutdownException();
    }

    @Override
    public Audience getAudienceFromIssuer(CommandIssuer issuer) {
        return ((BukkitCommandIssuer) issuer).getIssuer();
    }
}
