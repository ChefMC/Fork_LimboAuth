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
import com.j256.ormlite.stmt.UpdateBuilder;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.handler.AuthSessionHandler;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

public class TotpCommand extends RatelimitedCommand {

  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final RecoveryCodeGenerator codesGenerator = new RecoveryCodeGenerator();
  private final Dao<RegisteredPlayer, String> playerDao;

  private final Component notPlayer;
  private final boolean needPassword;
  private final String issuer;
  private final String qrGeneratorUrl;
  private final int recoveryCodesAmount;

  public TotpCommand(Dao<RegisteredPlayer, String> playerDao) {
    this.playerDao = playerDao;

    Serializer serializer = LimboAuth.getSerializer();
    this.notPlayer = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PLAYER);
    this.needPassword = Settings.IMP.MAIN.TOTP_NEED_PASSWORD;
    this.issuer = Settings.IMP.MAIN.TOTP_ISSUER;
    this.qrGeneratorUrl = Settings.IMP.MAIN.QR_GENERATOR_URL;
    this.recoveryCodesAmount = Settings.IMP.MAIN.TOTP_RECOVERY_CODES_AMOUNT;
  }

  // TODO: Rewrite.
  @Override
  public void execute(CommandSource source, String[] args) {
    if (source instanceof Player proxyPlayer) {
      String lang = Lang.getPlayerLanguage(proxyPlayer);
      Serializer serializer = LimboAuth.getSerializer();
      if (args.length == 0) {
        source.sendMessage(serializer.deserialize(Lang.__("TOTP_USAGE", lang)));
      } else {
        String username = proxyPlayer.getUsername();
        String usernameLowercase = username.toLowerCase(Locale.ROOT);

        RegisteredPlayer playerInfo;
        UpdateBuilder<RegisteredPlayer, String> updateBuilder;
        if (args[0].equalsIgnoreCase("enable")) {
          if (this.needPassword ? args.length == 2 : args.length == 1) {
            playerInfo = AuthSessionHandler.fetchInfoLowercased(this.playerDao, usernameLowercase);
            if (playerInfo == null) {
              source.sendMessage(serializer.deserialize(Lang.__("NOT_REGISTERED", lang)));
              return;
            } else if (playerInfo.getHash().isEmpty()) {
              source.sendMessage(serializer.deserialize(Lang.__("CRACKED_COMMAND", lang)));
              return;
            } else if (this.needPassword && !AuthSessionHandler.checkPassword(args[1], playerInfo, this.playerDao)) {
              source.sendMessage(serializer.deserialize(Lang.__("WRONG_PASSWORD", lang)));
              return;
            }

            if (!playerInfo.getTotpToken().isEmpty()) {
              source.sendMessage(serializer.deserialize(Lang.__("TOTP_ALREADY_ENABLED", lang)));
              return;
            }

            String secret = this.secretGenerator.generate();
            try {
              updateBuilder = this.playerDao.updateBuilder();
              updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, usernameLowercase);
              updateBuilder.updateColumnValue(RegisteredPlayer.TOTP_TOKEN_FIELD, secret);
              updateBuilder.update();
            } catch (SQLException e) {
              source.sendMessage(serializer.deserialize(Lang.__("ERROR_OCCURRED", lang)));
              throw new SQLRuntimeException(e);
            }
            source.sendMessage(serializer.deserialize(Lang.__("TOTP_SUCCESSFUL", lang)));

            QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(this.issuer)
                .build();
            String qrUrl = this.qrGeneratorUrl.replace("{data}", URLEncoder.encode(data.getUri(), StandardCharsets.UTF_8));
            source.sendMessage(serializer.deserialize(Lang.__("TOTP_QR", lang)).clickEvent(ClickEvent.openUrl(qrUrl)));

            source.sendMessage(serializer.deserialize(Lang.__("TOTP_TOKEN", lang, secret))
                .clickEvent(ClickEvent.copyToClipboard(secret)));
            String codes = String.join(", ", this.codesGenerator.generateCodes(this.recoveryCodesAmount));
            source.sendMessage(serializer.deserialize(Lang.__("TOTP_RECOVERY", lang, codes))
                .clickEvent(ClickEvent.copyToClipboard(codes)));
          } else {
            source.sendMessage(serializer.deserialize(Lang.__("TOTP_USAGE", lang)));
          }
        } else if (args[0].equalsIgnoreCase("disable")) {
          if (args.length == 2) {
            playerInfo = AuthSessionHandler.fetchInfoLowercased(this.playerDao, usernameLowercase);

            if (playerInfo == null) {
              source.sendMessage(serializer.deserialize(Lang.__("ERROR_OCCURRED", lang)));
              return;
            }

            if (AuthSessionHandler.TOTP_CODE_VERIFIER.isValidCode(playerInfo.getTotpToken(), args[1])) {
              try {
                updateBuilder = this.playerDao.updateBuilder();
                updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, usernameLowercase);
                updateBuilder.updateColumnValue(RegisteredPlayer.TOTP_TOKEN_FIELD, "");
                updateBuilder.update();

                source.sendMessage(serializer.deserialize(Lang.__("TOTP_DISABLED", lang)));
              } catch (SQLException e) {
                source.sendMessage(serializer.deserialize(Lang.__("ERROR_OCCURRED", lang)));
                throw new SQLRuntimeException(e);
              }
            } else {
              source.sendMessage(serializer.deserialize(Lang.__("TOTP_WRONG", lang)));
            }
          } else {
            source.sendMessage(serializer.deserialize(Lang.__("TOTP_USAGE", lang)));
          }
        } else {
          source.sendMessage(serializer.deserialize(Lang.__("TOTP_USAGE", lang)));
        }
      }
    } else {
      source.sendMessage(this.notPlayer);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.TOTP
        .hasPermission(invocation.source(), "limboauth.commands.totp");
  }
}
