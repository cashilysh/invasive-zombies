package invasivezombies.config;

import invasivezombies.VersionHelper; // Assuming this exists in your project

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ModConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("invasivezombies");

    private static final String MODIFIABLE_BLOCK_CONFIG_FILE = "invasivezombies/mineable.json";
    private static final String INTERNAL_BLOCK_CONFIG_FILE = "/data/mineable.json"; // From JAR

    private static final String MODIFIABLE_SETTINGS_CONFIG_FILE = "invasivezombies/settings.json";
    private static final String INTERNAL_SETTINGS_CONFIG_FILE = "/data/settings.json"; // From JAR

    private static ZombieSettings zombieSettings;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_BLOCKS = 1000;
    private static final int MAX_FILE_SIZE = 1024 * 1024;

    private static List<String> mineableBlocks = new ArrayList<>();
    private static List<String> configErrors = new ArrayList<>();

    private static Path configPath; // For user's mineable.json
    public static boolean isInitialized = false;

    static {
        try {
            configPath = FabricLoader.getInstance().getConfigDir().resolve(MODIFIABLE_BLOCK_CONFIG_FILE);
            loadOrCreateSettingsConfig(); // Load settings first
            loadOrCreateBlockConfig();    // Then blocks
            isInitialized = true;
        } catch(Exception e) {
            LOGGER.error("Failed to initialize config", e);
            configErrors.add("Critical initialization error: " + e.getMessage());
            // Fallback initialization
            if (zombieSettings == null) zombieSettings = new ZombieSettings();
            if (mineableBlocks.isEmpty() && configPath != null) { // Ensure configPath is not null
                try { mineableBlocks = new ArrayList<>(loadDefaultBlocks()); } catch (Exception ex) { LOGGER.error("Fallback to default blocks failed", ex); }
            } else if (mineableBlocks.isEmpty()) {
                mineableBlocks = new ArrayList<>(); // Absolute fallback
            }
        }
    }

    public static List<String> getMineableBlocksInternalList() {
        return mineableBlocks;
    }

    public static List<String> getConfigErrorsInternalList() {
        return configErrors;
    }

    public static class ZombieSettings {
        private double followRange = 50.0D;
        private double movementSpeed = 0.23D;
        private float miningSpeed = 1.0F;
        private int TargetRange = 15;
        private boolean BabyZombiesEnabled = false;
        private boolean TargetBlockParticlesEnabled = false;
        private boolean TargetBlockPathingParticlesEnabled = false;
        private int FarBlockSearchDistance = 12;
        private int DoorSearchDistance = 12;

        public ZombieSettings() {}

        public double getFollowRange() { return followRange; }
        public void setFollowRange(double followRange) { this.followRange = followRange; }
        public double getMovementSpeed() { return movementSpeed; }
        public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }
        public float getMiningSpeed() { return miningSpeed; }
        public void setMiningSpeed(float miningSpeed) { this.miningSpeed = miningSpeed; }
        public int getTargetRange() { return TargetRange; }
        public void setTargetRange(int TargetRange) { this.TargetRange = TargetRange; }
        public boolean getBabyZombiesEnabled() { return BabyZombiesEnabled; }
        public void setBabyZombiesEnabled(boolean BabyZombiesEnabled) { this.BabyZombiesEnabled = BabyZombiesEnabled; }
        public boolean getTargetBlockParticlesEnabled() { return TargetBlockParticlesEnabled; }
        public void setTargetBlockParticlesEnabled(boolean TargetBlockParticlesEnabled) { this.TargetBlockParticlesEnabled = TargetBlockParticlesEnabled; }
        public boolean getTargetBlockPathingParticlesEnabled() { return TargetBlockPathingParticlesEnabled; }
        public void setTargetBlockPathingParticlesEnabled(boolean TargetBlockPathingParticlesEnabled) { this.TargetBlockPathingParticlesEnabled = TargetBlockPathingParticlesEnabled; }
        public int getFarBlockSearchDistance() { return FarBlockSearchDistance; }
        public void setFarBlockSearchDistance(int FarBlockSearchDistance) { this.FarBlockSearchDistance = FarBlockSearchDistance; }
        public int getDoorSearchDistance() { return DoorSearchDistance; }
        public void setDoorSearchDistance(int DoorSearchDistance) { this.DoorSearchDistance = DoorSearchDistance; }
    }

    private static void loadOrCreateSettingsConfig() {
        Path settingsPath = FabricLoader.getInstance().getConfigDir().resolve(MODIFIABLE_SETTINGS_CONFIG_FILE);
        try {
            Path configDir = settingsPath.getParent();
            if (configDir != null && !Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            if (!Files.exists(settingsPath)) {
                createDefaultSettingsConfig(settingsPath);
            } else {
                loadSettingsFromFile(settingsPath);
            }
        } catch (Exception e) {
            String error = "Failed to load or create settings config: " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
            zombieSettings = new ZombieSettings();
        }
    }

    private static void createDefaultSettingsConfig(Path settingsPath) throws IOException {
        try (InputStream input = ModConfig.class.getResourceAsStream(INTERNAL_SETTINGS_CONFIG_FILE)) {
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    zombieSettings = GSON.fromJson(reader, ZombieSettings.class);
                    if (zombieSettings == null) {
                        LOGGER.warn("Default settings config resource was empty or malformed, using new ZombieSettings() defaults.");
                        zombieSettings = new ZombieSettings();
                    }
                } catch (JsonSyntaxException e) {
                    LOGGER.error("Default settings config file is malformed: " + e.getMessage() + ". Using new ZombieSettings() defaults.", e);
                    zombieSettings = new ZombieSettings();
                    configErrors.add("Default settings config resource (" + INTERNAL_SETTINGS_CONFIG_FILE + ") is malformed: " + e.getMessage());
                }
            } else {
                zombieSettings = new ZombieSettings();
                configErrors.add("Default settings config resource (" + INTERNAL_SETTINGS_CONFIG_FILE + ") not found - using default values");
            }
            saveSettingsConfig(settingsPath);
        }
    }

    private static void loadSettingsFromFile(Path settingsPath) throws IOException {
        String content = Files.readString(settingsPath, StandardCharsets.UTF_8);
        try {
            zombieSettings = GSON.fromJson(content, ZombieSettings.class);
            if (zombieSettings == null) {
                LOGGER.warn("Settings file was empty or corrupted, creating new default settings.");
                configErrors.add("Settings file " + settingsPath.getFileName() + " was empty or corrupted. Reverted to defaults.");
                createDefaultSettingsConfig(settingsPath);
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("Malformed JSON in settings file: " + e.getMessage() + ". Reverting to defaults.", e);
            configErrors.add("Malformed JSON in " + settingsPath.getFileName() + ": " + e.getMessage() + ". Reverted to defaults.");
            createDefaultSettingsConfig(settingsPath);
        }
    }

    private static void saveSettingsConfig(Path settingsPath) throws IOException {
        if (zombieSettings == null) {
            LOGGER.warn("Attempted to save null zombieSettings, initializing to defaults first.");
            zombieSettings = new ZombieSettings();
        }
        try {
            Path parentDir = settingsPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            if (Files.exists(settingsPath)) {
                Path backupPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".backup");
                Files.copy(settingsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            String jsonString = GSON.toJson(zombieSettings);
            Path tempFile = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp"); // Use resolveSibling
            Files.writeString(tempFile, jsonString, StandardCharsets.UTF_8);
            Files.move(tempFile, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            String error = "Failed to save settings config: " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
            throw e;
        }
    }

    public static void saveSettings() {
        Path settingsPath = FabricLoader.getInstance().getConfigDir().resolve(MODIFIABLE_SETTINGS_CONFIG_FILE);
        try {
            saveSettingsConfig(settingsPath);
        } catch (IOException e) {
            // Error already logged
        }
    }

    public static ZombieSettings getZombieSettings() {
        if (zombieSettings == null) {
            LOGGER.warn("ZombieSettings accessed while null. Attempting to load/create.");
            loadOrCreateSettingsConfig();
            if (zombieSettings == null) {
                LOGGER.error("CRITICAL: ZombieSettings is still null after attempting reload. Using emergency hardcoded defaults.");
                zombieSettings = new ZombieSettings();
            }
        }
        return zombieSettings;
    }

    private static void loadOrCreateBlockConfig() {
        // configErrors.clear(); // Original code did this. If other errors should persist, this needs refinement.
        // For strict "original code" adherence, it's kept.
        // If you want errors from settings load to persist, remove this line
        // or make error clearing more granular.
        // To keep settings errors and only clear block errors, you would:
        // List<String> nonBlockErrors = configErrors.stream().filter(e -> !e.contains("block")).collect(Collectors.toList());
        // configErrors.clear();
        // configErrors.addAll(nonBlockErrors);
        // For now, sticking to original code's apparent full clear:
        configErrors.clear();


        try {
            Path dir = configPath.getParent(); // configPath is for mineable.json
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (dir != null && !Files.isWritable(dir)) {
                throw new IOException("Config directory is not writable: " + dir);
            }

            if (!Files.exists(configPath)) {
                createDefaultBlockListFile();
            } else {
                if (!Files.isReadable(configPath)) {
                    throw new IOException("Config file is not readable: " + configPath);
                }
                if (Files.size(configPath) > MAX_FILE_SIZE) {
                    throw new IOException("Config file " + configPath.getFileName() + " exceeds maximum size limit of 1MB");
                }
                loadBlockConfigFromFile();
            }

            if (mineableBlocks.size() > MAX_BLOCKS) {
                String error = "Too many blocks in config (limit: " + MAX_BLOCKS + ")";
                configErrors.add(error);
                LOGGER.warn(error);
                mineableBlocks = mineableBlocks.subList(0, MAX_BLOCKS);
            }

            Set<String> uniqueBlocks = new HashSet<>(mineableBlocks);
            if (uniqueBlocks.size() < mineableBlocks.size()) {
                List<String> duplicates = mineableBlocks.stream()
                        .filter(block -> Collections.frequency(mineableBlocks, block) > 1)
                        .distinct()
                        .collect(Collectors.toList());
                String error = "Duplicate blocks found and removed from list: " + String.join(", ", duplicates);
                configErrors.add(error);
                LOGGER.warn(error);
                mineableBlocks = new ArrayList<>(uniqueBlocks);
            }

        } catch (Exception e) {
            String error = "Failed to load or create block config (" + configPath.getFileName() + "): " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
            // **THIS IS THE CORRECTED LINE**
            mineableBlocks = new ArrayList<>(loadDefaultBlocks()); // Fallback to jar defaults via the public method
        }
    }

    private static void createDefaultBlockListFile() throws IOException {
        mineableBlocks.clear(); // Start with an empty list for population
        JsonObject defaultConfigJsonOutput = new JsonObject(); // This will be saved to file
        JsonArray defaultValuesArray = new JsonArray();

        try (InputStream input = ModConfig.class.getResourceAsStream(INTERNAL_BLOCK_CONFIG_FILE)) {
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonObject parsedDefaults = GSON.fromJson(reader, JsonObject.class);
                    if (parsedDefaults != null && parsedDefaults.has("values") && parsedDefaults.get("values").isJsonArray()) {
                        JsonArray valuesFromJar = parsedDefaults.getAsJsonArray("values");
                        for (int i = 0; i < valuesFromJar.size(); i++) {
                            if (valuesFromJar.get(i).isJsonObject()) {
                                JsonObject blockObj = valuesFromJar.get(i).getAsJsonObject();
                                if (blockObj.has("id") && blockObj.get("id").isJsonPrimitive() && blockObj.get("id").getAsJsonPrimitive().isString()) {
                                    String id = blockObj.get("id").getAsString();
                                    mineableBlocks.add(id); // Populate in-memory list
                                    defaultValuesArray.add(blockObj); // Add to JSON array for saving
                                }
                            }
                        }
                    } else {
                        configErrors.add("Default block config resource ("+INTERNAL_BLOCK_CONFIG_FILE+") is malformed. Using empty list.");
                    }
                } catch (JsonSyntaxException e) {
                    configErrors.add("Default block config resource ("+INTERNAL_BLOCK_CONFIG_FILE+") is malformed: " + e.getMessage() + ". Using empty list.");
                }
            } else {
                configErrors.add("Default block config resource ("+INTERNAL_BLOCK_CONFIG_FILE+") not found - using empty config");
            }
        }
        defaultConfigJsonOutput.add("values", defaultValuesArray); // Use the array built from JAR content

        if (Files.exists(configPath)) {
            Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".backup");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(configPath, GSON.toJson(defaultConfigJsonOutput), StandardCharsets.UTF_8);
    }

    private static void loadBlockConfigFromFile() throws IOException {
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        JsonObject configJson;
        try {
            configJson = GSON.fromJson(content, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Malformed JSON in block config file: " + e.getMessage(), e);
        }

        mineableBlocks.clear();

        if (configJson == null) {
            throw new IOException("Block config file " + configPath.getFileName() + " is empty or corrupted (parsed to null)");
        }
        if (!configJson.has("values")) {
            throw new IOException("Invalid block config format in " + configPath.getFileName() + " - missing 'values' array");
        }
        JsonArray values;
        try {
            values = configJson.getAsJsonArray("values");
        } catch (ClassCastException e) {
            throw new IOException("'values' is not an array in block config (" + configPath.getFileName() + ")");
        }

        for (int i = 0; i < values.size(); i++) {
            try {
                if(!values.get(i).isJsonObject()){
                    configErrors.add("Block entry at index " + i + " in " + configPath.getFileName() + " is not an object.");
                    continue;
                }
                JsonObject blockObj = values.get(i).getAsJsonObject();
                if (!blockObj.has("id") || !blockObj.get("id").isJsonPrimitive() || !blockObj.get("id").getAsJsonPrimitive().isString()) {
                    configErrors.add("Block at index " + i + " in " + configPath.getFileName() + " is missing 'id' field or it's not a string.");
                    continue;
                }
                String id = blockObj.get("id").getAsString();

                if (id.startsWith("#")) {
                    mineableBlocks.add(id);
                } else {
                    try {
                        Identifier identifier = VersionHelper.CustomIdentifier(id);
                        if (Registries.BLOCK.containsId(identifier)) {
                            mineableBlocks.add(id);
                        } else {
                            configErrors.add("Block does not exist in game registry (during load of " + configPath.getFileName() + "): " + id);
                        }
                    } catch (InvalidIdentifierException e) {
                        configErrors.add("Invalid block ID format at index " + i + " (during load of " + configPath.getFileName() + "): " + id + " (" + e.getMessage() + ")");
                    }
                }
            } catch (Exception e) {
                configErrors.add("Error parsing block at index " + i + " in " + configPath.getFileName() + ": " + e.getMessage());
            }
        }
    }

    public static String validateBlockId(String blockId) {
        if (blockId == null) return "Block ID cannot be null";
        blockId = blockId.trim();
        if (blockId.isEmpty()) return "Block ID cannot be empty";
        if (blockId.length() > 100) return "Block ID is too long (max 100 characters)"; // Original limit
        if (blockId.contains(" ")) return "Block ID cannot contain spaces";

        if (blockId.startsWith("#")) {
            String[] parts = blockId.substring(1).split(":", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return "Invalid tag format. Expected: #namespace:path";
            }
            if (!parts[0].matches("[a-z0-9_.-]+")) return "Invalid tag namespace. Must only contain [a-z0-9_.-]";
            if (!parts[1].matches("[a-z0-9/._-]+")) return "Invalid tag path. Must only contain [a-z0-9/._-]";
            return null;
        }
        try {
            Identifier identifier = VersionHelper.CustomIdentifier(blockId);
            if (isInitialized && !Registries.BLOCK.containsId(identifier)) {
                return "Block does not exist in game registry: " + blockId;
            }
        } catch (InvalidIdentifierException e) {
            return "Invalid block identifier: " + e.getMessage();
        }
        return null;
    }

    public static void saveBlockConfig() {
        if (!isInitialized) {
            LOGGER.error("Attempted to save block config before initialization");
            return;
        }
        List<String> tempSaveErrors = new ArrayList<>();
        try {
            Path parentDir = configPath.getParent();
            if (parentDir != null && !Files.isWritable(parentDir)) {
                throw new IOException("Config directory is not writable for " + configPath.getFileName());
            }

            if (Files.exists(configPath)) {
                Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".backup");
                Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            JsonObject configOut = new JsonObject();
            JsonArray valuesOut = new JsonArray();
            List<String> blocksToActuallySave = new ArrayList<>();
            Set<String> seenInSave = new HashSet<>();

            for (String block : mineableBlocks) { // Iterate current in-memory list
                if (blocksToActuallySave.size() >= MAX_BLOCKS) {
                    tempSaveErrors.add("Max block limit ("+MAX_BLOCKS+") reached during save. Some blocks were not saved.");
                    break;
                }
                String validationError = validateBlockId(block);
                if (validationError == null) {
                    if (seenInSave.add(block)) {
                        blocksToActuallySave.add(block);
                    } else {
                        tempSaveErrors.add("Skipping duplicate block during save: " + block);
                    }
                } else {
                    String error = "Skipping invalid block ID during save: " + block + " (" + validationError + ")";
                    // LOGGER.warn(error); // Original didn't log this specifically here but added to configErrors in GUI save
                    tempSaveErrors.add(error);
                }
            }

            for(String blockIdToSave : blocksToActuallySave) { // Iterate the de-duplicated/validated list
                JsonObject blockObj = new JsonObject();
                blockObj.addProperty("id", blockIdToSave);
                valuesOut.add(blockObj);
            }

            configOut.add("values", valuesOut);
            String jsonString = GSON.toJson(configOut);

            if (jsonString.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE) {
                throw new IOException("Config file " + configPath.getFileName() + " would exceed maximum size limit of 1MB");
            }

            Path tempFile = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(tempFile, jsonString, StandardCharsets.UTF_8);
            Files.move(tempFile, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            if (!tempSaveErrors.isEmpty()) {
                LOGGER.warn("Some issues occurred during block config save: " + String.join("; ", tempSaveErrors));
                // Add these to the main configErrors list so GUI can see them if needed
                configErrors.addAll(tempSaveErrors);
            }

        } catch (IOException e) {
            String error = "Failed to save block config (" + configPath.getFileName() + "): " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
        }
    }

    public static Set<String> getMineableBlocks() {
        return new HashSet<>(mineableBlocks);
    }

    public static List<String> loadDefaultBlocks() {
        List<String> defaults = new ArrayList<>();
        try (InputStream input = ModConfig.class.getResourceAsStream(INTERNAL_BLOCK_CONFIG_FILE)) {
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonObject defaultConfig = GSON.fromJson(reader, JsonObject.class);
                    if (defaultConfig != null && defaultConfig.has("values") && defaultConfig.get("values").isJsonArray()) {
                        JsonArray values = defaultConfig.getAsJsonArray("values");
                        for (int i = 0; i < values.size(); i++) {
                            if (values.get(i).isJsonObject()) {
                                JsonObject blockObj = values.get(i).getAsJsonObject();
                                if (blockObj.has("id") && blockObj.get("id").isJsonPrimitive() && blockObj.get("id").getAsJsonPrimitive().isString()) {
                                    defaults.add(blockObj.get("id").getAsString());
                                }
                            }
                        }
                    }
                } catch (JsonSyntaxException e){
                    LOGGER.error("Failed to parse default block config resource: " + e.getMessage(), e);
                    configErrors.add("Failed to parse default block config resource ("+INTERNAL_BLOCK_CONFIG_FILE+"): " + e.getMessage());
                }
            } else {
                LOGGER.warn("Default block config resource ("+INTERNAL_BLOCK_CONFIG_FILE+") not found.");
                configErrors.add("Default block config resource ("+INTERNAL_BLOCK_CONFIG_FILE+") not found.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load default blocks from resources", e);
            configErrors.add("Failed to load default blocks ("+INTERNAL_BLOCK_CONFIG_FILE+"): " + e.getMessage());
        }
        return defaults;
    }
}