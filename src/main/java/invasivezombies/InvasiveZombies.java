package invasivezombies;

import invasivezombies.goal.BlockBreakGoal;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;


import net.fabricmc.api.EnvType;



public class InvasiveZombies implements ModInitializer {

    @Override
    public void onInitialize() {

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            if (!FabricLoader.getInstance().isModLoaded("modmenu") &&
                    !FabricLoader.getInstance().isModLoaded("modsettings")) {


                // Force a crash or visible error
                throw new RuntimeException(
                        "\n++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n" +
                                "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n" +
                                "This mod requires either ModMenu or ModSettings to be installed!\n" +
                                "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n" +
                                "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n"
                );
            }
        }


        ServerWorldEvents.LOAD.register((server, world) -> BlockBreakGoal.resetAllMiningStates());

        ServerWorldEvents.UNLOAD.register((server, world) -> BlockBreakGoal.resetAllMiningStates());

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ZombieEntity zombie) {
               BlockBreakGoal.resetMiningState(zombie);
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ZombieEntity zombie) {
                BlockBreakGoal.resetMiningState(zombie);
            }
        });

        ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((originalEntity, newEntity, origin, destination) -> {
            if (newEntity instanceof ZombieEntity zombie) {
                BlockBreakGoal.resetMiningState(zombie);
            }
        });
	
    }




    public static Identifier id(String path) {
        // Use VersionHelper.CustomIdentifier here to ensure consistency
        return VersionHelper.CustomIdentifier("invasivezombies"); // Use CustomIdentifier to build the Identifier
    }
	
	
}