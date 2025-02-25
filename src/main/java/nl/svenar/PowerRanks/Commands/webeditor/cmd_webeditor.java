package nl.svenar.PowerRanks.Commands.webeditor;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;

import nl.svenar.PowerRanks.PowerRanks;
import nl.svenar.PowerRanks.Cache.CacheManager;
import nl.svenar.PowerRanks.Commands.PowerCommand;
import nl.svenar.PowerRanks.Data.Messages;
import nl.svenar.PowerRanks.Data.PowerRanksVerbose;
import nl.svenar.common.http.DatabinClient;
import nl.svenar.common.storage.PowerStorageManager;
import nl.svenar.common.storage.provided.JSONStorageManager;

public class cmd_webeditor extends PowerCommand {

	private String tellraw_url = "tellraw %player% [\"\",{\"text\":\"Web editor is ready \",\"color\":\"dark_green\"},{\"text\":\"[\",\"color\":\"black\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%url%\"}},{\"text\":\"click to open\",\"color\":\"dark_green\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%url%\"}},{\"text\":\"]\",\"color\":\"black\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"%url%\"}}]";

	private String powerranks_webeditor_url = "https://editor.powerranks.nl/?id=";

	public cmd_webeditor(PowerRanks plugin, String command_name, COMMAND_EXECUTOR ce) {
		super(plugin, command_name, ce);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (sender.hasPermission("powerranks.cmd.webeditor")) {
			if (args.length == 0) {
				// No args
			} else if (args.length == 1 || args.length == 2) {
				final String webeditorCommand = args[0].toLowerCase();

				switch (webeditorCommand) {
				case "start":
					startWebeditor(sender);
					break;
				case "load":
					if (args.length == 2) {
						loadWebeditor(sender, args[1]);
					} else {
						Messages.messageCommandUsageWebeditor(sender);
					}
					break;
				default:
					Messages.messageCommandUsageWebeditor(sender);
					break;
				}

			} else {
				Messages.messageCommandUsageWebeditor(sender);
			}
		} else {
			Messages.noPermission(sender);
		}

		return false;
	}

	private void startWebeditor(CommandSender sender) {
		Messages.preparingWebeditor(sender);

		JSONStorageManager jsonmanager = new JSONStorageManager(PowerRanks.fileLoc, "dummyRanks.json",
				"dummyPlayers.json");
		PowerStorageManager powermanager = CacheManager.getStorageManager();

		jsonmanager.setRanks(powermanager.getRanks());
		jsonmanager.setPlayers(powermanager.getPlayers());

		String outputJSON = "";

		outputJSON += "{";
		outputJSON += "\"serverdata\":";
		outputJSON += getServerDataAsJSON();
		outputJSON += ",";
		outputJSON += "\"rankdata\":";
		outputJSON += jsonmanager.getRanksAsJSON(false);
		outputJSON += ",";
		outputJSON += "\"playerdata\":";
		outputJSON += jsonmanager.getPlayersAsJSON(false);
		outputJSON += ",";
		outputJSON += "\"usertags\":";
		outputJSON += PowerRanks.getUsertagManager().toJSON("usertags", false);
		outputJSON += "}";

		jsonmanager.removeAllData();

		// PowerRanks.getInstance().getLogger().info("JSON: ");
		// PowerRanks.getInstance().getLogger().info(outputJSON);

		DatabinClient client = new DatabinClient("https://databin.svenar.nl", "Databinclient/1.0");

		client.postJSON(outputJSON);

		int uploadSize = outputJSON.length() / 1024;
		int updateInterval = 5;
		int timeout = 5;

		new BukkitRunnable() {
			int waitTime = 0;

			@Override
			public void run() {
				PowerRanksVerbose.log("task", "Running task uploading webeditor data");

				if (client.hasResponse()) {
					String key = client.getResponse().get("key");

					if (key.length() > 0 && !key.startsWith("[FAILED]")) {
						sender.sendMessage(ChatColor.DARK_AQUA + "===----------" + ChatColor.DARK_BLUE
								+ PowerRanks.pdf.getName() + ChatColor.DARK_AQUA + "----------===");
						// sender.sendMessage(ChatColor.DARK_GREEN + getIdentifier() + ChatColor.GREEN +
						// " v" + getVersion());
						if (sender instanceof Player) {
							Bukkit.getServer().dispatchCommand((CommandSender) Bukkit.getServer().getConsoleSender(),
									tellraw_url.replaceAll("%player%", sender.getName())
											.replaceAll("%url%", powerranks_webeditor_url + key).replaceAll("\n", ""));
						} else {
							sender.sendMessage(ChatColor.DARK_GREEN + "Web editor is ready " + ChatColor.BLACK + "["
									+ ChatColor.GREEN + powerranks_webeditor_url + key + ChatColor.BLACK + "]");
						}
						sender.sendMessage(ChatColor.DARK_GREEN + "Editor ID: " + ChatColor.GREEN + key);
						sender.sendMessage(ChatColor.DARK_GREEN + "Uploaded: " + ChatColor.GREEN + uploadSize + "KB");
						sender.sendMessage(ChatColor.DARK_AQUA + "===------------------------------===");
					}

					this.cancel();
				}

				if (waitTime / (20 / updateInterval) > timeout) {
					this.cancel();

					Messages.webeditorTimedout(sender);
				}

				waitTime++;
			}
		}.runTaskTimer(PowerRanks.getInstance(), 0, updateInterval);
	}

	private void loadWebeditor(CommandSender sender, String key) {
		Messages.downloadingWebeditorData(sender);

		DatabinClient client = new DatabinClient("https://databin.svenar.nl", "Databinclient/1.0");

		client.getJSON(key);

		int updateInterval = 5;
		int timeout = 5;

		new BukkitRunnable() {
			int waitTime = 0;

			@Override
			public void run() {
				PowerRanksVerbose.log("task", "Running task downloading webeditor data");

				if (client.hasResponse()) {
					this.cancel();

					String rawJSON = client.getRawResponse();
					Gson gson = new Gson();
					Type mapType = new TypeToken<Map<String, Object>>() {
					}.getType();
					Map<String, Object> jsonData = gson.fromJson(rawJSON, mapType);
					handleWebeditorDownload(sender, jsonData);
				}

				if (waitTime / (20 / updateInterval) > timeout) {
					this.cancel();

					Messages.webeditorTimedout(sender);
				}

				waitTime++;
			}
		}.runTaskTimer(PowerRanks.getInstance(), 0, updateInterval);
	}

	public void handleWebeditorDownload(CommandSender sender, Map<String, Object> jsonData) {

		LinkedTreeMap<?, ?> serverData = (LinkedTreeMap<?, ?>) jsonData.get("serverdata");

		if (Objects.isNull(serverData) || !serverData.containsKey("powerranksVersion")) {
			PowerRanks.getInstance().getLogger().warning(serverData.toString());
			Messages.downloadedInvalidWebeditorData(sender);
			return;
		}

		if (!((String) serverData.get("powerranksVersion")).equals(PowerRanks.getVersion())) {
			Messages.incompattiblePowerRanksVersionWebeditor(sender, (String) serverData.get("powerranksVersion"),
					PowerRanks.getVersion());
			return;
		}

		LinkedTreeMap<?, ?> rankData = (LinkedTreeMap<?, ?>) jsonData.get("rankdata");
		LinkedTreeMap<?, ?> playerData = (LinkedTreeMap<?, ?>) jsonData.get("playerdata");
		LinkedTreeMap<?, ?> usertags = (LinkedTreeMap<?, ?>) jsonData.get("usertags");

		JSONStorageManager jsonmanager = new JSONStorageManager(PowerRanks.fileLoc, "dummyRanks.json",
				"dummyPlayers.json");

		if (!(rankData instanceof LinkedTreeMap<?, ?> && playerData instanceof LinkedTreeMap<?, ?>)) {
			Messages.FailedDownloadingWebeditorData(sender);
			return;
		}

		CacheManager.setRanks(jsonmanager.getRanksFromJSON(rankData));
		CacheManager.setPlayers(jsonmanager.getPlayersFromJSON(playerData));

		CacheManager.save();

		jsonmanager.removeAllData();

		PowerRanks.getUsertagManager().fromJSON("usertags", usertags);

		Messages.downloadedWebeditorData(sender);
		Messages.LoadedRanksPlayersWebeditor(sender, CacheManager.getRanks().size(), CacheManager.getPlayers().size());
	}

	public ArrayList<String> tabCompleteEvent(CommandSender sender, String[] args) {
		ArrayList<String> tabcomplete = new ArrayList<String>();

		if (args.length == 1) {
			tabcomplete.add("start");
			tabcomplete.add("load");
		}

		return tabcomplete;
	}

	private String getServerDataAsJSON() {
		String output = "";

		List<String> server_permissions = new ArrayList<String>();

		for (PermissionAttachmentInfo permission : Bukkit.getServer().getConsoleSender().getEffectivePermissions()) {
			server_permissions.add("\"" + permission.getPermission() + "\"");
		}

		output += "{";
		output += "\"powerranksVersion\":";
		output += "\"" + PowerRanks.getVersion() + "\"";
		output += ",\"serverPermissions\":";
		output += "[" + String.join(",", server_permissions) + "]";
		output += "}";

		return output;
	}
}
