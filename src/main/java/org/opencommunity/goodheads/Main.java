package org.opencommunity.goodheads;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.codehaus.plexus.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin {

    private Map<String, String[]> headData = new HashMap<>();

    public void onEnable() {
        // Check if the config file exists, and create it if it doesn't
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getConfig().addDefault("head-db-url", "https://minecraft-heads.4lima.de/csv/2022-02-25-ZgFDreHnLiGvHdf3RFfgg/Custom-Head-DB.csv");
            getConfig().options().copyDefaults(true);
            saveConfig();
        }

        // Retrieve head database URL from configuration file
        String headDbUrl = getConfig().getString("head-db-url");

        // Download head database file
        File headDbFile = new File(getDataFolder(), "Head-DB.csv");
        if (!headDbFile.exists()) {
            try {
                URL url = new URL(headDbUrl);
                FileUtils.copyURLToFile(url, headDbFile);
            } catch (IOException e) {
                getLogger().warning("Failed to download head database file: " + e.getMessage());
                return;
            }
        }

        // Read head database file and store head IDs, names, and textures in a map
        Map<String, String[]> headData = new ConcurrentHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(headDbFile.toPath())) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(";");
                if (values.length >= 6) {
                    String headId = values[1];
                    String headName = values[2];
                    String headTexture = values[3];
                    headData.put(headId, new String[]{headName, headTexture});
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to load heads: " + e.getMessage());
            return;
        }

        // Store headData map for later use
        this.headData = headData;

        getLogger().info("Loaded " + headData.size() + " heads.");
    }


    public void onDisable() {
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("hdb") && args.length >= 2 && args[0].equalsIgnoreCase("search")) {
            if (sender instanceof Player && !sender.hasPermission("goodheads.use")) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }
            // Code for /hdb search command
            String searchParam = args[1];
            String searchValue = "";
            if (searchParam.startsWith("id:")) {
                searchValue = searchParam.substring(3);
            } else {
                sender.sendMessage("Invalid search parameter: " + searchParam);
                return true;
            }

            String[] headData = this.headData.get(searchValue);
            if (headData == null) {
                sender.sendMessage("Head not found: " + searchValue);
                return true;
            }

            String headName = headData[0].replaceAll("\"", "");
            String headTextureUrl = "http://textures.minecraft.net/texture/" + headData[1];
            ItemStack playerHead = createHead(headName, headTextureUrl);

            if (sender instanceof Player) {
                ((Player) sender).getInventory().addItem(playerHead);
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("gh") && args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            // Code for /gh give command
            if (sender instanceof Player && !sender.hasPermission("goodheads.use")) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("Please provide a Minecraft URL.");
                return true;
            }

            String textureUrl = "http://textures.minecraft.net/texture/" + args[1];
            ItemStack playerHead = createHead("Player Head", textureUrl);

            if (sender instanceof Player) {
                ((Player) sender).getInventory().addItem(playerHead);
            }

            return true;
        }

        return false;
    }

    public ItemStack createHead(String headName, String textureUrl) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);

        // Encode texture URL to base64
        String texture = Base64.getEncoder().encodeToString(String.format("{textures:{SKIN:{url:\"%s\"}}}", textureUrl).getBytes());

        // Set texture property in game profile
        profile.getProperties().put("textures", new Property("textures", texture));

        // Set game profile in skull meta
        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        meta.setDisplayName(headName);
        item.setItemMeta(meta);
        return item;
    }

}

