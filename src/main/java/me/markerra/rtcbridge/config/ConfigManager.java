package me.markerra.rtcbridge.config;

import com.google.gson.JsonSyntaxException;
import me.markerra.rtcbridge.util.ResourceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Config Manager class */
public final class ConfigManager {

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AppConfig config;

    private ConfigManager() {}

    public static synchronized void load() {
        Path externalConfigPath = Paths.get(CONFIG_FILE_NAME);

        try {
            // 1. if there's no config file then create a new one
            if (Files.notExists(externalConfigPath)) {
                System.out.println("config.json does not exist. Creating new config file..");

                // get default config.json from resources
                String defaultConfigContent = ResourceManager.loadResource(CONFIG_FILE_NAME);

                // save file on drive near runtime app
                Files.writeString(externalConfigPath, defaultConfigContent, StandardCharsets.UTF_8);
            }

            // 2. read file from drive
            String jsonContent = Files.readString(externalConfigPath, StandardCharsets.UTF_8);

            // 3. make a BridgeConfig from json
            try {
                config = GSON.fromJson(jsonContent, AppConfig.class);

                if (config == null) {
                    config = new AppConfig(null, null, null);
                }

                System.out.println("config is loaded: " + config);
            } catch (JsonSyntaxException e) {
                System.out.println("Failed to load config: config.json contains invalid syntax");
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to create or load the config: ", e);
        }
    }

    public static AppConfig get() {
        if (config == null) {
            throw new IllegalStateException("ConfigManager has not been initialized yet.");
        }
        return config;
    }

    public static BrowserConfig browser() {
        return config.browser();
    }

    public static AudioConfig audio() {
        return config.audio();
    }

    public static BridgeConfig bridge() {
        return config.bridge();
    }

}
