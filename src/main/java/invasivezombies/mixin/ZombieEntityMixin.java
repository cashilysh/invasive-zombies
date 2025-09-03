package invasivezombies.mixin;

import invasivezombies.config.ModConfig;
import invasivezombies.goal.BlockBreakGoal;
import invasivezombies.goal.KeepTargetGoal;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;

import net.minecraft.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.math.random.Random;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {

    ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void addBlockBreakGoal(CallbackInfo info) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;


        // Remove the BreakDoorGoal & MoveThroughVillageGoal if it exists
        ((MobEntityAccessor) zombie).getGoalSelector().getGoals().removeIf(goal -> goal.getGoal() instanceof BlockBreakGoal);
        ((MobEntityAccessor) zombie).getGoalSelector().getGoals().removeIf(goal -> goal.getGoal() instanceof MoveThroughVillageGoal);

        ((MobEntityAccessor) zombie).getGoalSelector().add(0, new KeepTargetGoal(zombie));
        ((MobEntityAccessor) zombie).getGoalSelector().add(2, new BlockBreakGoal(zombie));

    }

    @Inject(method = "initAttributes", at = @At("TAIL"))
    private void modifyAttributes(CallbackInfo info) {
        ZombieEntity zombie = (ZombieEntity) (Object) this;

        // For Minecraft 1.19.4 until 1.21
        zombie.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).setBaseValue(settings.getFollowRange());
        zombie.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(settings.getMovementSpeed());


    }

    // Configuration variables - adjust these percentages as needed
    private static final float PICKAXE_CHANCE = 0.60f;
    private static final float AXE_CHANCE = 0.20f;
    private static final float SHOVEL_CHANCE = 0.20f;

    @Inject(method = "initEquipment", at = @At("TAIL"))
    private void addCustomTools(Random random, LocalDifficulty localDifficulty, CallbackInfo ci) {
        ZombieEntity zombie = (ZombieEntity)(Object)this;

        // Clear any existing mainhand item
        zombie.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

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
            zombie.equipStack(EquipmentSlot.MAINHAND, pickaxe);
        } else if (roll < PICKAXE_CHANCE + AXE_CHANCE) {
            // Axe
            ItemStack axe = random.nextBoolean() ?
                    new ItemStack(Items.STONE_AXE) :
                    new ItemStack(Items.IRON_AXE);
            zombie.equipStack(EquipmentSlot.MAINHAND, axe);
        } else {
            // Shovel
            ItemStack shovel = random.nextBoolean() ?
                    new ItemStack(Items.STONE_SHOVEL) :
                    new ItemStack(Items.IRON_SHOVEL);
            zombie.equipStack(EquipmentSlot.MAINHAND, shovel);
        }
    }

}