package invasivezombies.goal;

import invasivezombies.config.ModConfig;
import java.util.EnumSet;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class KeepTargetGoal extends Goal {

    private final Zombie zombie;
    private boolean targetSeen;
    private LivingEntity previousTarget = null;
    private int targetSwitchCooldown = 0;


    private static final ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();

    private static final double maxTargetRange = settings.getTargetRange();
    private static final double maxFollowRange = settings.getFollowRange();


    public KeepTargetGoal(Zombie zombie) {
        this.zombie = zombie;


        // TESTING - disable target flag so this goal can run parallel to other goals and not interfere as much

        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {

        if (zombie == null || !zombie.isAlive() || zombie.level() == null) {
            return false;
        }


        validateCurrentTarget();

        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget != previousTarget) {
            // Target has changed, reset targetSeen
            targetSeen = false;
            previousTarget = currentTarget;
        }

        Level level = zombie.level();
        if (level == null) return false;

        // For visibility tracking (used for extended follow range)
        // Check if we can see any player at all within max follow range
        LivingEntity visiblePlayer = findNearestValidPlayer(maxFollowRange);
        if (visiblePlayer != null && zombie.hasLineOfSight(visiblePlayer)) {
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
                if (farPlayer != null && farPlayer.distanceToSqr(zombie) > maxTargetRange * maxTargetRange) {
                    zombie.setTarget(farPlayer);
                } else if (farPlayer == null) {
                    LivingEntity farVillager = findNearestValidVillager(maxFollowRange);
                    if (farVillager != null && farVillager.distanceToSqr(zombie) > maxTargetRange * maxTargetRange) {
                        zombie.setTarget(farVillager);
                    }
                }
            }


        return false;
    }

    private void validateCurrentTarget() {
        LivingEntity target = zombie.getTarget();

        if (target != null) {
            if (target instanceof Player player) {
                // Clear target if player is now in creative, spectator, or invisible
                if (player.isCreative() || player.isSpectator() ||
                        player.hasEffect(MobEffects.INVISIBILITY)) {
                    zombie.setTarget(null);
                    targetSeen = false;
                }
            } else if (target instanceof Villager villager) {
                // Clear target if villager is invisible
                if (villager.hasEffect(MobEffects.INVISIBILITY)) {
                    zombie.setTarget(null);
                    targetSeen = false;
                }
            }

            // Check if target is too far away
            if (zombie.distanceToSqr(target) > maxFollowRange * maxFollowRange) {
                zombie.setTarget(null);
                targetSeen = false;
            }
        }
    }

    private Player findNearestValidPlayer(double range) {

        if (zombie == null || zombie.level() == null) return null;

        Player nearestPlayer = null;
        double closestDistance = Double.MAX_VALUE;
        double rangeSquared = range * range;

        // Use getEntitiesByClass for better performance than getPlayers()
        for (Player player : zombie.level().getEntitiesOfClass(
                Player.class,
                zombie.getBoundingBox().inflate(range),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative() &&
                        !player.hasEffect(MobEffects.INVISIBILITY))) {

            double distance = zombie.distanceToSqr(player);
            if (distance <= rangeSquared && distance < closestDistance) {
                closestDistance = distance;
                nearestPlayer = player;
            }
        }

        return nearestPlayer;
    }

    private Villager findNearestValidVillager(double range) {

        if (zombie == null || zombie.level() == null) return null;


        Villager nearestVillager = null;
        double closestDistance = Double.MAX_VALUE;
        double rangeSquared = range * range;

        for (Villager villager : zombie.level().getEntitiesOfClass(
                Villager.class,
                zombie.getBoundingBox().inflate(range),
                villager -> villager.isAlive() &&
                        !villager.hasEffect(MobEffects.INVISIBILITY))) {

            double distance = zombie.distanceToSqr(villager);
            if (distance <= rangeSquared && distance < closestDistance) {
                closestDistance = distance;
                nearestVillager = villager;
            }
        }

        return nearestVillager;
    }
}