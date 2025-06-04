package invasivezombies.goal;

import invasivezombies.config.ModConfig;
import invasivezombies.VersionHelper;
import invasivezombies.mixin.MobEntityAccessor;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings({"ConstantValue", "DanglingJavadoc"})
public class BlockBreakGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger("invasivezombies");

    private static final ModConfig.ZombieSettings settings = ModConfig.getZombieSettings();

    private static final float MINING_SPEED = settings.getMiningSpeed();
    private static final float HORIZONTAL_MINING_RANGE = 2.0F; //initial mining range
    private static final float VERTICAL_MINING_RANGE = 3.0F; //initial mining range


    private final ZombieEntity zombie;
    private BlockPos targetBlock;
    private int miningTicks;
    private float breakProgress;
    private int noPathTicks;

    private int failedBlockFindings;
    private int blockFindingIdleTicks;

    private int prevfailedBlockFindings;
    private int prevblockFindingIdleTicks;

    private final boolean debug = false;
    private final boolean blockdebug = false;

    private Path cachedPath = null;
    private int pathUpdateCounter = 0;
    private static final int PATH_UPDATE_INTERVAL = 3;
    private BlockPos lastTargetPos = null;


    private static final int MAX_MINING_POSITIONS = 50;


    private static final int MAX_IDLE_TICKS_DURING_BLOCK_FINDING = 20;


    private static final Set<BlockPos> currentlyMining = Collections.newSetFromMap(new ConcurrentHashMap<>(MAX_MINING_POSITIONS));

    private static final Map<ZombieEntity, BlockBreakGoal> goalMap = new ConcurrentHashMap<>();
    private static final Map<String, TagKey<Block>> tagCache = new ConcurrentHashMap<>();

    private static volatile Set<String> breakableBlocks;
    private static final Object INIT_LOCK = new Object();


    public BlockBreakGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        goalMap.put(zombie, this); // Register the goal for this zombie
        this.setControls(EnumSet.of(Goal.Control.LOOK, Goal.Control.MOVE)); // Added control flags
        loadBreakableBlocks();
    }

    private void loadBreakableBlocks() {
        synchronized (INIT_LOCK) {
            if (breakableBlocks == null) {
                breakableBlocks = ModConfig.getMineableBlocks();
            }
        }
    }

    public static void resetAllMiningStates() {
        currentlyMining.clear();
    }

    @Override
    public boolean canStart() {
        if (debug && (prevfailedBlockFindings < failedBlockFindings)) {
            System.out.println("canStart() FBF: " + failedBlockFindings);
            prevfailedBlockFindings = failedBlockFindings;
        }

        if (zombie == null || zombie.getTarget() == null) {
            return false; // Prevent null dereference
        }

        if (zombie.isBaby() && !settings.getBabyZombiesEnabled()) {
            return false;
        }

        if (zombie.getNavigation().isFollowingPath()) {
            if (debug) System.out.println("Zombie is still following a path!");
            return false;
        }

        // Check if we need to find a new block (current path doesn't reach target)
        Path path = getCachedPathToTarget();

        // If we have a valid path to target, no need to break blocks
        if (path != null && path.reachesTarget() && path.isFinished()) {
            return false;
        }

        // Try to find a block to break if path exists but doesn't reach target
        if (path != null && !path.reachesTarget()) {
            BlockPos blockToBreak = findBlockToBreak();
            // Only select this block if it's valid and not being mined by another zombie
            if (blockToBreak != null && !currentlyMining.contains(blockToBreak)) {
                targetBlock = blockToBreak;
                currentlyMining.add(targetBlock);
                miningTicks = 0;
                breakProgress = 0;
                blockFindingIdleTicks = 0;
                return true;
            }
        }

        return false;
    }


    private boolean isBlockAccessibleFromDirection(World world, BlockPos targetBlock, BlockPos startPos) {
        Vec3d startPosBottom;
        Vec3d startPosTop;

        startPosBottom = new Vec3d(
                startPos.getX() + (zombie.getWidth() / 2.0),
                startPos.getY() + 0.5,
                startPos.getZ() + (zombie.getWidth() / 2.0)
        );
        startPosTop = new Vec3d(
                startPos.getX() + (zombie.getWidth() / 2.0),
                startPos.getY() + zombie.getHeight() - 0.5,
                startPos.getZ() + (zombie.getWidth() / 2.0)
        );

        Vec3d targetPos = new Vec3d(
                targetBlock.getX() + 0.5,
                targetBlock.getY() + 0.5,
                targetBlock.getZ() + 0.5
        );

        return hasLineOfSight(world, startPosBottom, targetPos, targetBlock) ||
                hasLineOfSight(world, startPosTop, targetPos, targetBlock);
    }

    private boolean hasLineOfSight(World world, Vec3d start, Vec3d end, BlockPos targetBlock) {
        double distance = start.distanceTo(end);
        Vec3d ray = end.subtract(start).normalize();

        for (double d = 0.30; d < distance - 0.30; d += 0.30) {
            Vec3d checkPoint = start.add(ray.multiply(d));

            BlockPos checkPos = new BlockPos((int) Math.floor(checkPoint.x), (int) Math.floor(checkPoint.y), (int) Math.floor(checkPoint.z));

            if (checkPos.equals(targetBlock)) {
                continue;
            }

            if (!world.getBlockState(checkPos).isAir()) {
                return false;
            }
        }

        return true;
    }

    private int[] getminYmaxYAtEndNode(Vec3d endPos, Vec3d targetpos) {

        int minY, maxY, zombieBelowTarget;

        // Calculate height difference (positive means target is above zombie)
        double yDiff = targetpos.y - endPos.y;

        if (Math.abs(yDiff) < 0.5) { // Same level
            minY = 0;
            maxY = 1;
            zombieBelowTarget = 0;
        } else if (yDiff < -1) { // Zombie above target by MORE than 1 block
            minY = -1;
            maxY = 1;
            zombieBelowTarget = 0;
        } else if (yDiff < 0) { // Zombie above target by less than 1 block
            minY = 0;
            maxY = 1;
            zombieBelowTarget = 0;
        } else if (yDiff <= 1) { // Zombie below target by up to 1 block
            minY = 0;
            maxY = 1;
            zombieBelowTarget = 1;
        } else if (yDiff <= 2) { // Zombie below target by 1-2 blocks
            minY = 0;
            maxY = 2;
            zombieBelowTarget = 1;
        } else { // Zombie below target by MORE than 2 blocks
            minY = 0;
            maxY = 3;
            zombieBelowTarget = 1;
        }

        return new int[]{minY, maxY, zombieBelowTarget};
    }

    private BlockPos findBlockToBreak() {


        //execute the intensive blocksearch code only 1/2 of times

        if (new Random().nextInt(2) == 0) return null;

        World world = zombie.getWorld();
        LivingEntity target = zombie.getTarget();
        Vec3d zombiePos = zombie.getPos();
        Direction facing = zombie.getHorizontalFacing();

        if (!(world instanceof ServerWorld) || target == null || zombiePos == null || target.getPos() == null || facing == null) {
            return null;
        }

        Vec3d targetPos = target.getPos(); // Now safe to access

        int[] heightRange = getminYmaxYAtEndNode(zombiePos, targetPos);

        int minY = heightRange[0];
        int maxY = heightRange[1];
        int zombieBelowTarget = heightRange[2];


        double distanceToTarget = Math.sqrt(zombie.squaredDistanceTo(target));

        // Get zombie block position
        BlockPos zombieBlockPos = new BlockPos((int) Math.round(zombiePos.x), (int) Math.round(zombiePos.y), (int) Math.round(zombiePos.z));

        // Set mining parameters based on situation
        int checkRadius = distanceToTarget > 8.0 ? 2 : 1;

        int doorSearchRadius = settings.getDoorSearchDistance();
        int FarBlockRange = settings.getFarBlockSearchDistance();


        /** ----------------------------------------------------------- **/
        /** Priority 1: Check blocks directly between zombie and target **/
        /** ----------------------------------------------------------- **/


        List<BlockPos> directPathBlocks = new ArrayList<>();
        Vec3d directionVec = targetPos.subtract(zombiePos).normalize();

        int maxCheckDistance = (int) Math.min(distanceToTarget, 3.0);

        // First gather direct path blocks
        for (int i = 1; i <= maxCheckDistance; i++) {
            Vec3d checkVec = zombiePos.add(directionVec.multiply(i));
            int checkX = (int) Math.floor(checkVec.x);
            int checkZ = (int) Math.floor(checkVec.z);


            // When zombie is below target, search from maxY to minY (downward)
            // When zombie is above target, search from minY to maxY (upward)
            int startY = (zombieBelowTarget == 1) ? maxY : minY;
            int endY = (zombieBelowTarget == 1) ? minY : maxY;
            int step = (zombieBelowTarget == 1) ? -1 : 1;

            for (int y = startY; (zombieBelowTarget == 1) ? (y >= endY) : (y <= endY); y += step) {


                BlockPos checkPos = new BlockPos(checkX, (int) zombiePos.y + y, checkZ);

                BlockState blockState = world.getBlockState(checkPos);
                if (!blockState.isAir()) {
                    if (isBreakableBlock(world, checkPos)) {
                        if (isBlockAccessibleFromDirection(world, checkPos, zombieBlockPos)) {
                            if (!currentlyMining.contains(checkPos)) {
                                directPathBlocks.add(checkPos);
                            }
                        }
                    }
                }
            }
        }

        // Check if any direct path block is available
        if (!directPathBlocks.isEmpty()) {
            BlockPos pos = directPathBlocks.get(new Random().nextInt(directPathBlocks.size()));
            if (!currentlyMining.contains(pos)) {
                failedBlockFindings = 0;
                if (blockdebug) System.out.println("Direct (random) block between zombie and target found!");
                return pos;
            }
        }

        // Create a list of offsets and shuffle them
        List<int[]> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip center
                offsets.add(new int[]{x, z});
            }
        }
        Collections.shuffle(offsets);

        // Only if all direct path blocks are being mined, then check adjacent blocks
        for (int i = 1; i <= maxCheckDistance; i++) {
            Vec3d checkVec = zombiePos.add(directionVec.multiply(i));
            int checkX = (int) Math.floor(checkVec.x);
            int checkZ = (int) Math.floor(checkVec.z);

            for (int[] offset : offsets) {
                int xOffset = offset[0];
                int zOffset = offset[1];

                if (xOffset == 0 && zOffset == 0) continue; // Skip center

                // When zombie is below target, search from maxY to minY (downward)
                // When zombie is above target, search from minY to maxY (upward)
                int startY = (zombieBelowTarget == 1) ? maxY : minY;
                int endY = (zombieBelowTarget == 1) ? minY : maxY;
                int step = (zombieBelowTarget == 1) ? -1 : 1;

                for (int y = startY; (zombieBelowTarget == 1) ? (y >= endY) : (y <= endY); y += step) {

                    BlockPos adjacentPos = new BlockPos(checkX + xOffset, (int) zombiePos.y + y, checkZ + zOffset);

                    BlockState blockState = world.getBlockState(adjacentPos);

                    if (!blockState.isAir()) {
                        if (isBreakableBlock(world, adjacentPos)) {
                            if (!currentlyMining.contains(adjacentPos)) {
                                if (isBlockAccessibleFromDirection(world, adjacentPos, zombieBlockPos)) {
                                    failedBlockFindings = 0;
                                    if (blockdebug)
                                        System.out.println("Adjacent block between zombie and target found!");
                                    return adjacentPos;
                                }
                            }
                        }
                    }
                }
            }
        }


        /** ------------------------------------------ **/
        /** Priority 2: Check navigation path end node **/
        /** ------------------------------------------ **/

        // Create a list of offsets and shuffle them
        List<int[]> offsets2 = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                offsets2.add(new int[]{x, z});
            }
        }
        Collections.shuffle(offsets2);


        Path path = getCachedPathToTarget();

        if (path != null) {

            PathNode endNode = path.getEnd();
            Vec3d endPos = endNode.getPos();
            BlockPos endNodeBlockPos = new BlockPos((int) Math.round(endPos.x), (int) Math.round(endPos.y), (int) Math.round(endPos.z));

            int[] nodeHeightRange = getminYmaxYAtEndNode(endPos, targetPos);
            int minYatNode = nodeHeightRange[0];
            int maxYatNode = nodeHeightRange[1];
            int zombieBelowTargetatNode = nodeHeightRange[2];


            // Check the blocks around the node (adjust the range if needed)
            for (int[] offset : offsets2) {
                int xOffset = offset[0];
                int zOffset = offset[1];

                int startY = (zombieBelowTargetatNode == 1) ? maxYatNode : minYatNode;
                int endY = (zombieBelowTargetatNode == 1) ? minYatNode : maxYatNode;
                int step = (zombieBelowTargetatNode == 1) ? -1 : 1;

                for (int y = startY; (zombieBelowTargetatNode == 1) ? (y >= endY) : (y <= endY); y += step) {
                    BlockPos checkPos = new BlockPos(endNodeBlockPos.getX() + xOffset, endNodeBlockPos.getY() + y, endNodeBlockPos.getZ() + zOffset);

                    BlockState blockState = world.getBlockState(checkPos);

                    // Filter out air blocks and only check breakable ones
                    if (!blockState.isAir()) {
                        if (isBreakableBlock(world, checkPos)) {
                            if (!currentlyMining.contains(checkPos)) {
                                if (isBlockAccessibleFromDirection(world, checkPos, endNodeBlockPos)) {
                                    failedBlockFindings = 0;
                                    if (blockdebug)
                                        System.out.println("Block on path End node found!");
                                    return checkPos;
                                }
                            }
                        }
                    }
                }
            }
        }




        /** --------------------------- **/
        /** Priority 3: Look for  doors **/
        /** --------------------------- **/

        if (failedBlockFindings % 10 == 0) {
            List<BlockPos> doorBlocks = new ArrayList<>();

            // Collect all door blocks in the area
            for (int x = -doorSearchRadius; x <= doorSearchRadius; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -doorSearchRadius; z <= doorSearchRadius; z++) {
                        BlockPos doorPos = zombieBlockPos.add(x, y, z);

                        // Skip positions too far away
                        if (x * x + y * y + z * z > doorSearchRadius * doorSearchRadius) {
                            continue;
                        }

                        BlockState state = world.getBlockState(doorPos);

                        if (!state.isAir()) {
                            if (state.isIn(BlockTags.DOORS) && isBreakableBlock(world, doorPos)) {

                                if (canPathToBlockAndIsAccessible(doorPos)) {
                                    doorBlocks.add(doorPos);
                                }
                            }
                        }
                    }
                }
            }

            // Try each door in sequence until finding one that's not being mined
            for (BlockPos doorPos : doorBlocks) {
                if (!currentlyMining.contains(doorPos)) {
                    failedBlockFindings = 0;
                    if (blockdebug) System.out.println("Door Block found!");
                    return doorPos;
                }
            }

        }


        /** --------------------------- **/
        /** Priority 4: Check in facing **/
        /** --------------------------- **/

        List<BlockPos> facingBlocks = new ArrayList<>();

        // Collect main path blocks
        for (
                int dist = 1;
                dist <= checkRadius; dist++) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos checkPos = new BlockPos(
                        zombieBlockPos.getX() + facing.getOffsetX() * dist,
                        zombieBlockPos.getY() + y,
                        zombieBlockPos.getZ() + facing.getOffsetZ() * dist);
                BlockState blockState = world.getBlockState(checkPos);
                if (!blockState.isAir()) {
                    if (isBreakableBlock(world, checkPos)) {
                        if (isBlockAccessibleFromDirection(world, checkPos, zombieBlockPos)) {
                            if (!currentlyMining.contains(checkPos)) {

                                facingBlocks.add(checkPos);
                            }
                        }
                    }
                }
            }
        }

        // First try main blocks
        for (
                BlockPos pos : facingBlocks) {
            if (!currentlyMining.contains(pos)) {
                failedBlockFindings = 0;
                if (blockdebug) System.out.println("Facing direction block found!");
                return pos;
            }
        }

        // Then try adjacent positions if needed
        int perpX = facing.getOffsetZ();
        int perpZ = -facing.getOffsetX();

        for (
                int dist = 1;
                dist <= checkRadius; dist++) {
            for (int y = minY; y <= maxY; y++) {
                for (int offset = -1; offset <= 1; offset += 2) {
                    BlockPos adjacentPos = new BlockPos(
                            zombieBlockPos.getX() + facing.getOffsetX() * dist + perpX * offset,
                            zombieBlockPos.getY() + y,
                            zombieBlockPos.getZ() + facing.getOffsetZ() * dist + perpZ * offset);

                    BlockState blockState = world.getBlockState(adjacentPos);
                    if (!blockState.isAir()) {
                        if (isBreakableBlock(world, adjacentPos)) {
                            if (!currentlyMining.contains(adjacentPos)) {
                                if (isBlockAccessibleFromDirection(world, adjacentPos, zombieBlockPos)) {
                                    failedBlockFindings = 0;
                                    if (blockdebug) System.out.println("Facing direction adjacent block found!");
                                    return adjacentPos;
                                }
                            }
                        }
                    }
                }
            }
        }

        /** ------------------------------------ **/
        /** Priority 5: Look for Far Away Blocks **/
        /** ------------------------------------ **/

        if (failedBlockFindings % 20 == 0) {
            // Create a priority queue to keep track of the top 5 nearest blocks
            PriorityQueue<Map.Entry<BlockPos, Double>> topCandidates = new PriorityQueue<>(
                    5, Comparator.comparingDouble(Map.Entry::getValue));

            // Get target coordinates
            double tX = targetPos.getX();
            double tZ = targetPos.getZ();
            double zX = zombieBlockPos.getX();
            double zZ = zombieBlockPos.getZ();

            // Calculate direction vector from zombie to target
            double dX = tX - zX;
            double dZ = tZ - zZ;

            // Calculate distance
            double distance = Math.sqrt(dX * dX + dZ * dZ);

            // Normalize the direction vector if not at same position
            if (distance > 0.1) {
                dX = dX / distance;
                dZ = dZ / distance;
            }

            List<Map.Entry<BlockPos, Double>> potentialCandidates = new ArrayList<>();

            int myOffsetX;
            int myOffsetZ;
            Random random = new Random();

            int stepSize = 2; // Fixed step size

            // Ensure we get a random offset for each dimension
            myOffsetX = random.nextInt(stepSize);
            myOffsetZ = random.nextInt(stepSize);

            for (int x = -FarBlockRange + myOffsetX; x <= FarBlockRange; x += stepSize) {
                for (int y = maxY; y >= minY; y--) {
                    for (int z = -FarBlockRange + myOffsetZ; z <= FarBlockRange; z += stepSize) {


                        BlockPos blockPos = zombieBlockPos.add(x, y, z);

                        // Calculate distance from zombie
                        double distanceSquared = zombieBlockPos.getSquaredDistance(blockPos);

                        // Skip positions too far
                        if (distanceSquared > FarBlockRange * FarBlockRange) {
                            continue;
                        }

                        // Skip positions too close
                        if (distanceSquared < 2 * 2) {
                            continue;
                        }

                        // Check if the block is more than 3 blocks behind the zombie
                        // relative to the target direction
                        double relX = blockPos.getX() - zX;
                        double relZ = blockPos.getZ() - zZ;

                        // Calculate dot product to determine if the block is behind
                        double dotProduct = relX * dX + relZ * dZ;

                        // If dot product is negative, the block is behind the zombie
                        // Calculate how far behind using projection
                        if (dotProduct < 0) {
                            double behindDistance = Math.abs(dotProduct);
                            if (behindDistance > 3) {
                                continue; // Skip blocks more than 3 blocks behind
                            }
                        }

                        BlockState blockState = world.getBlockState(blockPos);

                        if (!blockState.isAir()) {
                            if (!blockState.isOf(Blocks.GRASS_BLOCK)) {
                                if (!currentlyMining.contains(blockPos)) {
                                    if (isBreakableBlock(world, blockPos)) {
                                        if (canPathToBlockAndIsAccessible(blockPos)) {
                                            potentialCandidates.add(new AbstractMap.SimpleEntry<>(blockPos, distanceSquared));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Sort candidates by squared distance first (quick filter)
            potentialCandidates.sort(Comparator.comparingDouble(Map.Entry::getValue));


            // Take top N candidates to avoid checking paths for all blocks
            List<Map.Entry<BlockPos, Double>> shortlistCandidates = potentialCandidates.stream()
                    .limit(15) // Check more than 5 since some might not have valid paths
                    .toList();

            // Now evaluate actual path lengths for our shortlisted candidates
            for (Map.Entry<BlockPos, Double> candidate : shortlistCandidates) {
                BlockPos candidateBlock = candidate.getKey();


                Path pathToBlock = zombie.getNavigation().findPathTo(candidateBlock, 0);

                if (pathToBlock != null) {

                    double distanceToBlock = Math.sqrt(zombieBlockPos.getSquaredDistance(candidateBlock));

                    // Add to priority queue using path length
                    topCandidates.offer(new AbstractMap.SimpleEntry<>(candidateBlock, distanceToBlock));

                    // If we have more than 5 candidates, remove the farthest one
                    if (topCandidates.size() > 5) {
                        topCandidates.poll();
                    }
                }
            }

            // Store candidates in a list for easier iteration
            List<BlockPos> candidateBlocks = new ArrayList<>();
            while (!topCandidates.isEmpty()) {
                candidateBlocks.addFirst(topCandidates.poll().getKey());
            }

            // Return the first (closest by path) candidate
            if (!candidateBlocks.isEmpty()) {
                if (debug) System.out.println("Found far block!");


                return candidateBlocks.getFirst();
            }

            return null;
        }


        failedBlockFindings++;
        return null;
    }


    private boolean canPathToBlockAndIsAccessible(BlockPos blockpos) {
        // Early validation - no change needed
        if (zombie == null || zombie.getWorld() == null || blockpos == null) return false;

        World world = zombie.getWorld();
        LivingEntity target = zombie.getTarget();
        Vec3d targetPos = target.getPos(); // Now safe to access


        // Only now find path (expensive operation)
        Path blockPath = zombie.getNavigation().findPathTo(blockpos, 0);
        if (blockPath == null || blockPath.getEnd() == null) return false;

        PathNode endNode = blockPath.getEnd();
        Vec3d endPos = endNode.getPos();
        BlockPos endNodeBlockPos = new BlockPos((int) Math.round(endPos.x), (int) Math.round(endPos.y), (int) Math.round(endPos.z));

        int[] heightRange = getminYmaxYAtEndNode(endPos, targetPos);
        int minYatNode = heightRange[0];
        int maxYatNode = heightRange[1];

        // Add vertical height check using minY and maxY
        int blockYRelativeToNode = blockpos.getY() - endNodeBlockPos.getY();

        if (blockYRelativeToNode < minYatNode || blockYRelativeToNode > maxYatNode) {
            return false; // Block is outside vertical range
        }

        // Calculate distances directly from the PathNode position
        // This avoids creating temporary BlockPos objects
        double xDistance = Math.abs((endPos.getX() + zombie.getWidth() / 2.0) - (blockpos.getX() + 0.5));
        double yDistance = Math.abs((endPos.getY() + zombie.getHeight() / 2.0) - (blockpos.getY() + 0.5));
        double zDistance = Math.abs((endPos.getZ() + zombie.getWidth() / 2.0) - (blockpos.getZ() + 0.5));

        // Return result and be MORE STRICT about the nodeEnd position in relation to the targetBlock
        if (xDistance <= HORIZONTAL_MINING_RANGE && zDistance <= HORIZONTAL_MINING_RANGE && yDistance <= VERTICAL_MINING_RANGE) {
            if (isBlockAccessibleFromDirection(world, blockpos, endNodeBlockPos)) {
                return true;
            }
        }
        return false;
    }


    public static boolean isBreakableBlock(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        try {
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            if (state.isAir() || state.getHardness(world, pos) < 0) {
                return false;
            }

            Identifier blockId = Registries.BLOCK.getId(block);

            // Direct ID check first (faster)
            for (String entry : breakableBlocks) {
                if (!entry.startsWith("#") &&
                        VersionHelper.CustomIdentifier(entry).equals(blockId)) {
                    return true;
                }
            }

            // Then check tags
            for (String entry : breakableBlocks) {
                if (entry.startsWith("#")) {
                    String tagPath = entry.substring(1);
                    TagKey<Block> tagKey = tagCache.computeIfAbsent(tagPath,
                            path -> TagKey.of(RegistryKeys.BLOCK, VersionHelper.CustomIdentifier(path)));
                    if (block.getDefaultState().isIn(tagKey)) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            LOGGER.error("Error checking block breakability at {}: {}", pos, e.getMessage());
            return false;
        }
    }

    private float getBlockStrength(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (hardness < 0) return 0.0F;
        return (2.0F / hardness / 250F) * MINING_SPEED;
    }

    @Override
    public void start() {
        if (debug) System.out.println("Goal started");
        zombie.getNavigation().stop();
        miningTicks = 0;
        breakProgress = 0;
        goalMap.put(zombie, this);
    }

    @Override
    public void stop() {
        World world = zombie.getWorld();

        if (world != null && targetBlock != null && zombie != null) {
            world.setBlockBreakingInfo(zombie.getId(), targetBlock, -1);
            currentlyMining.remove(targetBlock);
        }
        targetBlock = null;
        breakProgress = 0;
        goalMap.remove(zombie);
        if (debug) System.out.println("Goal stopped");
    }

    public static synchronized void resetMiningState(ZombieEntity zombie) {
        BlockBreakGoal goal = goalMap.get(zombie); // Get without removing first
        if (goal != null) {
            World world = zombie.getWorld();
            if (world != null && goal.targetBlock != null) {
                world.setBlockBreakingInfo(zombie.getId(), goal.targetBlock, -1);
                currentlyMining.remove(goal.targetBlock);
            }
            goal.targetBlock = null;
            goal.breakProgress = 0;
            goal.blockFindingIdleTicks = 0;
            goalMap.remove(zombie); // Remove after cleanup
            if (debug) System.out.println("Goal stopped externally");
        }
    }


    public void setAndValidateTargetAgain() {
        GoalSelector goalSelector = ((MobEntityAccessor) zombie).getGoalSelector();
        for (Goal goal : goalSelector.getGoals()) {
            if (goal instanceof KeepTargetGoal keepTargetGoal) {
                keepTargetGoal.canStart();
                if (debug) System.out.println("KeepTargetGoal canStart() executed!");
            }
        }
    }

    @Override
    public boolean shouldContinue() {

        if (debug && ((prevfailedBlockFindings < failedBlockFindings) || (blockFindingIdleTicks < prevblockFindingIdleTicks))) {
            System.out.println("shouldContinue() FBF: " + failedBlockFindings + " | idle Ticks block pathing: " + blockFindingIdleTicks);
            prevfailedBlockFindings = failedBlockFindings;
            prevblockFindingIdleTicks = blockFindingIdleTicks;
        }

        if (zombie == null || zombie.getWorld() == null) {
            return false;
        }

        if (zombie.getTarget() == null) {

            setAndValidateTargetAgain();

            //if still no target then exit
            if (zombie.getTarget() == null) return false;
        }


        Path path = zombie.getNavigation().findPathTo(zombie.getTarget(), 0);

        if (path == null) {
            noPathTicks++;
        }

        if (noPathTicks > 5) {
            if (debug) System.out.println("No path within 5 ticks");
            noPathTicks = 0;
            return false;
        }

        noPathTicks = 0;

        if (failedBlockFindings > 100) {
            failedBlockFindings = 0;
            if (debug) System.out.println("Too many failed blockfindings - stop goal");
            return false;
        }

        if (isPathingBetterThanBlockBreaking()) return false;

        if (path == null && noPathTicks < 5) return true;

        // Continue breaking current block if it exists and is still breakable
        if (path != null && !path.reachesTarget() && !path.isFinished()) {
            return true;
        }


        if (debug) {
            System.out.println(path);
            System.out.println("reachesTarget(): " + path.reachesTarget());
            System.out.println("isFinished(): " + path.isFinished());
            System.out.println("End Node: " + path.getEnd());
            System.out.println("Targetblock: " + targetBlock);
            System.out.println("Entity Target: " + zombie.getTarget());
            System.out.println("Should continue returning false");
        }

        return false;
    }

    private boolean isPathingBetterThanBlockBreaking() {
        // Get the cached path to target
        Path path = getCachedPathToTarget();

        // If there's no path, pathing is not a better option
        if (path == null || path.getEnd() == null || path.getLength() < 30 || zombie.getTarget().getBlockPos() == null) {
            return false;
        }

        // Get target position
        BlockPos targetPos = zombie.getTarget().getBlockPos();

        // If there's no target, pathing is not a better option
        if (targetPos == null) {
            return false;
        }

        // Get current zombie position
        BlockPos zombieBlockPos = zombie.getBlockPos();

        // Get end node of the path
        PathNode endNode = path.getEnd();
        Vec3d endPos = endNode.getPos();
        BlockPos nodeBlockPos = new BlockPos((int) Math.round(endPos.x), (int) Math.round(endPos.y), (int) Math.round(endPos.z));

        // Calculate distances
        double zombieDistanceToTarget = zombieBlockPos.getSquaredDistance(targetPos);
        double zombieDistanceFromEndNodeToTarget = nodeBlockPos.getSquaredDistance(targetPos);


        // Check if the path end is significantly closer to the target (by at least 10 blocks)
        // The path end is closer than 20% of the initial distance to the target
        // (distance before 100 blocks, if path completed then only 20 blocks, so start pathing

        if (zombieDistanceFromEndNodeToTarget < zombieDistanceToTarget * 0.2) {
            System.out.println("Stoping GOAL, path found to get closer");
            return true;
        }

        return false;
    }

    private Path getCachedPathToTarget() {
        LivingEntity target = zombie.getTarget();

        // No target or invalid target, no path
        if (target == null) {
            cachedPath = null;
            lastTargetPos = null;
            return null;
        }

        // Get current position
        BlockPos targetPos = target.getBlockPos();

        // Check if we need to update the path
        pathUpdateCounter++;

        if (pathUpdateCounter >= PATH_UPDATE_INTERVAL || cachedPath == null ||
                cachedPath.isFinished() ||
                (lastTargetPos != null && !lastTargetPos.equals(targetPos) &&
                        lastTargetPos.getSquaredDistance(targetPos) > 1.0)) {
            // Only update if moved significantly
            cachedPath = zombie.getNavigation().findPathTo(target, 0);
            pathUpdateCounter = 0;
            lastTargetPos = targetPos;
        }

        return cachedPath;
    }

    private void completeBlockBreak() {
        if (zombie == null || zombie.getWorld() == null || zombie.getWorld() == null || targetBlock == null) {
            return;
        }

        zombie.getWorld().breakBlock(targetBlock, true);
        zombie.getWorld().setBlockBreakingInfo(zombie.getId(), targetBlock, -1);

        currentlyMining.remove(targetBlock);
        targetBlock = null;

        breakProgress = 0;
        failedBlockFindings = 0;
        blockFindingIdleTicks = 0;

    }

    @Override
    public void tick() {

        //Sanity Checks

        World world = zombie.getWorld();

        if (zombie == null || world == null || !zombie.isAlive()) {
            stop();
            return;
        }

        if (settings.getTargetBlockPathingParticlesEnabled()) {
            visualizePathToBlock(targetBlock);
        }

        //Find INITIAL OR NEW Block
        if (targetBlock == null) {

            targetBlock = findBlockToBreak();

            if (targetBlock != null) {
                currentlyMining.add(targetBlock);
                miningTicks = 0;
                breakProgress = 0;
                blockFindingIdleTicks = 0;
            } else {

                //Farblock is mined and no other targetBlock is found -> chase Target

                Path path = getCachedPathToTarget();

                if (path != null) {
                    zombie.getNavigation().startMovingAlong(path, 1.0);
                    zombie.lookAtEntity(zombie.getTarget(), 1.0F, 1.0F);
                }

                return;
            }
        }

        //If Block is not existing anymore, COMPLETE blockbreak and find new block
        if (zombie.getWorld().

                getBlockState(targetBlock).

                isAir()) {
            completeBlockBreak();
            return;
        }

        //select NEW block if general pathfinding does take too long or zombie is idle too long during block pathing
        if ((blockFindingIdleTicks > MAX_IDLE_TICKS_DURING_BLOCK_FINDING) && targetBlock != null) {

            currentlyMining.remove(targetBlock);
            targetBlock = null;

            if (debug) System.out.println("Zombie Idle -> select new block");

            targetBlock = findBlockToBreak();

            if (targetBlock != null) {
                currentlyMining.add(targetBlock);
                miningTicks = 0;
                breakProgress = 0;
                blockFindingIdleTicks = 0;
            }
            return;

        }

        //TargetBlock accquired -> Block sanity checks

        if (targetBlock != null && !world.isChunkLoaded(targetBlock.getX() >> 4, targetBlock.getZ() >> 4)) {
            stop();
            return;
        }

        if (world.getBlockState(targetBlock).

                getHardness(world, targetBlock) <= 0) {
            stop();
            return;
        }


        //Spawn GREEN particles at TargetBlock for debugging
        if (settings.getTargetBlockParticlesEnabled()) {
            spawnTargetBlockParticles(world);
        }

        if (isWithinMiningRange(targetBlock)) {
            performMining(world);
        } else {
            navigateToTargetBlock();
        }

    }


    private void performMining(World world) {

        zombie.getNavigation().stop();

        if (!zombie.getNavigation().isIdle() || zombie.getNavigation().isFollowingPath()) {
            zombie.setVelocity(0, zombie.getVelocity().y, 0);
        }

        failedBlockFindings = 0;

        // Look at block while mining
        zombie.getLookControl().lookAt(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);

        // Mining animation and progress
        zombie.swingHand(Hand.MAIN_HAND);
        zombie.swingHand(Hand.OFF_HAND);
        miningTicks++;

        float strength = getBlockStrength(world, targetBlock);
        breakProgress += strength;

        // Update block breaking progress
        int progress = (int) (breakProgress * 10.0F);
        world.setBlockBreakingInfo(zombie.getId(), targetBlock, progress);

        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(targetBlock)),
                    targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5,
                    20, // more particles
                    0.25, 0.25, 0.25, // spread
                    0.05 // speed
            );
        }


        if (miningTicks % 2 == 0) {
            BlockState blockState = world.getBlockState(targetBlock);
            BlockSoundGroup soundGroup = blockState.getSoundGroup();
            float volume = 0.7F;
            float pitch = 0.4F + world.random.nextFloat() * 0.4F;

            world.playSound(
                    null,
                    targetBlock.getX() + 0.5,
                    targetBlock.getY() + 0.5,
                    targetBlock.getZ() + 0.5,
                    soundGroup.getHitSound(),
                    SoundCategory.PLAYERS,
                    volume,
                    pitch
            );

        }

        if (breakProgress >= 1.0F) {

            completeBlockBreak();
        }

    }


    private void spawnTargetBlockParticles(World world) {
        if (targetBlock != null && world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5,
                    30, 0.3, 0.3, 0.3, 0.0
            );
        }
    }

    private boolean isWithinMiningRange(BlockPos blockpos) {
        if (blockpos == null) return false;

        // Check if block is within range before doing expensive calculations
        BlockPos zombiePos = zombie.getBlockPos();
        int xDiff = Math.abs(blockpos.getX() - zombiePos.getX());
        int yDiff = Math.abs(blockpos.getY() - zombiePos.getY());
        int zDiff = Math.abs(blockpos.getZ() - zombiePos.getZ());

        // If block is clearly out of maximum possible range, return early
        if (xDiff > HORIZONTAL_MINING_RANGE + 2 ||
                zDiff > HORIZONTAL_MINING_RANGE + 2 ||
                yDiff > VERTICAL_MINING_RANGE + 2) {
            return false;
        }

        double zombieCenterX = zombie.getX() + zombie.getWidth() / 2.0;
        double zombieCenterY = zombie.getY() + zombie.getHeight() / 2.0;
        double zombieCenterZ = zombie.getZ() + zombie.getWidth() / 2.0;

        double targetX = blockpos.getX() + 0.5;
        double targetY = blockpos.getY() + 0.5;
        double targetZ = blockpos.getZ() + 0.5;


        double xDistance = Math.abs(targetX - zombieCenterX);
        double yDistance = Math.abs(targetY - zombieCenterY);
        double zDistance = Math.abs(targetZ - zombieCenterZ);

        // Define separate ranges for horizontal (X,Z) and vertical (Y) axes
        double effectiveHorizontalRange = HORIZONTAL_MINING_RANGE + (miningTicks > 0 ? 1.0 : 0.0);
        double effectiveVerticalRange = VERTICAL_MINING_RANGE + (miningTicks > 0 ? 1.0 : 0.0);

        return xDistance <= effectiveHorizontalRange &&
                zDistance <= effectiveHorizontalRange &&
                yDistance <= effectiveVerticalRange;
    }

    private void navigateToTargetBlock() {

        double targetX = targetBlock.getX() + 0.5;
        double targetY = targetBlock.getY() + 0.5;
        double targetZ = targetBlock.getZ() + 0.5;

        zombie.getNavigation().startMovingTo(targetX, targetY, targetZ, 1.0);
        zombie.getLookControl().lookAt(targetX, targetY, targetZ);


        //check if zombie is idle / not moving during pathing to the targetblock
        if ((zombie.getNavigation().isIdle() || !zombie.getNavigation().isFollowingPath()) && targetBlock != null) {
            blockFindingIdleTicks++;
        } else {
            blockFindingIdleTicks = Math.max(0, blockFindingIdleTicks - 1);
        }

    }


    public void visualizePathToBlock(BlockPos targetBlockPos) {
        if (targetBlockPos != null) {
            MobNavigation navigation = (MobNavigation) zombie.getNavigation();
            Path path = navigation.findPathTo(targetBlockPos, 0); // Create path to the target

            if (path != null && path.getEnd() != null) {

                ServerWorld serverWorld = (ServerWorld) zombie.getWorld();


                // End node (green) - happy villager particles
                PathNode endNode = path.getEnd();
                BlockPos endPos = new BlockPos(
                        (int) endNode.getPos().getX(),
                        (int) endNode.getPos().getY(),
                        (int) endNode.getPos().getZ());
                serverWorld.spawnParticles(
                        ParticleTypes.FLAME,
                        endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5,
                        10, 0.2, 0.2, 0.2, 0.0
                );

                // Path nodes (blue) - soul fire flame particles
                int nodeCount = path.getLength();
                for (int i = 0; i < nodeCount; i++) {
                    PathNode node = path.getNode(i);
                    BlockPos nodePos = new BlockPos(
                            (int) node.getPos().getX(),
                            (int) node.getPos().getY(),
                            (int) node.getPos().getZ());
                    serverWorld.spawnParticles(
                            ParticleTypes.SOUL_FIRE_FLAME,
                            nodePos.getX() + 0.5, nodePos.getY() + 0.5, nodePos.getZ() + 0.5,
                            5, 0.1, 0.1, 0.1, 0.0
                    );
                }
            }
        }
    }

}