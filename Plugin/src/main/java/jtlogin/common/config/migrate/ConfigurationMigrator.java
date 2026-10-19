/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.config.migrate;

import jtlogin.api.Logger;
import jtlogin.common.config.ConfigurateHelper;
import jtlogin.common.config.key.ConfigurationKey;

public interface ConfigurationMigrator {

    void migrate(ConfigurateHelper helper, Logger logger);

    default void rename(String from, ConfigurationKey<?> to, ConfigurateHelper helper) {
        helper.set(to.key(), to.getter().apply(helper, from));
        helper.set(from, null);
    }

}
