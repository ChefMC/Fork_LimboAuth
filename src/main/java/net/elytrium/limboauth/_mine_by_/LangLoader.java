package net.elytrium.limboauth._mine_by_;

import net.elytrium.limboauth.Settings;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LangLoader {

	private static final String LANG_DIR = "lang";

	public static void load(Path pluginDir, Logger logger) {
		Path langDir = pluginDir.resolve(LANG_DIR);

		if (!Files.exists(langDir)) {
			logger.info("Lang directory not found, skipping language loading.");
			return;
		}

		if (!Files.isDirectory(langDir)) {
			logger.warn("Lang path exists but is not a directory!");
			return;
		}

		Lang.invalidate();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(langDir, "*.yml")) {
			for (Path file : stream) {
				loadFile(file, logger);
			}
		} catch (IOException e) {
			logger.error("Failed to read lang directory!");
			e.printStackTrace();
		}
	}

	private static void loadFile(Path file, Logger logger) {
		String fileName = file.getFileName().toString();
		String lang = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);

		try (InputStream in = Files.newInputStream(file)) {
			Yaml yaml = new Yaml();

			Map<String, Object> data;
			try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				data = yaml.load(reader);
			}

			Map<String, String> map = new HashMap<>();

			if (data != null) {
				for (Map.Entry<String, Object> entry : data.entrySet()) {
					if (entry.getValue() instanceof String value) {
						map.put(entry.getKey(), value.replace("{PRFX}", Settings.IMP.PREFIX));
					}
				}
			}

			Lang.loadLocale(lang, map);
			logger.info("Loaded language: {} ({} keys)", lang, map.size());

		} catch (Exception e) {
			logger.error("Failed to load lang file: {}", fileName);
			e.printStackTrace();
		}
	}
}
