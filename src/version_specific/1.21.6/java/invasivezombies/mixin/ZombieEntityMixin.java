package invasivezombies.mixin;

import invasivezombies.config.ModConfig;
import invasivezombies.goal.BlockBreakGoal;
import invasivezombies.goal.KeepTargetGoal;


import net.minecraft.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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



        //** For Minecraft 1.21.4+
         zombie.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).setBaseValue(settings.getFollowRange());
         zombie.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(settings.getMovementSpeed());
         //**/


    }

}