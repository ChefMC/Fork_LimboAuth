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

import net.elytrium.limboauth._mine_by_.Lang;
import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.event.AuthUnregisterEvent;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;

public class UnregisterCommand extends RatelimitedCommand {

  private final LimboAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final String confirmKeyword;
  private final Component notPlayer;

  public UnregisterCommand(LimboAuth plugin, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.playerDao = playerDao;

    Serializer serializer = LimboAuth.getSerializer();
    this.confirmKeyword = Settings.IMP.MAIN.CONFIRM_KEYWORD;
    this.notPlayer = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PLAYER);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (source instanceof Player proxyPlayer) {
      if (args.length == 2) {
        if (this.confirmKeyword.equalsIgnoreCase(args[1])) {
          String username = proxyPlayer.getUsername();
          String usernameLowercase = username.toLowerCase(Locale.ROOT);
          RegisteredPlayer player = AuthSessionHandler.fetchInfoLowercased(this.playerDao, usernameLowercase);
          if (player == null) {
            source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("NOT_REGISTERED", proxyPlayer)));
          } else if (player.getHash().isEmpty()) {
            source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("CRACKED_COMMAND", proxyPlayer)));
          } else if (AuthSessionHandler.checkPassword(args[0], player, this.playerDao)) {
            try {
              this.plugin.getServer().getEventManager().fireAndForget(new AuthUnregisterEvent(username));
              this.playerDao.deleteById(usernameLowercase);
              this.plugin.removePlayerFromCacheLowercased(usernameLowercase);
              proxyPlayer.disconnect(LimboAuth.getSerializer().deserialize(Lang.__("UNREGISTER_SUCCESSFUL", proxyPlayer)));
            } catch (SQLException e) {
              source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("ERROR_OCCURRED", proxyPlayer)));
              throw new SQLRuntimeException(e);
            }
          } else {
            source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("WRONG_PASSWORD", proxyPlayer)));
          }

          return;
        }
      }

      source.sendMessage(LimboAuth.getSerializer().deserialize(Lang.__("UNREGISTER_USAGE", proxyPlayer)));
    } else {
      source.sendMessage(this.notPlayer);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.UNREGISTER
        .hasPermission(invocation.source(), "limboauth.commands.unregister");
  }
}
