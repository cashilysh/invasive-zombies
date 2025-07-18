package invasivezombies.client;

import invasivezombies.config.ModConfig;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ModMenuClient implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("Invasive Zombies Client Configuration")
                            .append(Text.literal("   ---   Restart game for changes to take effect!")
                                    .formatted(Formatting.YELLOW).formatted(Formatting.ITALIC)));

            builder.setSavingRunnable(() -> {
                if (!ModConfig.getConfigErrorsInternalList().isEmpty()) {
                    ModConfig.LOGGER.warn("Saving ZombieSettings with existing general config errors: {}", String.join(", ", ModConfig.getConfigErrorsInternalList()));
                }
                ModConfig.saveSettings();
            });

            ConfigCategory generalCategory = builder.getOrCreateCategory(Text.literal("Blocks"));
            ConfigCategory zombieSettingsCategory = builder.getOrCreateCategory(Text.literal("Zombie"));
            ConfigCategory debugSettingsCategory = builder.getOrCreateCategory(Text.literal("Debug"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            List<Text> tooltipLines = new ArrayList<>();

            if (!ModConfig.isInitialized) {
                tooltipLines.add(Text.literal("⚠ Configuration system not initialized!")
                        .setStyle(Style.EMPTY.withColor(Formatting.DARK_RED)));
            }

            tooltipLines.add(Text.literal("Current block count: " + ModConfig.getMineableBlocks().size() + "/" + ModConfig.MAX_BLOCKS)
                    .setStyle(Style.EMPTY.withColor(
                            ModConfig.getMineableBlocks().size() >= ModConfig.MAX_BLOCKS ? Formatting.RED : Formatting.GREEN)));

            List<String> currentErrorsSnapshot = new ArrayList<>(ModConfig.getConfigErrorsInternalList());
            if (!currentErrorsSnapshot.isEmpty()) {
                tooltipLines.add(Text.literal(""));
                tooltipLines.add(Text.literal("Current Configuration Errors:")
                        .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                for (String error : currentErrorsSnapshot) {
                    tooltipLines.add(Text.literal("• " + error)
                            .setStyle(Style.EMPTY.withColor(Formatting.RED)));
                }
            }

            List<String> currentBlockListForGui = new ArrayList<>(ModConfig.getMineableBlocksInternalList());

            generalCategory.addEntry(entryBuilder.startStrList(
                            Text.literal("Mineable Blocks")
                                    .setStyle(Style.EMPTY.withColor(
                                            !ModConfig.isInitialized || !currentErrorsSnapshot.isEmpty() ? Formatting.RED : Formatting.WHITE)),
                            currentBlockListForGui)
                    .setDefaultValue(ModConfig.loadDefaultBlocks())
                    .setSaveConsumer(newValue -> {
                        List<String> modConfigMineableBlocks = ModConfig.getMineableBlocksInternalList();
                        List<String> modConfigConfigErrors = ModConfig.getConfigErrorsInternalList();
                        if (!newValue.equals(currentBlockListForGui)) {
                            modConfigMineableBlocks.clear();
                            modConfigConfigErrors.clear();

                            Set<String> seen = new HashSet<>();
                            for (String block : newValue) {
                                if (modConfigMineableBlocks.size() >= ModConfig.MAX_BLOCKS) {
                                    modConfigConfigErrors.add("Max block limit (" + ModConfig.MAX_BLOCKS + ") reached. Some entries not added.");
                                    break;
                                }
                                if (!seen.add(block)) {
                                    modConfigConfigErrors.add("Duplicate block removed: " + block);
                                    continue;
                                }
                                String validationError = ModConfig.validateBlockId(block);
                                if (validationError == null) {
                                    modConfigMineableBlocks.add(block);
                                } else {
                                    modConfigConfigErrors.add("Invalid block ID: " + block + " (" + validationError + ")");
                                }
                            }
                            ModConfig.saveBlockConfig();
                        }
                    })
                    .setErrorSupplier(value -> {
                        if (value.isEmpty() && ModConfig.getMineableBlocks().isEmpty()) return Optional.empty();
                        if (value.size() > ModConfig.MAX_BLOCKS) {
                            return Optional.of(Text.literal("Too many blocks. Limit: " + ModConfig.MAX_BLOCKS).formatted(Formatting.RED));
                        }
                        Set<String> seenInGui = new HashSet<>();
                        for (String entry : value) {
                            if (entry.isEmpty() && value.size() > 1 && value.indexOf(entry) != value.size()-1) {
                                return Optional.of(Text.literal("Empty entries before the last are invalid.").formatted(Formatting.RED));
                            }
                            if (!entry.isEmpty() && !seenInGui.add(entry)) {
                                return Optional.of(Text.literal("Duplicate entry: " + entry).formatted(Formatting.GOLD));
                            }
                            if (!entry.isEmpty()) {
                                String validationError = ModConfig.validateBlockId(entry);
                                if (validationError != null) {
                                    if (validationError.startsWith("Block does not exist")) { // More specific error
                                        return Optional.of(Text.literal(validationError).formatted(Formatting.RED));
                                    }
                                    return Optional.of(Text.literal("Invalid format: " + entry + " ("+validationError+")").formatted(Formatting.RED));
                                }
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(tooltipLines.toArray(new Text[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .build()
            );

            ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();
            ModConfig.ZombieSettings defaults = new ModConfig.ZombieSettings();

            zombieSettingsCategory.addEntry(
                    entryBuilder.startDoubleField(Text.literal("Follow Range"), settings.getFollowRange())
                            .setDefaultValue(defaults.getFollowRange()).setMin(5.0D).setMax(200.0D)
                            .setSaveConsumer(settings::setFollowRange)
                            .setTooltip(Text.literal("How far zombies can detect, follow players and keep their target locked once seen (vanilla default: 35)"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startDoubleField(Text.literal("Movement Speed"), settings.getMovementSpeed())
                            .setDefaultValue(defaults.getMovementSpeed()).setMin(0.1D).setMax(1.0D)
                            .setSaveConsumer(settings::setMovementSpeed)
                            .setTooltip(Text.literal("How fast zombies can move (vanilla default: 0.23)"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startFloatField(Text.literal("Mining Speed Multiplier"), settings.getMiningSpeed())
                            .setDefaultValue(defaults.getMiningSpeed()).setMin(0.1F).setMax(50.0F)
                            .setSaveConsumer(settings::setMiningSpeed)
                            .setTooltip(Text.literal("How quickly zombies can break blocks"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startIntField(Text.literal("Zombie Lock-Target Range"), settings.getTargetRange())
                            .setDefaultValue(defaults.getTargetRange()).setMin(1).setMax(100)
                            .setSaveConsumer(settings::setTargetRange)
                            .setTooltip(Text.literal("In what radius zombies will keep their target, even without line-of-sight (X-Ray vision)"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Text.literal("Enable Baby Zombies Block Breaking"), settings.getBabyZombiesEnabled())
                            .setDefaultValue(defaults.getBabyZombiesEnabled())
                            .setSaveConsumer(settings::setBabyZombiesEnabled)
                            .setTooltip(Text.literal("Toggle whether baby zombies should be able to break blocks"))
                            .build());

            debugSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Text.literal("Enable TargetBlock Particles"), settings.getTargetBlockParticlesEnabled())
                            .setDefaultValue(defaults.getTargetBlockParticlesEnabled())
                            .setSaveConsumer(settings::setTargetBlockParticlesEnabled)
                            .setTooltip(Text.literal("Toggle green particle effects for selected target blocks, used for debugging"))
                            .build());

            debugSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Text.literal("Enable TargetBlock Pathing Visibility"), settings.getTargetBlockPathingParticlesEnabled())
                            .setDefaultValue(defaults.getTargetBlockPathingParticlesEnabled())
                            .setSaveConsumer(settings::setTargetBlockPathingParticlesEnabled)
                            .setTooltip(Text.literal("Show the path to the target block, used for debugging"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startIntSlider(Text.literal("Far Block Search Distance"),
                                    settings.getFarBlockSearchDistance(), 6, 24)
                            .setDefaultValue(defaults.getFarBlockSearchDistance())
                            .setSaveConsumer(value -> {
                                int adjustedValue = (value / 6) * 6;
                                if (adjustedValue < 6) adjustedValue = 6;
                                if (adjustedValue > 24) adjustedValue = 24;
                                settings.setFarBlockSearchDistance(adjustedValue);
                            })
                            .setTooltip(Text.literal("Square search distance for breakable blocks when no near blocks are found. Blocks behind the zombies facing direction are culled."))
                            .setTextGetter(value -> Text.literal( (value / 6 * 6 * 2) + "x" + (value / 6 * 6 * 2) + " blocks"))
                            .setMin(6).setMax(24)
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startIntSlider(Text.literal("Door Search Distance"),
                                    settings.getDoorSearchDistance(), 6, 24)
                            .setDefaultValue(defaults.getDoorSearchDistance())
                            .setSaveConsumer(value -> {
                                int adjustedValue = (value / 6) * 6;
                                if (adjustedValue < 6) adjustedValue = 6;
                                if (adjustedValue > 24) adjustedValue = 24;
                                settings.setDoorSearchDistance(adjustedValue);
                            })
                            .setTooltip(Text.literal("Square search distance for breakable doors around the zombie. NO blocks behind are culled."))
                            .setTextGetter(value -> Text.literal( (value / 6 * 6 * 2) + "x" + (value / 6 * 6 * 2) + " blocks"))
                            .setMin(6).setMax(24)
                            .build());

            return builder.build();
        };
    }
}