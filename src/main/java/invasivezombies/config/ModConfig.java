package invasivezombies.config;

import invasivezombies.VersionHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.util.InvalidIdentifierException;
import java.util. * ;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io. * ;
import java.nio.charset.StandardCharsets;
import java.nio.file. * ;
import java.util.stream.Collectors;

public class ModConfig implements ModMenuApi {
  private static final Logger LOGGER = LoggerFactory.getLogger("invasivezombies");

  private static final String MODIFIABLE_BLOCK_CONFIG_FILE = "invasivezombies/mineable.json";
  private static final String INTERNAL_BLOCK_CONFIG_FILE = "/data/mineable.json";

  private static final String MODIFIABLE_SETTINGS_CONFIG_FILE = "invasivezombies/settings.json";
  private static final String INTERNAL_SETTINGS_CONFIG_FILE = "/data/settings.json";

  private static ZombieSettings zombieSettings;

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final int MAX_BLOCKS = 1000;
  private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB limit

  private static List < String > mineableBlocks = new ArrayList < >();
  private static final List < String > configErrors = new ArrayList < >();
  private static Path configPath;
  private static boolean isInitialized = false;

  static {
    try {
      configPath = FabricLoader.getInstance().getConfigDir().resolve(MODIFIABLE_BLOCK_CONFIG_FILE);
      loadOrCreateBlockConfig();
      loadOrCreateSettingsConfig();
      isInitialized = true;
    } catch(Exception e) {
      LOGGER.error("Failed to initialize config", e);
      configErrors.add("Critical initialization error: " + e.getMessage());
    }
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

    // Getters and setters
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
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        if (!Files.exists(settingsPath)) {
            createDefaultSettingsConfig(settingsPath);
        }

        loadSettingsFromFile(settingsPath);
    } catch (Exception e) {
        String error = "Failed to load or create settings config: " + e.getMessage();
        LOGGER.error(error, e);
        configErrors.add(error);
        zombieSettings = new ZombieSettings(); // Use defaults if loading fails
    }
}

private static void createDefaultSettingsConfig(Path settingsPath) throws IOException {
    try (InputStream input = ModConfig.class.getResourceAsStream(INTERNAL_SETTINGS_CONFIG_FILE)) {
        if (input != null) {
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                zombieSettings = GSON.fromJson(reader, ZombieSettings.class);
            } catch (JsonSyntaxException e) {
                throw new IOException("Default settings config file is malformed: " + e.getMessage());
            }
        } else {
            zombieSettings = new ZombieSettings();
            configErrors.add("Default settings config resource not found - using default values");
        }
        
        saveSettingsConfig(settingsPath);
    }
}

private static void loadSettingsFromFile(Path settingsPath) throws IOException {
    String content = Files.readString(settingsPath, StandardCharsets.UTF_8);
    try {
        zombieSettings = GSON.fromJson(content, ZombieSettings.class);
        if (zombieSettings == null) {
            throw new IOException("Settings file is empty or corrupted");
        }
    } catch (JsonSyntaxException e) {
        throw new IOException("Malformed JSON in settings file: " + e.getMessage());
    }
}

private static void saveSettingsConfig(Path settingsPath) throws IOException {
    if (zombieSettings == null) {
        zombieSettings = new ZombieSettings();
    }
    
    try {
        if (Files.exists(settingsPath)) {
            Path backupPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".backup");
            Files.copy(settingsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        String jsonString = GSON.toJson(zombieSettings);
        Path tempFile = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp");
        Files.writeString(tempFile, jsonString, StandardCharsets.UTF_8);
        Files.move(tempFile, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
        String error = "Failed to save settings config: " + e.getMessage();
        LOGGER.error(error, e);
        configErrors.add(error);
    }
}

public static void saveSettings() {
    Path settingsPath = FabricLoader.getInstance().getConfigDir().resolve(MODIFIABLE_SETTINGS_CONFIG_FILE);
    try {
        saveSettingsConfig(settingsPath);
    } catch (IOException e) {
        LOGGER.error("Failed to save settings", e);
    }
}

// Add getter for zombie settings
public static ZombieSettings getZombieSettings() {
    if (zombieSettings == null) {
        zombieSettings = new ZombieSettings();
    }
    return zombieSettings;
}

//---------------------------------------------------------------------------BLOCK CONFIG-----------------------------------------------------------------------


    private static void loadOrCreateBlockConfig() {
        configErrors.clear();
        try {
            Path configDir = configPath.getParent();
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            if (!Files.isWritable(configDir)) {
                throw new IOException("Config directory is not writable: " + configDir);
            }

            if (!Files.exists(configPath)) {
                createDefaultConfig();
            } else {
                if (!Files.isReadable(configPath)) {
                    throw new IOException("Config file is not readable: " + configPath);
                }
                if (Files.size(configPath) > MAX_FILE_SIZE) {
                    throw new IOException("Config file exceeds maximum size limit of 1MB");
                }
            }

            loadConfigFromFile();

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
                String error = "Duplicate blocks found: " + String.join(", ", duplicates);
                configErrors.add(error);
                LOGGER.warn(error);
                mineableBlocks = new ArrayList<>(uniqueBlocks);
            }

        } catch (Exception e) {
            String error = "Failed to load or create config: " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
            mineableBlocks = new ArrayList<>();
        }
    }

    private static void createDefaultConfig() throws IOException {
        try (InputStream input = ModConfig.class.getResourceAsStream(INTERNAL_BLOCK_CONFIG_FILE)) {
            JsonObject defaultConfig;
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    defaultConfig = GSON.fromJson(reader, JsonObject.class);
                } catch (JsonSyntaxException e) {
                    throw new IOException("Default config file is malformed: " + e.getMessage());
                }
            } else {
                defaultConfig = new JsonObject();
                defaultConfig.add("values", new JsonArray());
                configErrors.add("Default config resource not found - using empty config");
            }
            
            if (Files.exists(configPath)) {
                Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".backup");
                Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.writeString(configPath, GSON.toJson(defaultConfig), StandardCharsets.UTF_8);
        }
    }

private static void loadConfigFromFile() throws IOException {
    String content = Files.readString(configPath, StandardCharsets.UTF_8);
    JsonObject config;
    try {
        config = GSON.fromJson(content, JsonObject.class);
    } catch (JsonSyntaxException e) {
        throw new IOException("Malformed JSON in config file: " + e.getMessage());
    }
    mineableBlocks.clear();
    
    if (config == null) {
        throw new IOException("Config file is empty or corrupted");
    }
    
    if (!config.has("values")) {
        throw new IOException("Invalid config format - missing 'values' array");
    }
    JsonArray values;
    try {
        values = config.getAsJsonArray("values");
    } catch (ClassCastException e) {
        throw new IOException("'values' is not an array in config");
    }
    for (int i = 0; i < values.size(); i++) {
        try {
            JsonObject blockObj = values.get(i).getAsJsonObject();
            if (!blockObj.has("id")) {
                configErrors.add("Block at index " + i + " is missing 'id' field");
                continue;
            }
            String id = blockObj.get("id").getAsString();
            
            if (id.startsWith("#")) {
                // Validate the tag exists in the registry
                try {
                    // We can't easily check if a tag exists in the registry at load time
                    // So we just add it and assume it's valid
                    mineableBlocks.add(id);
                } catch (InvalidIdentifierException e) {
                    configErrors.add("Invalid tag ID at index " + i + ": " + id + " (" + e.getMessage() + ")");
                }
            } else {
                // Handle regular block IDs
                try {
                    Identifier identifier = VersionHelper.CustomIdentifier(id);
                    if (Registries.BLOCK.containsId(identifier)) {
                        mineableBlocks.add(id);
                    } else {
                        configErrors.add("Block does not exist in game registry: " + id);
                    }
                } catch (InvalidIdentifierException e) {
                    configErrors.add("Invalid block ID at index " + i + ": " + id + " (" + e.getMessage() + ")");
                }
            }
        } catch (Exception e) {
            configErrors.add("Error parsing block at index " + i + ": " + e.getMessage());
        }
    }
}

    public static String validateBlockId(String blockId) {
    if (blockId == null) {
        return "Block ID cannot be null";
    }

    blockId = blockId.trim();

    if (blockId.isEmpty()) {
        return "Block ID cannot be empty";
    }

    if (blockId.length() > 100) {
        return "Block ID is too long (max 100 characters)";
    }

    if (blockId.contains(" ")) {
        return "Block ID cannot contain spaces";
    }

    // Handle tag entries
    if (blockId.startsWith("#")) {
        String[] parts = blockId.substring(1).split(":", 2); // Remove '#' and split into namespace:path
        if (parts.length != 2) {
            return "Invalid tag format. Expected: #namespace:path";
        }

        String namespace = parts[0];
        String path = parts[1];

        // Validate namespace and path
        if (!namespace.matches("[a-z0-9_.-]+")) {
            return "Invalid tag namespace. Must only contain [a-z0-9_.-]";
        }
        if (!path.matches("[a-z0-9/._-]+")) {
            return "Invalid tag path. Must only contain [a-z0-9/._-]";
        }

        return null; // Tag is valid
    }

    // Handle regular block IDs
    try {
        Identifier identifier = VersionHelper.CustomIdentifier(blockId);
        if (!Registries.BLOCK.containsId(identifier)) {
            return "Block does not exist in game registry: " + blockId;
        }
    } catch (InvalidIdentifierException e) {
        return "Invalid block identifier: " + e.getMessage();
    }

    return null; // Block ID is valid
}

    public static void saveBlockConfig() {
        if (!isInitialized) {
            LOGGER.error("Attempted to save config before initialization");
            return;
        }

        configErrors.clear();
        try {
            if (Files.exists(configPath)) {
                Path backupPath = configPath.resolveSibling(configPath.getFileName() + ".backup");
                Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            JsonObject config = new JsonObject();
            JsonArray values = new JsonArray();
            
            if (!Files.isWritable(configPath.getParent())) {
                throw new IOException("Config directory is not writable");
            }
            
            for (String block : mineableBlocks) {
                String validationError = validateBlockId(block);
                if (validationError == null) {
                    JsonObject blockObj = new JsonObject();
                    blockObj.addProperty("id", block);
                    values.add(blockObj);
                } else {
                    String error = "Skipping invalid block ID during save: " + block + " (" + validationError + ")";
                    LOGGER.warn(error);
                    configErrors.add(error);
                }
            }
            
            config.add("values", values);
            String jsonString = GSON.toJson(config);
            
            if (jsonString.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE) {
                throw new IOException("Config file would exceed maximum size limit of 1MB");
            }
            
            Path tempFile = configPath.resolveSibling(configPath.getFileName() + ".tmp");
            Files.writeString(tempFile, jsonString, StandardCharsets.UTF_8);
            Files.move(tempFile, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
						
            
        } catch (IOException e) {
            String error = "Failed to save config: " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
        }
    }

    public static Set<String> getMineableBlocks() {
        return new HashSet<>(mineableBlocks);
    }
	
	    private static List<String> loadDefaultBlocks() {
        List<String> defaults = new ArrayList<>();
        try (InputStream input = ModConfig.class.getResourceAsStream(INTERNAL_BLOCK_CONFIG_FILE)) {
            if (input != null) {
                try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonObject defaultConfig = GSON.fromJson(reader, JsonObject.class);
                    JsonArray values = defaultConfig.getAsJsonArray("values");
                    for (int i = 0; i < values.size(); i++) {
                        JsonObject blockObj = values.get(i).getAsJsonObject();
                        if (blockObj.has("id")) {
                            defaults.add(blockObj.get("id").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load default config from resources", e);
            configErrors.add("Failed to load defaults: " + e.getMessage());
        }
        return defaults;
    }


@Override
public ConfigScreenFactory<?> getModConfigScreenFactory() {

    return parent -> {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
        .setTitle(Text.literal("Invasive Zombies Configuration")
            .append(Text.literal("  ---   Restart game for changes to take effect!")
                .formatted(Formatting.YELLOW).formatted(Formatting.ITALIC)));

        // Create the general category for block configuration
        ConfigCategory generalCategory = builder.getOrCreateCategory(Text.literal("Blocks"));
        
        // Create a separate category for zombie settings
        ConfigCategory zombieSettingsCategory = builder.getOrCreateCategory(Text.literal("Zombie"));

        // Create a separate category for debug settings
        ConfigCategory debugSettingsCategory = builder.getOrCreateCategory(Text.literal("Debug"));
        
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Add the existing block configuration
        List<Text> tooltipLines = new ArrayList<>();
        
        if (!isInitialized) {
            tooltipLines.add(Text.literal("⚠ Configuration system not initialized!")
                .setStyle(Style.EMPTY.withColor(Formatting.DARK_RED)));
        }

        tooltipLines.add(Text.literal("Current block count: " + mineableBlocks.size() + "/" + MAX_BLOCKS)
            .setStyle(Style.EMPTY.withColor(
                mineableBlocks.size() >= MAX_BLOCKS ? Formatting.RED : Formatting.GREEN)));

        if (!configErrors.isEmpty()) {
            tooltipLines.add(Text.literal("")); 
            tooltipLines.add(Text.literal("Current Configuration Errors:")
                .setStyle(Style.EMPTY.withColor(Formatting.RED)));
            for (String error : configErrors) {
                tooltipLines.add(Text.literal("• " + error)
                    .setStyle(Style.EMPTY.withColor(Formatting.RED)));
            }
        }

        List<String> currentList = new ArrayList<>(mineableBlocks);

        generalCategory.addEntry(entryBuilder.startStrList(
                Text.literal("Mineable Blocks")
                    .setStyle(Style.EMPTY.withColor(
                        !isInitialized || !configErrors.isEmpty() ? Formatting.RED : Formatting.WHITE)),
                currentList)
            .setDefaultValue(loadDefaultBlocks())
            .setSaveConsumer(newValue -> {
                if (!newValue.equals(currentList)) {
                    mineableBlocks.clear();
                    configErrors.clear();
                    
                    Set<String> seen = new HashSet<>();
                    for (String block : newValue) {
                        if (!seen.add(block)) {
                            configErrors.add("Duplicate block removed: " + block);
                            continue;
                        }
                        
                        String validationError = validateBlockId(block);
                        if (validationError == null) {
                            mineableBlocks.add(block);
                        } else {
                            configErrors.add("Invalid block ID: " + block + " (" + validationError + ")");
                        }
                    }
                    saveBlockConfig();
                }
            })
            .setErrorSupplier(value -> {
                if (value.isEmpty()) return Optional.empty();
                
                // Check every entry for validity
                for (String entry : value) {
                    if (entry.isEmpty()) continue;
                    
                    // Then do additional validation for tags and blocks
                    if (entry.startsWith("#")) {
                        String tagId = entry.substring(1); // Remove the # prefix
                        try {
                            // Basic format validation
                            if (!tagId.contains(":")) {
                                return Optional.of(Text.literal("Invalid tag format (missing namespace): " + entry)
                                    .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                            }

                            
                        } catch (Exception e) {
                            return Optional.of(Text.literal("Invalid block tag format: " + entry)
                                .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                        }
                    } else {
                        // For blocks, check if the block exists in the registry
                        try {
                            Identifier blockId = VersionHelper.CustomIdentifier(entry);
                            if (!Registries.BLOCK.containsId(blockId)) {
                                return Optional.of(Text.literal("Block does not exist: " + entry)
                                    .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                            }
                        } catch (Exception e) {
                            return Optional.of(Text.literal("Invalid block identifier: " + entry)
                                .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                        }
                    }
                }
                
                // Check for duplicates
                Set<String> seen = new HashSet<>();
                for (String block : value) {
                    if (!block.isEmpty() && !seen.add(block)) {
                        return Optional.of(Text.literal("Duplicate entry: " + block)
                            .setStyle(Style.EMPTY.withColor(Formatting.GOLD)));
                    }
                }
                
                return Optional.empty();
            })
            .setTooltip(tooltipLines.toArray(new Text[0]))
            .setInsertInFront(true)
			.setExpanded(true)  // Add this line to make it expanded by default
            .build()
        );

        // Add zombie settings entries
        ZombieSettings settings = getZombieSettings();
        ZombieSettings defaults = new ZombieSettings();

        // Follow Range
        zombieSettingsCategory.addEntry(
            entryBuilder.startDoubleField(Text.literal("Follow Range"), settings.getFollowRange())
                .setDefaultValue(defaults.getFollowRange())
                .setMin(5.0D)
                .setMax(200.0D)
                .setSaveConsumer(settings::setFollowRange)
                .setTooltip(Text.literal("How far zombies can detect, follow players and keep their target locked once seen (vanilla default: 35)"))
                .build()
        );

        // Movement Speed
        zombieSettingsCategory.addEntry(
            entryBuilder.startDoubleField(Text.literal("Movement Speed"), settings.getMovementSpeed())
                .setDefaultValue(defaults.getMovementSpeed())
                .setMin(0.1D)
                .setMax(1.0D)
                .setSaveConsumer(settings::setMovementSpeed)
                .setTooltip(Text.literal("How fast zombies can move (vanilla default: 0.23)"))
                .build()
        );

        // Mining Speed
        zombieSettingsCategory.addEntry(
            entryBuilder.startFloatField(Text.literal("Mining Speed Multiplier"), settings.getMiningSpeed())
                .setDefaultValue(defaults.getMiningSpeed())
                .setMin(0.1F)
                .setMax(50.0F)
                .setSaveConsumer(settings::setMiningSpeed)
                .setTooltip(Text.literal("How quickly zombies can break blocks"))
                .build()
        );
		
		// Target Range
        zombieSettingsCategory.addEntry(
            entryBuilder.startIntField(Text.literal("Zombie Lock-Target Range"), settings.getTargetRange())
                .setDefaultValue(defaults.getTargetRange())
                .setMin(1)
                .setMax(100)
                .setSaveConsumer(settings::setTargetRange)
                .setTooltip(Text.literal("In what radius zombies will keep their target, even without line-of-sight"))
                .build()
        );
		
		
		// Baby Zombies Toggle
		zombieSettingsCategory.addEntry(
		entryBuilder.startBooleanToggle(Text.literal("Enable Baby Zombies Block Breaking"), settings.getBabyZombiesEnabled())
        .setDefaultValue(defaults.getBabyZombiesEnabled())  // Set the default to ON or OFF
        .setSaveConsumer(settings::setBabyZombiesEnabled)  // Save the setting to the settings object
        .setTooltip(Text.literal("Toggle whether baby zombies should be able to break blocks"))
        .build()
		);

        //Target Block Green Particles Toggle
        debugSettingsCategory.addEntry(
                entryBuilder.startBooleanToggle(Text.literal("Enable TargetBlock Particles"), settings.getTargetBlockParticlesEnabled())
                        .setDefaultValue(defaults.getTargetBlockParticlesEnabled())  // Set the default to ON or OFF
                        .setSaveConsumer(settings::setTargetBlockParticlesEnabled)  // Save the setting to the settings object
                        .setTooltip(Text.literal("Toggle green particle effects for selected target blocks, used for debugging"))
                        .build()
        );

        //Target Block Pathing Visibility
        debugSettingsCategory.addEntry(
                entryBuilder.startBooleanToggle(Text.literal("Enable TargetBlock Pathing Visibility"), settings.getTargetBlockPathingParticlesEnabled())
                        .setDefaultValue(defaults.getTargetBlockPathingParticlesEnabled())  // Set the default to ON or OFF
                        .setSaveConsumer(settings::setTargetBlockPathingParticlesEnabled)  // Save the setting to the settings object
                        .setTooltip(Text.literal("Show the path to the target block, used for debugging"))
                        .build()
        );

        // Far Block Search Distance with 6-step increments
        zombieSettingsCategory.addEntry(
                entryBuilder.startIntSlider(Text.literal("Far Block Search Distance"),
                                settings.getFarBlockSearchDistance(),
                                6, 24)
                        .setDefaultValue(defaults.getFarBlockSearchDistance())
                        .setSaveConsumer(value -> {
                            // Ensure the value is always a multiple of 6
                            int adjustedValue = (value / 6) * 6;
                            settings.setFarBlockSearchDistance(adjustedValue);
                        })
                        .setTooltip(Text.literal("Square search distance for breakable blocks when no near blocks are found. Blocks behind the zombies facing direction are culled."))
                        .setTextGetter(value -> Text.literal( (value / 6 * 6 * 2) + "x" + (value / 6 * 6 * 2) + " blocks"))  // Display the adjusted value in multiples of 6
                        .setMin(6)  //shown as 12
                        .setMax(24) //shown as 48
                        .build()
        );



        // Door Search Distance
        zombieSettingsCategory.addEntry(
                entryBuilder.startIntSlider(Text.literal("Door Search Distance"),
                                settings.getDoorSearchDistance(),
                                6, 24)
                        .setDefaultValue(defaults.getDoorSearchDistance())
                        .setSaveConsumer(value -> {settings.setDoorSearchDistance(value);})
                        .setTooltip(Text.literal("Square search distance for breakable doors around the zombie. NO blocks behind are culled."))
                        .setTextGetter(value -> Text.literal( (value * 2) + "x" + (value * 2) + " blocks"))
                        .setMin(6)
                        .setMax(24)
                        .build()
        );


        builder.setSavingRunnable(() -> {
            if (!configErrors.isEmpty()) {
                LOGGER.warn("Saving config with errors: {}", String.join(", ", configErrors));
            }
            saveSettings();
        });

        return builder.build();
    };
}
}