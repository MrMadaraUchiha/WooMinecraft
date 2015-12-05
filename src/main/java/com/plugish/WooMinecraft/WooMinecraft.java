/*
 * Woo Minecraft Donation plugin
 * Author:	   Jerry Wood
 * Author URI: http://plugish.com
 * License:	   GPLv2
 * 
 * Copyright 2014 All rights Reserved
 * 
 */
package com.plugish.WooMinecraft;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.JSONArray;
import org.json.JSONObject;

import com.plugish.WooMinecraft.Commands.WooCommand;

public final class WooMinecraft extends JavaPlugin {

	public static Logger log;
	public static WooMinecraft instance;
	public static String configPath = "WooMinecraft";
	public static String urlPath = configPath+".web";
	public File englishFile;
	public FileConfiguration english;
	public File configFile;
	public FileConfiguration config;
	
	public static BukkitRunner runnerNew;
	public static FileConfiguration c;

	public void initalizePlugin() {
		configFile = new File(getDataFolder(), "config.yml");
		englishFile = new File(getDataFolder(), "english.yml");
		config = new YamlConfiguration();
		english = new YamlConfiguration();
		WooDefaults.initDefaults();
		WooDefaults.loadYamls();
		log.info("[Woo] Initialized Config and Messages System.");
	}
	
	@Override
	public void onEnable(){
		log = getLogger();
		instance = this;
		log.info("[Woo] Initializing Config and Messages System.");
		initalizePlugin();
		log.info("[Woo] Initializing Commands");
		initCommands();
		log.info("[Woo] Commands Initialized");
		this.runnerNew = new BukkitRunner(instance);
		this.runnerNew.runTaskTimerAsynchronously(instance, c.getInt(urlPath+".time_delay") * 20, c.getInt(urlPath+".time_delay") * 20);
		log.info("[Woo] Donation System Enabled!");
	}
	
	@Override
	public void onDisable(){
		saveConfig();
		log.info("[Woo] Donation System Disabled!");
	}
	
	/**
	 * Grabs stream data from the Website
	 * 
	 * @return DataOutputStream
	 */
	public DataOutputStream getSiteStream() {
		String sPath = c.getString(urlPath + ".url");
		String key = c.getString(urlPath + ".key");
		URL url;
		HttpURLConnection con;
		
		try {
			url = new URL( sPath + "?woo_minecraft=check&key=" + key );
			
			// Type-cast to HTTPURLConnection since it extends URLConnection which is what's returned from openConnection()
			con = (HttpURLConnection) url.openConnection();
			
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
			con.setDoInput(true);
			con.setDoOutput(true);			
		} catch( IOException e ) {
			log.severe( e.getMessage() );
		}
		
		return new DataOutputStream( con.getOutputStream() );
		
	}
	
	/**
	 * Generates a comma delimited list of player names
	 * 
	 * @return String
	 */
	public String getPlayerList() {
		// Build post data based on player list
		StringBuilder sb = new StringBuilder();
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			sb.append(player.getName() + ", ");
		}
		String playerList = sb.toString();
		
		// Remove the last and final comma
		Pattern pattern = Pattern.compile(", $");
		Matcher matcher = pattern.matcher(playerList);
		
		return matcher.replaceAll("");
	}

	/**
	 * Checks all online players against the
	 * webiste's database looking for pending donation deliveries
	 * 
	 * @return boolean
	 */
	public boolean check() {

		ArrayList<Integer> rowUpdates = new ArrayList<Integer>();
		try {
			// Check for player counts first
			Collection<? extends Player> list = Bukkit.getOnlinePlayers();
			if (list.size() < 1) return false;
			
			String playerlist = getPlayerList();
			
			String urlParams = playerList;
			
			DataOutputStream wr = ;
			wr.writeBytes("names=" + urlParams);
			wr.flush();
			wr.close();

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			// ENDNEW

			StringBuilder sb2 = new StringBuilder();
			String line;
			while ((line = in.readLine()) != null) {
				sb2.append(line);
			}
			in.close();

			JSONObject json = new JSONObject(sb2.toString());

			if (json.getString("status").equalsIgnoreCase("success")) {
				JSONArray jsonArr = json.getJSONArray("data");
				for (int i = 0; i < jsonArr.length(); i++) {
					JSONObject obj = jsonArr.getJSONObject(i);

					String playerName = obj.getString("player_name");
					String x = obj.getString("command");
					final String command = x.replace("%s", playerName);
					
					// @TODO: Update to getUUID()
					Player pN = Bukkit.getServer().getPlayer(playerName);

					if (x.substring(0, 3) == "give") {
						int count = 0;
						for (ItemStack iN : pN.getInventory()) {
							if (iN == null)
								count++;
						}

						if (count == 0) return false;
					}

					int id = obj.getInt("id");

					BukkitScheduler sch = Bukkit.getServer().getScheduler();
					sch.scheduleSyncDelayedTask(instance, new Runnable() {
						@Override
						public void run() {
							Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
						}
					}, 20L);
					rowUpdates.add(id);
				}
			} else {
				log.info("Check: No donations for online users. STATUS: " + json.getString("status"));
				if (json.has("debug_info")) {
					log.info(json.getString("debug_info"));
				}
			}
			remove(rowUpdates);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private void remove(ArrayList<Integer> ids) {
		if (ids.isEmpty()) return;

		try {
			String sPath = c.getString(urlPath + ".url");
			String key = c.getString(urlPath + ".key");

			URL url = new URL(sPath + "?woo_minecraft=update&key=" + key);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("POST");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
			String urlParams = StringUtils.join(ids, ',');
			con.setDoInput(true);
			con.setDoOutput(true);

			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.writeBytes("players=" + urlParams);
			wr.flush();
			wr.close();

			BufferedReader input = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String response = input.readLine();
			if (!response.equalsIgnoreCase("true")) {
				log.severe("Could not update donations.");
				log.info(response);
			} else {
				log.info("Donations updated");
			}
			input.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void initCommands() {
		getCommand("woo").setExecutor(new WooCommand());
	}
	
	public void stopServer() {
		ConsoleCommandSender console = Bukkit.getConsoleSender();
		Bukkit.dispatchCommand( console, "save-all" );
		Bukkit.dispatchCommand(console, "stop" );
	}
}
