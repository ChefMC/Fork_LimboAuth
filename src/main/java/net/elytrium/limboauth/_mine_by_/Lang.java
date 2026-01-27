package net.elytrium.limboauth._mine_by_;

import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboauth.LimboAuth;

import java.util.*;

public class Lang {

	private static final Set<String> existingLangs = new HashSet<>();
	public  static final Map<UUID, String> playerLangs = new HashMap<>();
	private static final Map<String, Map<String, String>> messages = new HashMap<>();

	public static void invalidate() {
		existingLangs.clear();
		playerLangs.clear();
		messages.clear();
	}

	public static void loadLocale(String lang, Map<String, String> strings) {
		existingLangs.add(lang);
		messages.put(lang, strings);
	}

	public static void newStrings(String lang, Map<String, String> strings) {
		messages.put(lang, strings);
	}

	public static String __(String key, Player player) {
		return __(key, getPlayerLanguage(player));
	}

	public static String __(String key, Player player, Object... args) {
		String message = __(key, player);
		for (int i = 0; i < args.length; i++) {
			message = message.replace("{" + i + "}", String.valueOf(args[i]));
		}
		return message;
	}

	public static String __(String key, String language, Object... args) {
		Map<String, String> langMessages = messages.get(language);
		if (langMessages == null) {
			langMessages = messages.get("en"); // Fallback to English
		}
		String message = langMessages.get(key);
		if (message == null) return "[MISSING TRANSLATION: " + key + "]";
		for (int i = 0; i < args.length; i++) {
			message = message.replace("{" + i + "}", String.valueOf(args[i]));
		}
		return message;
	}

	public static String getPlayerLanguage(Player player) {
		String lang = playerLangs.get(player.getUniqueId());

		if (lang != null) {
			return lang;
		}

		String locale = null;
		Locale localeObj = player.getEffectiveLocale();
		if (localeObj != null) {
			locale = localeObj.toLanguageTag().replace("-", "_").toLowerCase(Locale.ROOT);
			LimboAuth.getLogger().info("Language of {}: {}", player.getUsername(), locale);
		} else {
			LimboAuth.getLogger().info("Language of {}: null", player.getUsername());
		}

		if (locale != null) {

			if (existingLangs.contains(locale)) {
				playerLangs.put(player.getUniqueId(), locale);
				return locale;
			}

			// Russian-speaking locales (just fallback and only if any exact of them is not presented in config)
			if (
					locale.startsWith("ru_") ||
					locale.startsWith("be_") ||
					locale.startsWith("kk_") ||
					locale.startsWith("ry_") ||
					locale.startsWith("sah_") ||
					locale.equals("tt_ru") ||
					locale.equals("ba_ru") ||
					locale.equals("uk_ua") ||
					locale.equals("rpr")
			) {
				playerLangs.put(player.getUniqueId(), "ru");
				return "ru";
			}

		}

		// Default to English (final fallback)
		playerLangs.put(player.getUniqueId(), "en"); // Commenting out isn't working :(
		return "en";
	}
}
