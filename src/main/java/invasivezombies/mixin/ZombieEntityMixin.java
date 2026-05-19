package invasivezombies.mixin;

import invasivezombies.config.ModConfig;
import invasivezombies.goal.BlockBreakGoal;
import invasivezombies.goal.KeepTargetGoal;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieEntityMixin {

    ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addBlockBreakGoal(CallbackInfo info) {
        Zombie zombie = (Zombie) (Object) this;

        if (zombie instanceof ZombifiedPiglin) {
            return;
        }


        // Remove the BreakDoorGoal & MoveThroughVillageGoal if it exists
        ((MobEntityAccessor) zombie).getGoalSelector().getAvailableGoals().removeIf(goal -> goal.getGoal() instanceof BlockBreakGoal);
        ((MobEntityAccessor) zombie).getGoalSelector().getAvailableGoals().removeIf(goal -> goal.getGoal() instanceof MoveThroughVillageGoal);

        ((MobEntityAccessor) zombie).getGoalSelector().addGoal(0, new KeepTargetGoal(zombie));
        ((MobEntityAccessor) zombie).getGoalSelector().addGoal(2, new BlockBreakGoal(zombie));

    }

    @Inject(method = "randomizeReinforcementsChance", at = @At("TAIL"))
    private void modifyAttributes(CallbackInfo info) {
        Zombie zombie = (Zombie) (Object) this;

        // For Minecraft 1.19.4 until 1.21
        zombie.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(settings.getFollowRange());
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(settings.getMovementSpeed());


    }

    // Configuration variables - adjust these percentages as needed
    private static final float PICKAXE_CHANCE = 0.60f;
    private static final float AXE_CHANCE = 0.20f;
    private static final float SHOVEL_CHANCE = 0.20f;

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void addCustomTools(RandomSource random, DifficultyInstance localDifficulty, CallbackInfo ci) {
        Zombie zombie = (Zombie)(Object)this;

        // Clear any existing mainhand item
        zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        // First decide if zombie should get ANY tool
        float totalChance = PICKAXE_CHANCE + AXE_CHANCE + SHOVEL_CHANCE;
        if (random.nextFloat() >= totalChance) {
            return; // No tool
        }

        // Now randomly pick which tool type
        float roll = random.nextFloat() * totalChance;

        if (roll < PICKAXE_CHANCE) {
            // Pickaxe
            ItemStack pickaxe = random.nextBoolean() ?
                    new ItemStack(Items.STONE_PICKAXE) :
                    new ItemStack(Items.IRON_PICKAXE);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
        } else if (roll < PICKAXE_CHANCE + AXE_CHANCE) {
            // Axe
            ItemStack axe = random.nextBoolean() ?
                    new ItemStack(Items.STONE_AXE) :
                    new ItemStack(Items.IRON_AXE);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, axe);
        } else {
            // Shovel
            ItemStack shovel = random.nextBoolean() ?
                    new ItemStack(Items.STONE_SHOVEL) :
                    new ItemStack(Items.IRON_SHOVEL);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, shovel);
        }
    }

}