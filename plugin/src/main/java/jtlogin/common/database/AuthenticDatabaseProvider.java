/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.database;

import jtlogin.api.database.ReadWriteDatabaseProvider;
import jtlogin.api.database.connector.DatabaseConnector;
import jtlogin.common.AuthenticJTLogin;

public abstract class AuthenticDatabaseProvider<C extends DatabaseConnector<?, ?>> implements ReadWriteDatabaseProvider {

    protected final C connector;
    protected final AuthenticJTLogin<?, ?> plugin;

    protected AuthenticDatabaseProvider(C connector, AuthenticJTLogin<?, ?> plugin) {
        this.connector = connector;
        this.plugin = plugin;
    }

    public void validateSchema() {
    }

}
