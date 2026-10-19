/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jtlogin.common.command.commands;

import co.aikar.commands.annotation.*;
import net.kyori.adventure.audience.Audience;
import jtlogin.api.event.events.WrongPasswordEvent.AuthenticationSource;
import jtlogin.common.AuthenticJTLogin;
import jtlogin.common.command.Command;
import jtlogin.common.command.InvalidCommandArgument;
import jtlogin.common.event.events.AuthenticPasswordChangeEvent;
import jtlogin.common.event.events.AuthenticWrongPasswordEvent;

import java.util.concurrent.CompletionStage;

@CommandAlias("changepassword|changepass|passwd|passch")
public class ChangePasswordCommand<P> extends Command<P> {
    public ChangePasswordCommand(AuthenticJTLogin<P, ?> plugin) {
        super(plugin);
    }

    @Default
    @Syntax("{@@syntax.change-password}")
    @CommandCompletion("%autocomplete.change-password")
    public CompletionStage<Void> onPasswordChange(Audience sender, P player, String oldPass, @Single String newPass) {
        return runAsync(() -> {
            var user = getUser(player);

            if (!user.isRegistered()) {
                throw new InvalidCommandArgument(getMessage("error-no-password"));
            }

            var hashed = user.getHashedPassword();
            var crypto = getCrypto(hashed);

            if (!crypto.matches(oldPass, hashed)) {
                plugin.getEventProvider()
                        .unsafeFire(plugin.getEventTypes().wrongPassword,
                                new AuthenticWrongPasswordEvent<>(user, player, plugin, AuthenticationSource.CHANGE_PASSWORD));
                throw new InvalidCommandArgument(getMessage("error-password-wrong"));
            }

            setPassword(sender, user, newPass, "info-editing");

            getDatabaseProvider().updateUser(user);

            sender.sendMessage(getMessage("info-edited"));

            plugin.getEventProvider().unsafeFire(plugin.getEventTypes().passwordChange, new AuthenticPasswordChangeEvent<>(user, player, plugin, hashed));
        });
    }

}
