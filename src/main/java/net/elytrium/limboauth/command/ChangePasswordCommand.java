/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth.command;

import by.mine.fork_limboauth.Lang;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.ChangePasswordEvent;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;

public class ChangePasswordCommand extends RatelimitedCommand {

  private final LimboAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final boolean needOldPass;
  private final Component notPlayer;

  public ChangePasswordCommand(LimboAuth plugin, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.playerDao = playerDao;

    Serializer serializer = LimboAuth.getSerializer();
    this.needOldPass = Settings.IMP.MAIN.CHANGE_PASSWORD_NEED_OLD_PASSWORD;
    this.notPlayer = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PLAYER);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (source instanceof Player proxyPlayer) {
      String usernameLowercase = proxyPlayer.getUsername().toLowerCase(Locale.ROOT);
      RegisteredPlayer player = AuthSessionHandler.fetchInfoLowercased(this.playerDao, usernameLowercase);

      if (player == null) {
        source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("NOT_REGISTERED", proxyPlayer)));
        return;
      }

      boolean onlineMode = player.getHash().isEmpty();
      boolean needOldPass = this.needOldPass && !onlineMode;
      if (needOldPass) {
        if (args.length < 2) {
          source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("CHANGE_PASSWORD_USAGE", proxyPlayer)));
          return;
        }

        if (!AuthSessionHandler.checkPassword(args[0], player, this.playerDao)) {
          source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("WRONG_PASSWORD", proxyPlayer)));
          return;
        }
      } else if (args.length < 1) {
        source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("CHANGE_PASSWORD_USAGE", proxyPlayer)));
        return;
      }

      try {
        final String oldHash = player.getHash();
        final String newPassword = needOldPass ? args[1] : args[0];
        final String newHash = RegisteredPlayer.genHash(newPassword);

        UpdateBuilder<RegisteredPlayer, String> updateBuilder = this.playerDao.updateBuilder();
        updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, usernameLowercase);
        updateBuilder.updateColumnValue(RegisteredPlayer.HASH_FIELD, newHash);
        updateBuilder.update();

        this.plugin.removePlayerFromCacheLowercased(usernameLowercase);

        this.plugin.getServer().getEventManager().fireAndForget(
            new ChangePasswordEvent(player, needOldPass ? args[0] : null, oldHash, newPassword, newHash));

        source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("CHANGE_PASSWORD_SUCCESSFUL", proxyPlayer)));
      } catch (SQLException e) {
        source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("ERROR_OCCURRED", proxyPlayer)));
        throw new SQLRuntimeException(e);
      }
    } else {
      source.sendMessage(this.notPlayer);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.CHANGE_PASSWORD
        .hasPermission(invocation.source(), "limboauth.commands.changepassword");
  }
}
