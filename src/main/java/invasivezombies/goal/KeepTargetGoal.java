package invasivezombies.goal;

import invasivezombies.config.ModConfig;


import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;

import java.util.EnumSet;

public class KeepTargetGoal extends Goal {

    private final ZombieEntity zombie;
    private boolean targetSeen;
    private LivingEntity previousTarget = null;
    private int targetSwitchCooldown = 0;


    private static final ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();

    private static final double maxTargetRange = settings.getTargetRange();
    private static final double maxFollowRange = settings.getFollowRange();


    public KeepTargetGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Goal.Control.TARGET));
    }

    @Override
    public boolean canStart() {
        if (zombie == null || zombie.getWorld() == null) return false;


        validateCurrentTarget();

        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget != previousTarget) {
            // Target has changed, reset targetSeen
            targetSeen = false;
            previousTarget = currentTarget;
        }

        // For visibility tracking (used for extended follow range)
        // Check if we can see any player at all within max follow range
        LivingEntity visiblePlayer = findNearestValidPlayer(maxFollowRange);
        if (visiblePlayer != null && zombie.canSee(visiblePlayer)) {
            targetSeen = true;
        }

            // If we can switch targets (not on cooldown)
            // Priority 1: Target players within close range regardless of visibility (X-ray vision)
            LivingEntity nearestPlayer = findNearestValidPlayer(maxTargetRange);

            // Priority 2: If no players, check for villagers within close range (X-ray vision)
            LivingEntity nearestVillager = nearestPlayer == null ? findNearestValidVillager(maxTargetRange) : null;

            // Set target based on priority
            if (nearestPlayer != null && (zombie.getTarget() == null ||
                    !nearestPlayer.equals(zombie.getTarget()))) {
                zombie.setTarget(nearestPlayer);
            } else if (nearestVillager != null && (zombie.getTarget() == null ||
                    !nearestVillager.equals(zombie.getTarget()))) {
                zombie.setTarget(nearestVillager);

            } else if (targetSeen && zombie.getTarget() == null) {
                // Priority 3: If we've seen a target before, extend to the follow range
                // This is for when targets move outside maxTargetRange but are still within maxFollowRange
                LivingEntity farPlayer = findNearestValidPlayer(maxFollowRange);
                if (farPlayer != null && farPlayer.squaredDistanceTo(zombie) > maxTargetRange * maxTargetRange) {
                    zombie.setTarget(farPlayer);
                } else if (farPlayer == null) {
                    LivingEntity farVillager = findNearestValidVillager(maxFollowRange);
                    if (farVillager != null && farVillager.squaredDistanceTo(zombie) > maxTargetRange * maxTargetRange) {
                        zombie.setTarget(farVillager);
                    }
                }
            }


        return false;
    }

    private void validateCurrentTarget() {
        LivingEntity target = zombie.getTarget();

        if (target != null) {
            if (target instanceof PlayerEntity player) {
                // Clear target if player is now in creative, spectator, or invisible
                if (player.isCreative() || player.isSpectator() ||
                        player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    zombie.setTarget(null);
                    targetSeen = false;
                }
            } else if (target instanceof VillagerEntity villager) {
                // Clear target if villager is invisible
                if (villager.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    zombie.setTarget(null);
                    targetSeen = false;
                }
            }

            // Check if target is too far away
            if (zombie.squaredDistanceTo(target) > maxFollowRange * maxFollowRange) {
                zombie.setTarget(null);
                targetSeen = false;
            }
        }
    }

    private PlayerEntity findNearestValidPlayer(double range) {
        PlayerEntity nearestPlayer = null;
        double closestDistance = Double.MAX_VALUE;
        double rangeSquared = range * range;

        // Use getEntitiesByClass for better performance than getPlayers()
        for (PlayerEntity player : zombie.getWorld().getEntitiesByClass(
                PlayerEntity.class,
                zombie.getBoundingBox().expand(range),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative() &&
                        !player.hasStatusEffect(StatusEffects.INVISIBILITY))) {

            double distance = zombie.squaredDistanceTo(player);
            if (distance <= rangeSquared && distance < closestDistance) {
                closestDistance = distance;
                nearestPlayer = player;
            }
        }

        return nearestPlayer;
    }

    private VillagerEntity findNearestValidVillager(double range) {
        VillagerEntity nearestVillager = null;
        double closestDistance = Double.MAX_VALUE;
        double rangeSquared = range * range;

        for (VillagerEntity villager : zombie.getWorld().getEntitiesByClass(
                VillagerEntity.class,
                zombie.getBoundingBox().expand(range),
                villager -> villager.isAlive() &&
                        !villager.hasStatusEffect(StatusEffects.INVISIBILITY))) {

            double distance = zombie.squaredDistanceTo(villager);
            if (distance <= rangeSquared && distance < closestDistance) {
                closestDistance = distance;
                nearestVillager = villager;
            }
        }

        return nearestVillager;
    }
}