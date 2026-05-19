package invasivezombies.client;

import invasivezombies.config.ModConfig;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
                    .setTitle(Component.literal("Invasive Zombies Client Configuration")
                            .append(Component.literal("   ---   Restart game for changes to take effect!")
                                    .withStyle(ChatFormatting.YELLOW).withStyle(ChatFormatting.ITALIC)));

            builder.setSavingRunnable(() -> {
                if (!ModConfig.getConfigErrorsInternalList().isEmpty()) {
                    ModConfig.LOGGER.warn("Saving ZombieSettings with existing general config errors: {}", String.join(", ", ModConfig.getConfigErrorsInternalList()));
                }
                ModConfig.saveSettings();
            });

            ConfigCategory generalCategory = builder.getOrCreateCategory(Component.literal("Blocks"));
            ConfigCategory zombieSettingsCategory = builder.getOrCreateCategory(Component.literal("Zombie"));
            ConfigCategory debugSettingsCategory = builder.getOrCreateCategory(Component.literal("Debug"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            List<Component> tooltipLines = new ArrayList<>();

            if (!ModConfig.isInitialized) {
                tooltipLines.add(Component.literal("⚠ Configuration system not initialized!")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED)));
            }

            tooltipLines.add(Component.literal("Current block count: " + ModConfig.getMineableBlocks().size() + "/" + ModConfig.MAX_BLOCKS)
                    .setStyle(Style.EMPTY.withColor(
                            ModConfig.getMineableBlocks().size() >= ModConfig.MAX_BLOCKS ? ChatFormatting.RED : ChatFormatting.GREEN)));

            List<String> currentErrorsSnapshot = new ArrayList<>(ModConfig.getConfigErrorsInternalList());
            if (!currentErrorsSnapshot.isEmpty()) {
                tooltipLines.add(Component.literal(""));
                tooltipLines.add(Component.literal("Current Configuration Errors:")
                        .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                for (String error : currentErrorsSnapshot) {
                    tooltipLines.add(Component.literal("• " + error)
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                }
            }

            List<String> currentBlockListForGui = new ArrayList<>(ModConfig.getMineableBlocksInternalList());

            generalCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("Mineable Blocks")
                                    .setStyle(Style.EMPTY.withColor(
                                            !ModConfig.isInitialized || !currentErrorsSnapshot.isEmpty() ? ChatFormatting.RED : ChatFormatting.WHITE)),
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
                            return Optional.of(Component.literal("Too many blocks. Limit: " + ModConfig.MAX_BLOCKS).withStyle(ChatFormatting.RED));
                        }
                        Set<String> seenInGui = new HashSet<>();
                        for (String entry : value) {
                            if (entry.isEmpty() && value.size() > 1 && value.indexOf(entry) != value.size()-1) {
                                return Optional.of(Component.literal("Empty entries before the last are invalid.").withStyle(ChatFormatting.RED));
                            }
                            if (!entry.isEmpty() && !seenInGui.add(entry)) {
                                return Optional.of(Component.literal("Duplicate entry: " + entry).withStyle(ChatFormatting.GOLD));
                            }
                            if (!entry.isEmpty()) {
                                String validationError = ModConfig.validateBlockId(entry);
                                if (validationError != null) {
                                    if (validationError.startsWith("Block does not exist")) { // More specific error
                                        return Optional.of(Component.literal(validationError).withStyle(ChatFormatting.RED));
                                    }
                                    return Optional.of(Component.literal("Invalid format: " + entry + " ("+validationError+")").withStyle(ChatFormatting.RED));
                                }
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(tooltipLines.toArray(new Component[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .build()
            );

            ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();
            ModConfig.ZombieSettings defaults = new ModConfig.ZombieSettings();

            zombieSettingsCategory.addEntry(
                    entryBuilder.startDoubleField(Component.literal("Follow Range"), settings.getFollowRange())
                            .setDefaultValue(defaults.getFollowRange()).setMin(5.0D).setMax(200.0D)
                            .setSaveConsumer(settings::setFollowRange)
                            .setTooltip(Component.literal("How far zombies can detect, follow players and keep their target locked once seen (vanilla default: 35)"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startDoubleField(Component.literal("Movement Speed"), settings.getMovementSpeed())
                            .setDefaultValue(defaults.getMovementSpeed()).setMin(0.1D).setMax(1.0D)
                            .setSaveConsumer(settings::setMovementSpeed)
                            .setTooltip(Component.literal("How fast zombies can move (vanilla default: 0.23)"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startFloatField(Component.literal("Mining Speed Multiplier"), settings.getMiningSpeed())
                            .setDefaultValue(defaults.getMiningSpeed()).setMin(0.1F).setMax(50.0F)
                            .setSaveConsumer(settings::setMiningSpeed)
                            .setTooltip(Component.literal("How quickly zombies can break blocks"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startIntField(Component.literal("Zombie Lock-Target Range"), settings.getTargetRange())
                            .setDefaultValue(defaults.getTargetRange()).setMin(1).setMax(100)
                            .setSaveConsumer(settings::setTargetRange)
                            .setTooltip(Component.literal("In what radius zombies will keep their target, even without line-of-sight (X-Ray vision)"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Enable Baby Zombies Block Breaking"), settings.getBabyZombiesEnabled())
                            .setDefaultValue(defaults.getBabyZombiesEnabled())
                            .setSaveConsumer(settings::setBabyZombiesEnabled)
                            .setTooltip(Component.literal("Toggle whether baby zombies should be able to break blocks"))
                            .build());

            debugSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Enable TargetBlock Particles"), settings.getTargetBlockParticlesEnabled())
                            .setDefaultValue(defaults.getTargetBlockParticlesEnabled())
                            .setSaveConsumer(settings::setTargetBlockParticlesEnabled)
                            .setTooltip(Component.literal("Toggle green particle effects for selected target blocks, used for debugging"))
                            .build());

            debugSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Enable TargetBlock Pathing Visibility"), settings.getTargetBlockPathingParticlesEnabled())
                            .setDefaultValue(defaults.getTargetBlockPathingParticlesEnabled())
                            .setSaveConsumer(settings::setTargetBlockPathingParticlesEnabled)
                            .setTooltip(Component.literal("Show the path to the target block, used for debugging"))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startIntSlider(Component.literal("Far Block Search Distance"),
                                    settings.getFarBlockSearchDistance(), 6, 24)
                            .setDefaultValue(defaults.getFarBlockSearchDistance())
                            .setSaveConsumer(value -> {
                                int adjustedValue = (value / 6) * 6;
                                if (adjustedValue < 6) adjustedValue = 6;
                                if (adjustedValue > 24) adjustedValue = 24;
                                settings.setFarBlockSearchDistance(adjustedValue);
                            })
                            .setTooltip(Component.literal("Square search distance for breakable blocks when no near blocks are found. Blocks behind the zombies facing direction are culled."))
                            .setTextGetter(value -> Component.literal( (value / 6 * 6 * 2) + "x" + (value / 6 * 6 * 2) + " blocks"))
                            .setMin(6).setMax(24)
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startIntSlider(Component.literal("Door Search Distance"),
                                    settings.getDoorSearchDistance(), 6, 24)
                            .setDefaultValue(defaults.getDoorSearchDistance())
                            .setSaveConsumer(value -> {
                                int adjustedValue = (value / 6) * 6;
                                if (adjustedValue < 6) adjustedValue = 6;
                                if (adjustedValue > 24) adjustedValue = 24;
                                settings.setDoorSearchDistance(adjustedValue);
                            })
                            .setTooltip(Component.literal("Square search distance for breakable doors around the zombie. NO blocks behind are culled."))
                            .setTextGetter(value -> Component.literal( (value / 6 * 6 * 2) + "x" + (value / 6 * 6 * 2) + " blocks"))
                            .setMin(6).setMax(24)
                            .build());

            return builder.build();
        };
    }
}