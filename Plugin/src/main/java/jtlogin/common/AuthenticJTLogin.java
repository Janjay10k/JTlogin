/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.Nullable;
import jtlogin.api.BiHolder;
import jtlogin.api.JTLoginPlugin;
import jtlogin.api.Logger;
import jtlogin.api.PlatformHandle;
import jtlogin.api.configuration.CorruptedConfigurationException;
import jtlogin.api.crypto.CryptoProvider;
import jtlogin.api.crypto.HashedPassword;
import jtlogin.api.database.*;
import jtlogin.api.database.connector.DatabaseConnector;
import jtlogin.api.database.connector.MySQLDatabaseConnector;
import jtlogin.api.database.connector.PostgreSQLDatabaseConnector;
import jtlogin.api.database.connector.SQLiteDatabaseConnector;
import jtlogin.api.integration.LimboIntegration;
import jtlogin.api.premium.PremiumException;
import jtlogin.api.premium.PremiumUser;
import jtlogin.api.server.ServerHandler;
import jtlogin.api.totp.TOTPProvider;
import jtlogin.api.util.SemanticVersion;
import jtlogin.api.util.ThrowableFunction;
import jtlogin.common.authorization.AuthenticAuthorizationProvider;
import jtlogin.common.command.CommandProvider;
import jtlogin.common.command.InvalidCommandArgument;
import jtlogin.common.config.HoconMessages;
import jtlogin.common.config.HoconPluginConfiguration;
import jtlogin.common.crypto.Argon2IDCryptoProvider;
import jtlogin.common.crypto.BCrypt2ACryptoProvider;
import jtlogin.common.crypto.LogITMessageDigestCryptoProvider;
import jtlogin.common.crypto.MessageDigestCryptoProvider;
import jtlogin.common.database.AuthenticDatabaseProvider;
import jtlogin.common.database.AuthenticUser;
import jtlogin.common.database.connector.AuthenticMySQLDatabaseConnector;
import jtlogin.common.database.connector.AuthenticPostgreSQLDatabaseConnector;
import jtlogin.common.database.connector.AuthenticSQLiteDatabaseConnector;
import jtlogin.common.database.connector.DatabaseConnectorRegistration;
import jtlogin.common.database.provider.JTLoginMySQLDatabaseProvider;
import jtlogin.common.database.provider.JTLoginPostgreSQLDatabaseProvider;
import jtlogin.common.database.provider.JTLoginSQLiteDatabaseProvider;
import jtlogin.common.event.AuthenticEventProvider;
import jtlogin.common.image.AuthenticImageProjector;
import jtlogin.common.integration.FloodgateIntegration;
import jtlogin.common.integration.luckperms.LuckPermsIntegration;
import jtlogin.common.listener.LoginTryListener;
import jtlogin.common.legacy.LegacyDataMigrator;
import jtlogin.common.legacy.LegacyNames;
import jtlogin.common.log.Log4JFilter;
import jtlogin.common.log.SimpleLogFilter;
import jtlogin.common.mail.AuthenticEMailHandler;
import jtlogin.common.migrate.*;
import jtlogin.common.premium.AuthenticPremiumProvider;
import jtlogin.common.server.AuthenticServerHandler;
import jtlogin.common.totp.AuthenticTOTPProvider;
import jtlogin.common.util.CancellableTask;
import jtlogin.common.util.GeneralUtil;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

import static jtlogin.common.config.ConfigurationKeys.*;

public abstract class AuthenticJTLogin<P, S> implements JTLoginPlugin<P, S> {

    public static final Gson GSON = new Gson();
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd. MM. yyyy HH:mm");
    public static final ExecutorService EXECUTOR;

    static {
        EXECUTOR = new ForkJoinPool(4);
    }

    private final Map<String, CryptoProvider> cryptoProviders;
    private final Map<String, ReadDatabaseProviderRegistration<?, ?, ?>> readProviders;
    private final Map<Class<?>, DatabaseConnectorRegistration<?, ?>> databaseConnectors;
    private final Multimap<P, CancellableTask> cancelOnExit;
    private final PlatformHandle<P, S> platformHandle;
    private final Set<String> forbiddenPasswords;
    protected Logger logger;
    private AuthenticPremiumProvider premiumProvider;
    private AuthenticEventProvider<P, S> eventProvider;
    private AuthenticServerHandler<P, S> serverHandler;
    private TOTPProvider totpProvider;
    private AuthenticImageProjector<P, S> imageProjector;
    private FloodgateIntegration floodgateApi;
    private LuckPermsIntegration<P, S> luckpermsApi;
    private SemanticVersion version;
    private HoconPluginConfiguration configuration;
    private HoconMessages messages;
    private AuthenticAuthorizationProvider<P, S> authorizationProvider;
    private CommandProvider<P, S> commandProvider;
    private ReadWriteDatabaseProvider databaseProvider;
    private DatabaseConnector<?, ?> databaseConnector;
    private AuthenticEMailHandler eMailHandler;
    private LoginTryListener<P, S> loginTryListener;

    protected AuthenticJTLogin() {
        cryptoProviders = new ConcurrentHashMap<>();
        readProviders = new ConcurrentHashMap<>();
        databaseConnectors = new ConcurrentHashMap<>();
        platformHandle = providePlatformHandle();
        forbiddenPasswords = new HashSet<>();
        cancelOnExit = HashMultimap.create();
    }

    public Map<Class<?>, DatabaseConnectorRegistration<?, ?>> getDatabaseConnectors() {
        return databaseConnectors;
    }

    @Override
    public <E extends Exception, C extends DatabaseConnector<E, ?>> void registerDatabaseConnector(Class<?> clazz, ThrowableFunction<String, C, E> factory, String id) {
        registerDatabaseConnector(new DatabaseConnectorRegistration<>(factory, null, id), clazz);
    }

    @Override
    public void registerReadProvider(ReadDatabaseProviderRegistration<?, ?, ?> registration) {
        readProviders.put(registration.id(), registration);
    }

    @Override
    public AuthenticEMailHandler getEmailHandler() {
        return eMailHandler;
    }

    @Nullable
    @Override
    public LimboIntegration<S> getLimboIntegration() {
        return null;
    }

    @Override
    public User createUser(UUID uuid, UUID premiumUUID, HashedPassword hashedPassword, String lastNickname, Timestamp joinDate, Timestamp lastSeen, String secret, String ip, Timestamp lastAuthentication, String lastServer, String email) {
        return new AuthenticUser(uuid, premiumUUID, hashedPassword, lastNickname, joinDate, lastSeen, secret, ip, lastAuthentication, lastServer, email);
    }

    public void registerDatabaseConnector(DatabaseConnectorRegistration<?, ?> registration, Class<?> clazz) {
        databaseConnectors.put(clazz, registration);
    }

    @Override
    public PlatformHandle<P, S> getPlatformHandle() {
        return platformHandle;
    }

    protected abstract PlatformHandle<P, S> providePlatformHandle();

    @Override
    public SemanticVersion getParsedVersion() {
        return version;
    }


    @Override
    public boolean validPassword(String password) {
        var length = password.length() >= configuration.get(MINIMUM_PASSWORD_LENGTH);

        if (!length) {
            return false;
        }

        var upper = password.toUpperCase();

        return !forbiddenPasswords.contains(upper);
    }

    @Override
    public Map<String, ReadDatabaseProviderRegistration<?, ?, ?>> getReadProviders() {
        return Map.copyOf(readProviders);
    }

    public CommandProvider<P, S> getCommandProvider() {
        return commandProvider;
    }

    @Override
    public ReadWriteDatabaseProvider getDatabaseProvider() {
        return databaseProvider;
    }

    @Override
    public AuthenticPremiumProvider getPremiumProvider() {
        return premiumProvider;
    }

    @Override
    public TOTPProvider getTOTPProvider() {
        return totpProvider;
    }

    @Override
    public AuthenticImageProjector<P, S> getImageProjector() {
        return imageProjector;
    }

    @Override
    public ServerHandler<P, S> getServerHandler() {
        return serverHandler;
    }

    /**
     * Copies an existing LibreLogin/LibrePremium data folder into the JTLogin one before anything reads or creates
     * configuration or database files. If a legacy folder exists but cannot be copied completely, startup is aborted
     * on purpose: continuing would create a new, empty database next to the existing one.
     */
    private void migrateLegacyData(File folder) {
        var pluginsDirectory = folder.getAbsoluteFile().getParentFile();
        if (pluginsDirectory == null) return;

        var result = new LegacyDataMigrator(
                pluginsDirectory.toPath(),
                folder.getName(),
                logger::info,
                logger::warn
        ).migrate();

        if (result.outcome() == LegacyDataMigrator.Outcome.FAILED) {
            logger.error("Could not copy the existing data from '" + result.source().getFileName() + "' to '" + result.target().getFileName() + "': " + result.error());
            logger.error("JTLogin refuses to start to avoid creating a new, empty database next to your existing one. Nothing in '" + result.source().getFileName() + "' was modified.");
            throw new RuntimeException("Can't migrate the existing data folder, see the log above.");
        }
    }

    protected void enable() {
        version = SemanticVersion.parse(getVersion());
        if (logger == null) logger = provideLogger();

        try {
            new Log4JFilter().inject();
        } catch (Throwable ignored) {
            logger.info("LogFilter is not supported on this platform");
            var simpleLogger = getSimpleLogger();

            if (simpleLogger != null) {
                logger.info("Using SimpleLogFilter");
                new SimpleLogFilter(simpleLogger).inject();
            }
        }

        var folder = getDataFolder();
        migrateLegacyData(folder);

        try {
            Files.copy(getResourceAsStream("LICENSE.txt"), new File(folder, "LICENSE.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Silently ignore
        }

        if (platformHandle.getPlatformIdentifier().equals("paper")) {
            LIMBO.setDefault(List.of("limbo"));

            var lobby = HashMultimap.<String, String>create();
            lobby.put("root", "world");

            LOBBY.setDefault(lobby);
        }

        eventProvider = new AuthenticEventProvider<>(this);
        premiumProvider = new AuthenticPremiumProvider(this);

        registerCryptoProvider(new MessageDigestCryptoProvider("SHA-256"));
        registerCryptoProvider(new MessageDigestCryptoProvider("SHA-512"));
        registerCryptoProvider(new BCrypt2ACryptoProvider());
        registerCryptoProvider(new Argon2IDCryptoProvider(logger));
        registerCryptoProvider(new LogITMessageDigestCryptoProvider("LOGIT-SHA-256", "SHA-256"));

        setupDB();

        checkDataFolder();

        loadConfigs();

        logger.info("Loading forbidden passwords...");

        try {
            loadForbiddenPasswords();
        } catch (IOException e) {
            e.printStackTrace();
            logger.info("An unknown exception occurred while attempting to load the forbidden passwords, this most likely isn't your fault");
            shutdownProxy(1);
        }

        logger.info("Loaded %s forbidden passwords".formatted(forbiddenPasswords.size()));

        connectToDB();

        serverHandler = new AuthenticServerHandler<>(this);

        this.loginTryListener = new LoginTryListener<>(this);

        // Moved to a different class to avoid class loading issues
        GeneralUtil.checkAndMigrate(configuration, logger, this);

        imageProjector = provideImageProjector();

        if (imageProjector != null) {
            if (!configuration.get(TOTP_ENABLED)) {
                imageProjector = null;
                logger.warn("2FA is disabled in the configuration, aborting...");
            } else {
                imageProjector.enable();
            }
        }

        totpProvider = imageProjector == null ? null : new AuthenticTOTPProvider(this);
        eMailHandler = configuration.get(MAIL_ENABLED) ? new AuthenticEMailHandler(this) : null;

        authorizationProvider = new AuthenticAuthorizationProvider<>(this);
        commandProvider = new CommandProvider<>(this);

        if (version.dev()) {
            logger.warn("!! YOU ARE RUNNING A DEVELOPMENT BUILD OF JTLOGIN !!");
            logger.warn("!! THIS IS NOT A RELEASE, USE THIS ONLY IF YOU WERE INSTRUCTED TO DO SO. DO NOT USE THIS IN PRODUCTION !!");
        }

        if (pluginPresent("floodgate")) {
            logger.info("Floodgate detected, enabling bedrock support...");
            floodgateApi = new FloodgateIntegration();
        }

        if (pluginPresent("luckperms")) {
            logger.info("LuckPerms detected, enabling context provider");
            luckpermsApi = new LuckPermsIntegration<>(this);
        }

        if (multiProxyEnabled()) {
            logger.info("Detected MultiProxy setup, enabling MultiProxy support...");
        }
    }

    public <C extends DatabaseConnector<?, ?>> DatabaseConnectorRegistration<?, C> getDatabaseConnector(Class<C> clazz) {
        return (DatabaseConnectorRegistration<?, C>) databaseConnectors.get(clazz);
    }

    private void connectToDB() {
        logger.info("Connecting to the database...");

        try {
            var registration = readProviders.get(configuration.get(DATABASE_TYPE));
            if (registration == null) {
                logger.error("Database type %s doesn't exist, please check your configuration".formatted(configuration.get(DATABASE_TYPE)));
                shutdownProxy(1);
            }

            DatabaseConnector<?, ?> connector = null;

            if (registration.databaseConnector() != null) {
                var connectorRegistration = getDatabaseConnector(registration.databaseConnector());

                if (connectorRegistration == null) {
                    logger.error("Database type %s is corrupted, please use a different one".formatted(configuration.get(DATABASE_TYPE)));
                    shutdownProxy(1);
                }

                connector = connectorRegistration.factory().apply("database.properties." + connectorRegistration.id() + ".");

                connector.connect();
            }

            var provider = registration.create(connector);

            if (provider instanceof ReadWriteDatabaseProvider casted) {
                databaseProvider = casted;
                databaseConnector = connector;
            } else {
                logger.error("Database type %s cannot be used for writing, please use a different one".formatted(configuration.get(DATABASE_TYPE)));
                shutdownProxy(1);
            }

        } catch (Exception e) {
            var cause = GeneralUtil.getFurthestCause(e);
            logger.error("!! THIS IS MOST LIKELY NOT AN ERROR CAUSED BY JTLOGIN !!");
            logger.error("Failed to connect to the database, this most likely is caused by wrong credentials. Cause: %s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()));
            shutdownProxy(1);
        }

        logger.info("Successfully connected to the database");

        if (databaseProvider instanceof AuthenticDatabaseProvider<?> casted) {
            logger.info("Validating schema");

            try {
                casted.validateSchema();
            } catch (Exception e) {
                var cause = GeneralUtil.getFurthestCause(e);
                logger.error("Failed to validate schema! Cause: %s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()));
                logger.error("Please open an issue on our GitHub, or visit Discord support");
                shutdownProxy(1);
            }

            logger.info("Schema validated");
        }
    }

    private void loadConfigs() {
        logger.info("Loading messages...");

        messages = new HoconMessages(logger);

        try {
            messages.reload(this);
        } catch (IOException e) {
            e.printStackTrace();
            logger.info("An unknown exception occurred while attempting to load the messages, this most likely isn't your fault");
            shutdownProxy(1);
        } catch (CorruptedConfigurationException e) {
            var cause = GeneralUtil.getFurthestCause(e);
            logger.error("!! THIS IS MOST LIKELY NOT AN ERROR CAUSED BY JTLOGIN !!");
            logger.error("!!The messages are corrupted, please look below for further clues. If you are clueless, delete the messages and a new ones will be created for you. Cause: %s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()));
            shutdownProxy(1);
        }

        logger.info("Loading configuration...");

        var defaults = new ArrayList<BiHolder<Class<?>, String>>();

        for (DatabaseConnectorRegistration<?, ?> value : databaseConnectors.values()) {
            if (value.configClass() == null) continue;
            defaults.add(new BiHolder<>(value.configClass(), "database.properties." + value.id() + "."));
            defaults.add(new BiHolder<>(value.configClass(), "migration.old-database." + value.id() + "."));
        }

        configuration = new HoconPluginConfiguration(logger, defaults);

        try {
            if (configuration.reload(this)) {
                logger.warn("!! A new configuration was generated, please fill it out, if in doubt, see the wiki !!");
                shutdownProxy(0);
            }

            var limbos = configuration.get(LIMBO);
            var lobby = configuration.get(LOBBY);

            for (String value : lobby.values()) {
                if (limbos.contains(value)) {
                    throw new CorruptedConfigurationException("Lobby server/world %s is also a limbo server/world, this is not allowed".formatted(value));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.info("An unknown exception occurred while attempting to load the configuration, this most likely isn't your fault");
            shutdownProxy(1);
        } catch (CorruptedConfigurationException e) {
            var cause = GeneralUtil.getFurthestCause(e);
            logger.error("!! THIS IS MOST LIKELY NOT AN ERROR CAUSED BY JTLOGIN !!");
            logger.error("!!The configuration is corrupted, please look below for further clues. If you are clueless, delete the config and a new one will be created for you. Cause: %s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()));
            shutdownProxy(1);
        }
    }

    private void setupDB() {
        registerDatabaseConnector(new DatabaseConnectorRegistration<>(
                        prefix -> new AuthenticMySQLDatabaseConnector(this, prefix),
                        AuthenticMySQLDatabaseConnector.Configuration.class,
                        "mysql"
                ),
                MySQLDatabaseConnector.class);
        registerDatabaseConnector(new DatabaseConnectorRegistration<>(
                        prefix -> new AuthenticSQLiteDatabaseConnector(this, prefix),
                        AuthenticSQLiteDatabaseConnector.Configuration.class,
                        "sqlite"
                ),
                SQLiteDatabaseConnector.class);
        registerDatabaseConnector(new DatabaseConnectorRegistration<>(
                        prefix -> new AuthenticPostgreSQLDatabaseConnector(this, prefix),
                        AuthenticPostgreSQLDatabaseConnector.Configuration.class,
                        "postgresql"
                ),
                PostgreSQLDatabaseConnector.class);

        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JTLoginMySQLDatabaseProvider(connector, this),
                "jtlogin-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JTLoginMySQLDatabaseProvider(connector, this),
                LegacyNames.DATABASE_TYPE_MYSQL, // LibreLogin's id, kept so existing configurations keep working
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JTLoginSQLiteDatabaseProvider(connector, this),
                "jtlogin-sqlite",
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JTLoginSQLiteDatabaseProvider(connector, this),
                LegacyNames.DATABASE_TYPE_SQLITE, // LibreLogin's id, kept so existing configurations keep working
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JTLoginPostgreSQLDatabaseProvider(connector, this),
                "jtlogin-postgresql",
                PostgreSQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JTLoginPostgreSQLDatabaseProvider(connector, this),
                LegacyNames.DATABASE_TYPE_POSTGRESQL, // LibreLogin's id, kept so existing configurations keep working
                PostgreSQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new AegisSQLMigrateReadProvider(configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE), logger, connector),
                "aegis-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new AuthMeSQLMigrateReadProvider(configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE), logger, connector),
                "authme-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new AuthMeSQLMigrateReadProvider(configuration.get(MIGRATION_POSTGRESQL_OLD_DATABASE_TABLE), logger, connector),
                "authme-postgresql",
                PostgreSQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new AuthMeSQLMigrateReadProvider("authme", logger, connector),
                "authme-sqlite",
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new DBASQLMigrateReadProvider(configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE), logger, connector),
                "dba-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new JPremiumSQLMigrateReadProvider(configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE), logger, connector),
                "jpremium-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new NLoginSQLMigrateReadProvider("nlogin", logger, connector),
                "nlogin-sqlite",
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new NLoginSQLMigrateReadProvider("nlogin", logger, connector),
                "nlogin-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new FastLoginSQLMigrateReadProvider("premium", logger, connector, databaseConnector, premiumProvider),
                "fastlogin-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new FastLoginSQLMigrateReadProvider("premium", logger, connector, databaseConnector, premiumProvider),
                "fastlogin-sqlite",
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new UniqueCodeAuthSQLMigrateReadProvider("uniquecode_proxy_users", logger, connector, this),
                "uniquecodeauth-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new LoginSecuritySQLMigrateReadProvider("ls_players", logger, connector),
                "loginsecurity-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new LoginSecuritySQLMigrateReadProvider("ls_players", logger, connector),
                "loginsecurity-sqlite",
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new LimboAuthSQLMigrateReadProvider("AUTH", logger, connector),
                "limboauth-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new AuthySQLMigrateReadProvider("players", logger, connector),
                "authy-mysql",
                MySQLDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new AuthySQLMigrateReadProvider("players", logger, connector),
                "authy-sqlite",
                SQLiteDatabaseConnector.class
        ));
        registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new LogItSQLMigrateReadProvider(configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE), logger, connector),
                "logit-mysql",
                MySQLDatabaseConnector.class
        ));
        // Currently disabled as crazylogin stores all names in lowercase
        /*registerReadProvider(new ReadDatabaseProviderRegistration<>(
                connector -> new CrazyLoginSQLMigrateReadProvider(configuration.get(MIGRATION_MYSQL_OLD_DATABASE_TABLE), logger, connector),
                "crazylogin-mysql",
                MySQLDatabaseConnector.class
        ));*/
    }

    private void loadForbiddenPasswords() throws IOException {
        var file = new File(getDataFolder(), "forbidden-passwords.txt");

        if (!file.exists()) {
            // The full list (about one million entries) is bundled with the jar, so nothing is downloaded from a
            // remote repository. Falling back to the two-entry template would silently weaken password validation,
            // so that only happens if the bundled list is missing, and it is reported loudly.
            logger.info("Forbidden passwords list doesn't exist, creating it from the bundled list...");
            try (var full = getResourceAsStream("forbidden-passwords-full.txt")) {
                if (full != null) {
                    Files.copy(full, file.toPath());
                } else {
                    logger.warn("!! The bundled forbidden passwords list is missing from this jar, using the short template instead !!");
                    try (var template = getResourceAsStream("forbidden-passwords-template.txt")) {
                        Files.copy(template, file.toPath());
                    }
                }
            }
        }

        try (var reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("# ")) {
                    continue;
                }
                forbiddenPasswords.add(line.toUpperCase(Locale.ROOT));
            }
        }
    }

    public UUID generateNewUUID(String name, @Nullable UUID premiumID) {
        return switch (configuration.getNewUUIDCreator()) {
            case RANDOM -> UUID.randomUUID();
            case MOJANG -> premiumID == null ? GeneralUtil.getCrackedUUIDFromName(name) : premiumID;
            case CRACKED -> GeneralUtil.getCrackedUUIDFromName(name);
        };
    }

    protected void disable() {
        if (databaseConnector != null) {
            try {
                databaseConnector.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Failed to disconnect from database, ignoring...");
            }
        }
        if (luckpermsApi != null) {
            luckpermsApi.disable();
        }
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public HoconPluginConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public HoconMessages getMessages() {
        return messages;
    }

    @Override
    public void checkDataFolder() {
        var folder = getDataFolder();

        if (!folder.exists()) if (!folder.mkdir()) throw new RuntimeException("Failed to create datafolder");
    }

    protected abstract Logger provideLogger();

    public abstract CommandManager<?, ?, ?, ?, ?, ?> provideManager();

    public abstract P getPlayerFromIssuer(CommandIssuer issuer);

    public abstract void authorize(P player, User user, Audience audience);

    public abstract CancellableTask delay(Runnable runnable, long delayInMillis);

    public abstract CancellableTask repeat(Runnable runnable, long delayInMillis, long repeatInMillis);

    public abstract boolean pluginPresent(String pluginName);

    protected abstract AuthenticImageProjector<P, S> provideImageProjector();

    public PremiumUser getUserOrThrowICA(String username) throws InvalidCommandArgument {
        try {
            return getPremiumProvider().getUserForName(username);
        } catch (PremiumException e) {
            throw new InvalidCommandArgument(getMessages().getMessage(
                    switch (e.getIssue()) {
                        case THROTTLED -> "error-premium-throttled";
                        default -> "error-premium-unknown";
                    }
            ));
        }
    }

    @Override
    public AuthenticAuthorizationProvider<P, S> getAuthorizationProvider() {
        return authorizationProvider;
    }

    @Override
    public CryptoProvider getCryptoProvider(String id) {
        return cryptoProviders.get(id);
    }

    @Override
    public void registerCryptoProvider(CryptoProvider provider) {
        cryptoProviders.put(provider.getIdentifier(), provider);
    }

    @Override
    public CryptoProvider getDefaultCryptoProvider() {
        return getCryptoProvider(configuration.get(DEFAULT_CRYPTO_PROVIDER));
    }

    @Override
    public void migrate(ReadDatabaseProvider from, WriteDatabaseProvider to) {
        logger.info("Reading data...");
        var users = from.getAllUsers();
        logger.info("Data read, inserting into database...");
        to.insertUsers(users);
    }

    @Override
    public AuthenticEventProvider<P, S> getEventProvider() {
        return eventProvider;
    }

    public LoginTryListener<P, S> getLoginTryListener() {
        return loginTryListener;
    }

    public void onExit(P player) {
        cancelOnExit.removeAll(player).forEach(CancellableTask::cancel);
        if (configuration.get(REMEMBER_LAST_SERVER)) {
            var server = platformHandle.getPlayersServerName(player);
            if (server == null) return;
            var user = databaseProvider.getByUUID(platformHandle.getUUIDForPlayer(player));
            if (user != null && !getConfiguration().get(LIMBO).contains(server)) {
                user.setLastServer(server);
                databaseProvider.updateUser(user);
            }
        }
    }

    public void cancelOnExit(CancellableTask task, P player) {
        cancelOnExit.put(player, task);
    }

    public boolean floodgateEnabled() {
        return floodgateApi != null;
    }

    public boolean luckpermsEnabled() {
        return luckpermsApi != null;
    }

    public boolean fromFloodgate(UUID uuid) {
        return floodgateApi != null && uuid != null && floodgateApi.isFloodgateId(uuid);
    }

    protected void shutdownProxy(int code) {
        //noinspection finally
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        } finally {
            System.exit(code);
        }
    }

    public abstract Audience getAudienceFromIssuer(CommandIssuer issuer);

    protected boolean mainThread() {
        return false;
    }

    public void reportMainThread() {
        if (mainThread()) {
            logger.error("AN IO OPERATION IS BEING PERFORMED ON THE MAIN THREAD! THIS IS A SERIOUS BUG!, PLEASE REPORT IT TO THE DEVELOPER OF THE PLUGIN AND ATTACH THE STACKTRACE BELOW!");
            new Throwable().printStackTrace();
        }
    }

    public boolean fromFloodgate(String username) {
        return floodgateApi != null && floodgateApi.getPlayer(username) != null;
    }

    protected java.util.logging.Logger getSimpleLogger() {
        return null;
    }
}