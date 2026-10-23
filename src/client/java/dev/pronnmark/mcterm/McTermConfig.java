package dev.pronnmark.mcterm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** {@code config/mcterm.json}. Written with defaults on first run. */
public final class McTermConfig {
	private static final Logger LOG = LoggerFactory.getLogger("mcterm");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Command run in the pseudo-terminal. Your own ssh client, keys and ~/.ssh/config are used. */
	public List<String> command = List.of("ssh", "-t", "pbox", "herdr");

	/**
	 * Command a computer runs. A computer is a sign whose first line is [pc]; the other lines are
	 * its name, and {name} is replaced by it. Each computer is its own session and its own herdr view.
	 */
	public List<String> computerCommand = List.of("ssh", "-t", "pbox", "herdr");

	/** Keys typed once the computer's program is up; {name} is replaced. Default: Ctrl+K, the name, Enter (herdr's space picker). */
	public String computerKeys = "\u000b{name}\r";

	/** Extra environment variables for the command. */
	public Map<String, String> env = Map.of();

	/** Font pixel size: 15, 20, 25, 30, 35 or 40. 0 picks one from the window size. */
	public int fontSize = 0;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mcterm.json");
	}

	public void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.warn("Could not write {}", file, e);
		}
	}

	public static McTermConfig load() {
		Path file = file();
		McTermConfig config = null;
		if (Files.isRegularFile(file)) {
			try {
				config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), McTermConfig.class);
			} catch (IOException | RuntimeException e) {
				LOG.error("Could not read {}; using defaults", file, e);
			}
		}
		if (config == null) {
			config = new McTermConfig();
			config.save();
		}
		if (config.command == null || config.command.isEmpty()) config.command = new McTermConfig().command;
		if (config.env == null) config.env = Map.of();
		if (config.computerCommand == null || config.computerCommand.isEmpty()) config.computerCommand = new McTermConfig().computerCommand;
		if (config.computerKeys == null) config.computerKeys = "";
		return config;
	}
}
