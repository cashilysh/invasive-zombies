package invasivezombies.client;

import invasivezombies.config.ModConfig;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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

            ConfigCategory zombieSettingsCategory = builder.getOrCreateCategory(Component.literal("Zombie"));
            ConfigCategory standardBlocksCategory = builder.getOrCreateCategory(Component.literal("Standard Mineables"));
            ConfigCategory toolsCategory = builder.getOrCreateCategory(Component.literal("Tools"));
            ConfigCategory pickaxeBlocksCategory = builder.getOrCreateCategory(Component.literal("Pickaxe Mineables"));
            ConfigCategory axeBlocksCategory = builder.getOrCreateCategory(Component.literal("Axe Mineables"));
            ConfigCategory shovelBlocksCategory = builder.getOrCreateCategory(Component.literal("Shovel Mineables"));
            ConfigCategory alwaysBlocksCategory = builder.getOrCreateCategory(Component.literal("Always Mineables"));
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
            List<String> currentAlwaysBlockListForGui = new ArrayList<>(ModConfig.getAlwaysMineableBlocksInternalList());
            List<String> currentPickaxeBlockListForGui = new ArrayList<>(ModConfig.getPickaxeMineableBlocksInternalList());
            List<String> currentAxeBlockListForGui = new ArrayList<>(ModConfig.getAxeMineableBlocksInternalList());
            List<String> currentShovelBlockListForGui = new ArrayList<>(ModConfig.getShovelMineableBlocksInternalList());

            ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();
            ModConfig.ZombieSettings defaults = new ModConfig.ZombieSettings();

            List<Component> mineableBlocksTooltip = new ArrayList<>(tooltipLines);

            BooleanListEntry requireToolsEntry = entryBuilder.startBooleanToggle(Component.literal("Require Tools to Break Blocks"), settings.getBreakBlocksWithToolsOnly())
                    .setDefaultValue(defaults.getBreakBlocksWithToolsOnly())
                    .setSaveConsumer(settings::setBreakBlocksWithToolsOnly)
                    .setTooltip(Component.literal("Zombies need appropriate tools to break blocks.\nDisables the 'Standard Mineables' list."))
                    .build();

            standardBlocksCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("Standard Mineable Blocks")
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
                            if (entry.isEmpty() && value.size() > 1 && value.indexOf(entry) != value.size() - 1) {
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
                                    return Optional.of(Component.literal("Invalid format: " + entry + " (" + validationError + ")").withStyle(ChatFormatting.RED));
                                }
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(mineableBlocksTooltip.toArray(new Component[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .setRequirement(() -> !requireToolsEntry.getValue())
                    .build()
            );

            List<Component> alwaysBlocksTooltip = new ArrayList<>(tooltipLines);
            alwaysBlocksTooltip.add(Component.literal("Blocks in this list can ALWAYS be broken by zombies, regardless of tool requirements."));

            alwaysBlocksCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("Always Mineable Blocks")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)),
                            currentAlwaysBlockListForGui)
                    .setDefaultValue(ModConfig.loadDefaultAlwaysBlocks())
                    .setSaveConsumer(newValue -> {
                        List<String> modConfigAlwaysBlocks = ModConfig.getAlwaysMineableBlocksInternalList();
                        List<String> modConfigConfigErrors = ModConfig.getConfigErrorsInternalList(); // Share error list
                        if (!newValue.equals(currentAlwaysBlockListForGui)) {
                            modConfigAlwaysBlocks.clear();
                            // Don't clear errors here as we might have errors from the other list? 
                            // Actually existing logic clears errors. Let's stick to that pattern for now or better, don't clear if other list failed?
                            // But keeping it simple: existing pattern.
                            
                            Set<String> seen = new HashSet<>();
                            for (String block : newValue) {
                                if (modConfigAlwaysBlocks.size() >= ModConfig.MAX_BLOCKS) {
                                    modConfigConfigErrors.add("Max block limit (" + ModConfig.MAX_BLOCKS + ") reached for always list. Some entries not added.");
                                    break;
                                }
                                if (!seen.add(block)) {
                                    modConfigConfigErrors.add("Duplicate always block removed: " + block);
                                    continue;
                                }
                                String validationError = ModConfig.validateBlockId(block);
                                if (validationError == null) {
                                    modConfigAlwaysBlocks.add(block);
                                } else {
                                    modConfigConfigErrors.add("Invalid always block ID: " + block + " (" + validationError + ")");
                                }
                            }
                            ModConfig.saveAlwaysBlockConfig();
                        }
                    })
                    .setErrorSupplier(value -> {
                         // Similar validation logic
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
                                    if (validationError.startsWith("Block does not exist")) {
                                        return Optional.of(Component.literal(validationError).withStyle(ChatFormatting.RED));
                                    }
                                    return Optional.of(Component.literal("Invalid format: " + entry + " ("+validationError+")").withStyle(ChatFormatting.RED));
                                }
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(alwaysBlocksTooltip.toArray(new Component[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .setRequirement(requireToolsEntry::getValue)
                    .build()
            );

            List<Component> pickaxeBlocksTooltip = new ArrayList<>(tooltipLines);
            pickaxeBlocksTooltip.add(Component.literal("Blocks that can be broken by zombies holding a Pickaxe (if 'Break Blocks With Tools Only' is enabled)."));

            pickaxeBlocksCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("Pickaxe Mineable Blocks")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)),
                            currentPickaxeBlockListForGui)
                    .setDefaultValue(ModConfig.loadDefaultToolBlocks("/data/pickaxe_mineable.json"))
                    .setSaveConsumer(newValue -> {
                        List<String> targetList = ModConfig.getPickaxeMineableBlocksInternalList();
                        List<String> errors = ModConfig.getConfigErrorsInternalList();
                        if (!newValue.equals(currentPickaxeBlockListForGui)) {
                            targetList.clear();
                            Set<String> seen = new HashSet<>();
                            for (String block : newValue) {
                                if (targetList.size() >= ModConfig.MAX_BLOCKS) {
                                    errors.add("Max block limit reached for pickaxe list.");
                                    break;
                                }
                                if (!seen.add(block)) continue;
                                String validationError = ModConfig.validateBlockId(block);
                                if (validationError == null) targetList.add(block);
                                else errors.add("Invalid pickaxe block ID: " + block + " (" + validationError + ")");
                            }
                            ModConfig.savePickaxeConfig();
                        }
                    })
                    .setErrorSupplier(value -> {
                        if (value.size() > ModConfig.MAX_BLOCKS) return Optional.of(Component.literal("Too many blocks.").withStyle(ChatFormatting.RED));
                        for (String entry : value) {
                            if (!entry.isEmpty()) {
                                String err = ModConfig.validateBlockId(entry);
                                if (err != null) return Optional.of(Component.literal(err).withStyle(ChatFormatting.RED));
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(pickaxeBlocksTooltip.toArray(new Component[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .setRequirement(requireToolsEntry::getValue)
                    .build()
            );

            List<Component> axeBlocksTooltip = new ArrayList<>(tooltipLines);
            axeBlocksTooltip.add(Component.literal("Blocks that can be broken by zombies holding an Axe (if 'Break Blocks With Tools Only' is enabled)."));

            axeBlocksCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("Axe Mineable Blocks")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)),
                            currentAxeBlockListForGui)
                    .setDefaultValue(ModConfig.loadDefaultToolBlocks("/data/axe_mineable.json"))
                    .setSaveConsumer(newValue -> {
                        List<String> targetList = ModConfig.getAxeMineableBlocksInternalList();
                        List<String> errors = ModConfig.getConfigErrorsInternalList();
                        if (!newValue.equals(currentAxeBlockListForGui)) {
                            targetList.clear();
                            Set<String> seen = new HashSet<>();
                            for (String block : newValue) {
                                if (targetList.size() >= ModConfig.MAX_BLOCKS) {
                                    errors.add("Max block limit reached for axe list.");
                                    break;
                                }
                                if (!seen.add(block)) continue;
                                String validationError = ModConfig.validateBlockId(block);
                                if (validationError == null) targetList.add(block);
                                else errors.add("Invalid axe block ID: " + block + " (" + validationError + ")");
                            }
                            ModConfig.saveAxeConfig();
                        }
                    })
                    .setErrorSupplier(value -> {
                        if (value.size() > ModConfig.MAX_BLOCKS) return Optional.of(Component.literal("Too many blocks.").withStyle(ChatFormatting.RED));
                        for (String entry : value) {
                            if (!entry.isEmpty()) {
                                String err = ModConfig.validateBlockId(entry);
                                if (err != null) return Optional.of(Component.literal(err).withStyle(ChatFormatting.RED));
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(axeBlocksTooltip.toArray(new Component[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .setRequirement(requireToolsEntry::getValue)
                    .build()
            );

            List<Component> shovelBlocksTooltip = new ArrayList<>(tooltipLines);
            shovelBlocksTooltip.add(Component.literal("Blocks that can be broken by zombies holding a Shovel (if 'Break Blocks With Tools Only' is enabled)."));

            shovelBlocksCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("Shovel Mineable Blocks")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)),
                            currentShovelBlockListForGui)
                    .setDefaultValue(ModConfig.loadDefaultToolBlocks("/data/shovel_mineable.json"))
                    .setSaveConsumer(newValue -> {
                        List<String> targetList = ModConfig.getShovelMineableBlocksInternalList();
                        List<String> errors = ModConfig.getConfigErrorsInternalList();
                        if (!newValue.equals(currentShovelBlockListForGui)) {
                            targetList.clear();
                            Set<String> seen = new HashSet<>();
                            for (String block : newValue) {
                                if (targetList.size() >= ModConfig.MAX_BLOCKS) {
                                    errors.add("Max block limit reached for shovel list.");
                                    break;
                                }
                                if (!seen.add(block)) continue;
                                String validationError = ModConfig.validateBlockId(block);
                                if (validationError == null) targetList.add(block);
                                else errors.add("Invalid shovel block ID: " + block + " (" + validationError + ")");
                            }
                            ModConfig.saveShovelConfig();
                        }
                    })
                    .setErrorSupplier(value -> {
                        if (value.size() > ModConfig.MAX_BLOCKS) return Optional.of(Component.literal("Too many blocks.").withStyle(ChatFormatting.RED));
                        for (String entry : value) {
                            if (!entry.isEmpty()) {
                                String err = ModConfig.validateBlockId(entry);
                                if (err != null) return Optional.of(Component.literal(err).withStyle(ChatFormatting.RED));
                            }
                        }
                        return Optional.empty();
                    })
                    .setTooltip(shovelBlocksTooltip.toArray(new Component[0]))
                    .setInsertInFront(true)
                    .setExpanded(true)
                    .setRequirement(requireToolsEntry::getValue)
                    .build()
            );

            AtomicInteger livePickaxe = new AtomicInteger(settings.getPickaxeSpawnChance());
            AtomicInteger liveAxe = new AtomicInteger(settings.getAxeSpawnChance());
            AtomicInteger liveShovel = new AtomicInteger(settings.getShovelSpawnChance());

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
                    entryBuilder.startIntField(Component.literal("X-Ray Target Range"), settings.getTargetRange())
                            .setDefaultValue(defaults.getTargetRange()).setMin(1).setMax(100)
                            .setSaveConsumer(settings::setTargetRange)
                            .setTooltip(Component.literal("Radius in which zombies maintain target lock without line-of-sight."))
                            .build());

            zombieSettingsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Baby Zombies Can Break Blocks"), settings.getBabyZombiesEnabled())
                            .setDefaultValue(defaults.getBabyZombiesEnabled())
                            .setSaveConsumer(settings::setBabyZombiesEnabled)
                            .setTooltip(Component.literal("Toggle whether baby zombies should be able to break blocks"))
                            .build());

            toolsCategory.addEntry(requireToolsEntry);

            toolsCategory.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Enable Bypass List"), settings.getEnableAlwaysBreakableBlockList())
                            .setDefaultValue(defaults.getEnableAlwaysBreakableBlockList())
                            .setSaveConsumer(settings::setEnableAlwaysBreakableBlockList)
                            .setTooltip(Component.literal("Allows zombies to break blocks in 'Always Mineables' list even without tools."))
                            .setRequirement(requireToolsEntry::getValue)
                            .build());

            var toolSpawningGroup = entryBuilder.startSubCategory(Component.literal("Tool Spawning Settings"));
            toolSpawningGroup.add(entryBuilder.startBooleanToggle(Component.literal("Enable Tool Spawning"), settings.getEnableToolChance())
                    .setDefaultValue(defaults.getEnableToolChance())
                    .setSaveConsumer(settings::setEnableToolChance)
                    .setTooltip(Component.literal("Zombies have a chance to spawn with mining tools (Pickaxe, Axe, Shovel)."))
                    .setRequirement(requireToolsEntry::getValue)
                    .build());
            toolSpawningGroup.add(entryBuilder.startIntSlider(Component.literal("Pickaxe Spawn Chance"), settings.getPickaxeSpawnChance(), 0, 100)
                    .setDefaultValue(defaults.getPickaxeSpawnChance())
                    .setSaveConsumer(settings::setPickaxeSpawnChance)
                    .setErrorSupplier(value -> {
                        livePickaxe.set(value);
                        if (livePickaxe.get() + liveAxe.get() + liveShovel.get() > 100) {
                            return Optional.of(Component.literal("Total tool chance cannot exceed 100%").withStyle(ChatFormatting.RED));
                        }
                        return Optional.empty();
                    })
                    .setTextGetter(value -> Component.literal(value + "%"))
                    .setTooltip(Component.literal("Chance for a zombie to spawn with a Pickaxe.\nRequires 'Enable Tool Spawning'.\nExample: 30% means 30% of ALL spawned zombies get a pickaxe."))
                    .setRequirement(requireToolsEntry::getValue)
                    .build());
            toolSpawningGroup.add(entryBuilder.startIntSlider(Component.literal("Axe Spawn Chance"), settings.getAxeSpawnChance(), 0, 100)
                    .setDefaultValue(defaults.getAxeSpawnChance())
                    .setSaveConsumer(settings::setAxeSpawnChance)
                    .setErrorSupplier(value -> {
                        liveAxe.set(value);
                        if (livePickaxe.get() + liveAxe.get() + liveShovel.get() > 100) {
                            return Optional.of(Component.literal("Total tool chance cannot exceed 100%").withStyle(ChatFormatting.RED));
                        }
                        return Optional.empty();
                    })
                    .setTextGetter(value -> Component.literal(value + "%"))
                    .setTooltip(Component.literal("Chance for a zombie to spawn with an Axe.\nRequires 'Enable Tool Spawning'.\nExample: 20% means 20% of ALL spawned zombies get an axe."))
                    .setRequirement(requireToolsEntry::getValue)
                    .build());
            toolSpawningGroup.add(entryBuilder.startIntSlider(Component.literal("Shovel Spawn Chance"), settings.getShovelSpawnChance(), 0, 100)
                    .setDefaultValue(defaults.getShovelSpawnChance())
                    .setSaveConsumer(settings::setShovelSpawnChance)
                    .setErrorSupplier(value -> {
                        liveShovel.set(value);
                        if (livePickaxe.get() + liveAxe.get() + liveShovel.get() > 100) {
                            return Optional.of(Component.literal("Total tool chance cannot exceed 100%").withStyle(ChatFormatting.RED));
                        }
                        return Optional.empty();
                    })
                    .setTextGetter(value -> Component.literal(value + "%"))
                    .setTooltip(Component.literal("Chance for a zombie to spawn with a Shovel.\nRequires 'Enable Tool Spawning'.\nExample: 10% means 10% of ALL spawned zombies get a shovel."))
                    .setRequirement(requireToolsEntry::getValue)
                    .build());
            toolSpawningGroup.setExpanded(true);
            toolsCategory.addEntry(toolSpawningGroup.build());

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