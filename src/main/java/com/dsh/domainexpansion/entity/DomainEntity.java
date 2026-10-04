package com.dsh.domainexpansion.entity;

import com.dsh.domainexpansion.DomainConfig;
import com.dsh.domainexpansion.domain.Barrier;
import com.dsh.domainexpansion.domain.SphereShape;
import com.dsh.domainexpansion.registry.ModBlocks;
import com.dsh.domainexpansion.registry.ModEntities;
import com.gametechbc.traveloptics.entity.projectiles.RainfallAoe;
import com.gametechbc.traveloptics.entity.projectiles.overflow.FloodPoolEntity;
import com.gametechbc.traveloptics.util.TravelopticsParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The domain sphere itself.
 *
 * The whole approach is modelled on Cursed Fate's domain, which is the one implementation
 * known to render and run correctly in the target pack. The ideas taken from it, and why:
 *
 *  - The interior is neither deleted nor left alone; it is replaced with a dedicated
 *    invisible block ({@code domain_air}). That block is air in every way that matters
 *    (no shape, no collision, no occlusion) but it is a real block, so the terrain it
 *    displaced can be recorded and put back exactly. Nothing about the world is destroyed.
 *  - The build runs in two phases, a growing radius and then a growing height, so the cost
 *    is spread over many ticks and the sphere visibly grows out of the ground.
 *  - Placement uses update flag 2 (clients only). Neighbour updates are pointless for a
 *    shell nothing interacts with, and skipping them is a large saving.
 *  - Positions that would become the invisible filler while already being air are skipped
 *    entirely: nothing to change, nothing to remember. Over open ground that is the
 *    overwhelming majority of the sphere, which is what makes a radius 30 domain
 *    affordable at all.
 *
 * One deliberate departure: the lower half of the sphere is not filled. Cursed Fate fills
 * it to keep players out of caves below, but the floor layer already provides support and
 * skipping the lower half removes roughly half the work.
 */
public class DomainEntity extends Entity {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final List<DomainEntity> ACTIVE = new ArrayList<>();

    /** Cursed Fate restores 1000 blocks per tick; the same figure works here. */
    private static final int RESTORE_PER_TICK = 1000;

    /** Gameplay work - barrier, support spells - runs once a second. */
    private static final int PERIODIC_INTERVAL = 20;

    /** Wall-clock ceiling for one tick's placement, so a slow machine cannot stall. */
    private static final double PLACE_MILLIS_PER_TICK = 25.0;

    /** Clients only, matching Cursed Fate. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /**
     * The redundant particle layer drawn over the finished wall.
     *
     * Block rendering has failed silently in this pack more than once while particles were
     * always visible, so the boundary is drawn twice: real blocks, which are what make the
     * domain a barrier, and a sparse particle shell on the same surface. If the wall draws,
     * this reads as sparkle; if it does not, this is the only thing you can see.
     */
    private static final int PARTICLE_POINTS = 900;
    private static final int PARTICLES_PER_TICK = 60;

    private enum Phase {
        NONE,
        OUTWARD,
        VERTICAL,
        COMPLETE
    }

    /** A displaced block, with the block entity data it carried, so it can be put back. */
    private record BlockData(BlockState state, CompoundTag nbt) {
    }

    public static List<DomainEntity> activeDomains() {
        return ACTIVE;
    }

    public static DomainEntity activeFor(LivingEntity caster) {
        for (DomainEntity d : ACTIVE) {
            if (!d.isRemoved() && caster.getUUID().equals(d.ownerUuid)) {
                return d;
            }
        }
        return null;
    }

    private UUID ownerUuid;
    private int spellLevel = 1;
    private int lifeTicks;

    /** Everything this domain has written, and what was there before. */
    private final Map<BlockPos, BlockData> originalBlocks = new HashMap<>(1 << 16, 0.75F);

    private BlockPos domainCenter;
    private Phase phase = Phase.NONE;
    private int currentRadius;
    private int currentVerticalY = 1;
    private List<BlockPos> blocksToPlace = new ArrayList<>();
    private int blockPlaceIndex;

    private boolean restoring;
    private boolean discardAfterRestore;
    private List<BlockPos> blocksToRestore = new ArrayList<>();
    private int blockRestoreIndex;

    private BlockState shellState;
    private BlockState floorState;
    private BlockState airState;

    /** Real time the finished sphere has stood, and when the previous tick started. */
    private long domainMillis;
    private long lastTickMillis;
    private boolean buildComplete;

    /** Set when the entity is loaded back in without the data needed to undo itself. */
    private boolean needsRecovery;

    /** True once {@link #initialize} has wired this domain up. */
    private boolean initialized;

    /** The particle shell drawn over the finished wall. */
    private List<Vec3> particlePoints = List.of();
    private int particleCursor;

    private FloodPoolEntity overflow;
    private RainfallAoe rainfall;
    private final Set<UUID> captured = new HashSet<>();

    /** Running totals of barrier corrections, so the log can prove the barrier is working. */
    private int barrierHeldInside;
    private int barrierKeptOut;

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public DomainEntity(Level level, LivingEntity owner, int spellLevel) {
        this(ModEntities.DOMAIN.get(), level);
        this.ownerUuid = owner.getUUID();
        this.spellLevel = spellLevel;
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    public int getSpellLevel() {
        return spellLevel;
    }

    public Vec3 center() {
        return domainCenter != null
                ? new Vec3(domainCenter.getX() + 0.5D, domainCenter.getY() + 0.5D, domainCenter.getZ() + 0.5D)
                : position();
    }

    public double radius() {
        return DomainConfig.DOMAIN_RADIUS;
    }

    /** The radius as a whole number of blocks, which is all the geometry ever needs. */
    private static int radiusBlocks() {
        return (int) DomainConfig.DOMAIN_RADIUS;
    }

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level() instanceof ServerLevel server) {
            initialize(server);
        }
    }

    /**
     * Wires the domain up and starts its build. Idempotent.
     *
     * Called explicitly by the spell right after the entity is added, and again from
     * {@code onAddedToWorld}; whichever happens first does the work. Registration with the
     * tick handler lives here, so if this step were ever skipped the spell would appear to do
     * nothing at all - the failure mode that cost several rounds of guessing - and an
     * explicit call from the code that creates the entity removes any dependence on when the
     * loader chooses to fire its callback.
     */
    public void initialize(ServerLevel server) {
        if (initialized || level() != server) {
            return;
        }
        initialized = true;
        ACTIVE.add(this);
        domainCenter = blockPosition();
        beginBuild();
        captureInitialEntities(server);
        LOGGER.info("[DomainExpansion] domain opened at {} level={} radius={} captured={}",
                position(), spellLevel, DomainConfig.DOMAIN_RADIUS, captured.size());
    }

    private void beginBuild() {
        phase = Phase.OUTWARD;
        currentRadius = 0;
        currentVerticalY = 1;
        blocksToPlace = new ArrayList<>();
        blockPlaceIndex = 0;
        shellState = ModBlocks.DOMAIN_SHELL.get().defaultBlockState();
        floorState = ModBlocks.DOMAIN_FLOOR.get().defaultBlockState();
        airState = ModBlocks.DOMAIN_AIR.get().defaultBlockState();

        Vec3 c = center();
        List<Vec3> points = new ArrayList<>();
        for (SphereShape.Offset o : SphereShape.shellSample(radiusBlocks(), PARTICLE_POINTS)) {
            points.add(new Vec3(c.x + o.x(), c.y + o.y(), c.z + o.z()));
        }
        particlePoints = points;
        particleCursor = 0;
    }

    private void captureInitialEntities(ServerLevel server) {
        Vec3 c = center();
        AABB box = new AABB(c, c).inflate(radius());
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!target.getUUID().equals(ownerUuid) && contains(target)) {
                captured.add(target.getUUID());
            }
        }
    }

    public boolean contains(Entity entity) {
        return entity.position().distanceToSqr(center()) <= radius() * radius();
    }

    @Override
    public void tick() {
        // work is driven from DomainTickHandler, so the domain keeps running even though
        // the entity has no AI and never moves
    }

    /** Advances the domain one server tick. */
    public void advance(ServerLevel server) {
        // Hard guard: never write into a level other than the one this domain lives in,
        // whatever the caller passes. The tick event fires once per loaded level and the
        // active list is global, so without this a domain in the overworld would have its
        // blocks written into the nether at the same coordinates.
        if (level() != server) {
            return;
        }

        lifeTicks++;

        long now = System.currentTimeMillis();
        if (buildComplete && lastTickMillis != 0L) {
            domainMillis += Math.min(now - lastTickMillis, DomainConfig.MAX_TICK_MILLIS_CREDIT);
        }
        lastTickMillis = now;

        if (restoring) {
            restoreStep(server);
            return;
        }
        if (needsRecovery) {
            recover(server);
            return;
        }
        if (!initialized) {
            // Belt and braces: only initialize() adds to the active list, so reaching here
            // uninitialised should be impossible, and finishing the setup is better than
            // dereferencing a null centre below.
            initialize(server);
            return;
        }

        if (lifeTicks % 100 == 0) {
            LOGGER.info("[DomainExpansion] t={}s phase={} r={} y={} written={} captured={}",
                    lifeTicks / 20, phase, currentRadius, currentVerticalY, originalBlocks.size(), captured.size());
        }

        if (lifeTicks % PERIODIC_INTERVAL == 0) {
            maintainBarrier(server);
            maintainSupportSpells(server);
        }

        if (buildComplete) {
            emitBoundaryParticles(server);
            if (domainMillis >= DomainConfig.DOMAIN_DURATION_MILLIS) {
                LOGGER.info("[DomainExpansion] domain closing after {}s of real time", domainMillis / 1000L);
                closeDomain();
            }
            return;
        }

        buildStep(server);
    }

    /**
     * Emits the next slice of the particle shell. Only runs once the wall is finished, so it
     * never gives away the shape before the blocks have grown into it.
     */
    private void emitBoundaryParticles(ServerLevel server) {
        if (particlePoints.isEmpty()) {
            return;
        }
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            Vec3 p = particlePoints.get(particleCursor);
            particleCursor = (particleCursor + 1) % particlePoints.size();
            server.sendParticles(TravelopticsParticleHelper.WATER_SPARKS,
                    p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // the two phase build
    // ------------------------------------------------------------------

    private void buildStep(ServerLevel server) {
        switch (phase) {
            case OUTWARD -> buildOutward(server);
            case VERTICAL -> buildVertical(server);
            default -> {
            }
        }
    }

    private void buildOutward(ServerLevel server) {
        if (blocksToPlace.isEmpty()) {
            currentRadius++;
            if (currentRadius > DomainConfig.DOMAIN_RADIUS) {
                phase = Phase.VERTICAL;
                LOGGER.info("[DomainExpansion] outward phase done at t={}s", lifeTicks / 20);
                return;
            }
            blocksToPlace = toPositions(SphereShape.outwardStep(currentRadius));
            blockPlaceIndex = 0;
        }

        float progress = (float) currentRadius / DomainConfig.DOMAIN_RADIUS;
        placeBatch(server, (int) (500.0F + 800.0F * progress));

        if (blockPlaceIndex >= blocksToPlace.size()) {
            blocksToPlace.clear();
        }
    }

    private void buildVertical(ServerLevel server) {
        if (blocksToPlace.isEmpty()) {
            if (currentVerticalY > DomainConfig.DOMAIN_RADIUS) {
                phase = Phase.COMPLETE;
                buildComplete = true;
                lastTickMillis = System.currentTimeMillis();
                domainMillis = 0L;
                LOGGER.info("[DomainExpansion] sphere complete at t={}s, {} blocks written; {}s real time start now",
                        lifeTicks / 20, originalBlocks.size(), DomainConfig.DOMAIN_DURATION_MILLIS / 1000L);
                return;
            }
            blocksToPlace = toPositions(SphereShape.verticalStep(currentVerticalY, radiusBlocks()));
            blockPlaceIndex = 0;
            currentVerticalY++;
        }

        float progress = (float) currentVerticalY / DomainConfig.DOMAIN_RADIUS;
        placeBatch(server, (int) (2000.0F + 1300.0F * progress));

        if (blockPlaceIndex >= blocksToPlace.size()) {
            blocksToPlace.clear();
        }
    }

    private List<BlockPos> toPositions(List<SphereShape.Offset> offsets) {
        List<BlockPos> positions = new ArrayList<>(offsets.size());
        for (SphereShape.Offset o : offsets) {
            positions.add(domainCenter.offset(o.x(), o.y(), o.z()));
        }
        return positions;
    }

    private void placeBatch(ServerLevel server, int budget) {
        long deadline = System.nanoTime() + (long) (PLACE_MILLIS_PER_TICK * 1_000_000.0);
        while (budget-- > 0 && blockPlaceIndex < blocksToPlace.size() && System.nanoTime() < deadline) {
            placeBlock(server, blocksToPlace.get(blockPlaceIndex++));
        }
    }

    /**
     * Writes one position of the sphere.
     *
     * The state comes from the position's offset from the centre, exactly as Cursed Fate
     * does it: the level below the centre is the floor, the outer one block band is the
     * visible wall, and everything else inside is the invisible filler.
     *
     * A position that would become filler and is already air is skipped outright: there is
     * nothing to change and nothing that would need restoring. Over open ground that skips
     * almost the whole sphere.
     */
    private void placeBlock(ServerLevel server, BlockPos pos) {
        int relX = pos.getX() - domainCenter.getX();
        int relY = pos.getY() - domainCenter.getY();
        int relZ = pos.getZ() - domainCenter.getZ();

        SphereShape.Placement placement = SphereShape.classify(relX, relY, relZ, radiusBlocks());
        BlockState target = switch (placement) {
            case FLOOR -> floorState;
            case SHELL -> shellState;
            case FILLER -> airState;
        };

        BlockState current = server.getBlockState(pos);
        if (current.is(target.getBlock())) {
            return;
        }
        if (placement == SphereShape.Placement.FILLER && current.isAir()) {
            return;
        }

        saveOriginal(server, pos);

        BlockEntity existing = server.getBlockEntity(pos);
        if (existing != null) {
            server.removeBlockEntity(pos);
        }
        server.setBlock(pos, target, PLACE_FLAGS);
    }

    private void saveOriginal(ServerLevel server, BlockPos pos) {
        if (originalBlocks.containsKey(pos)) {
            return;
        }
        BlockState state = server.getBlockState(pos);
        CompoundTag nbt = null;
        if (!state.isAir() && state.hasBlockEntity()) {
            BlockEntity be = server.getBlockEntity(pos);
            if (be != null) {
                nbt = be.saveWithoutMetadata();
            }
        }
        originalBlocks.put(pos.immutable(), new BlockData(state, nbt));
    }

    // ------------------------------------------------------------------
    // closing and restoring
    // ------------------------------------------------------------------

    private void closeDomain() {
        if (restoring) {
            return;
        }
        restoring = true;
        discardAfterRestore = true;
        dismissSupportSpells();
        captured.clear();
        blocksToRestore = new ArrayList<>(originalBlocks.keySet());

        // outermost first, so the ground under the player is the last thing to return and
        // the domain never opens up beneath them part way through
        BlockPos c = domainCenter != null ? domainCenter : blockPosition();
        blocksToRestore.sort(Comparator.comparingInt((BlockPos p) -> {
            int dx = p.getX() - c.getX();
            int dy = p.getY() - c.getY();
            int dz = p.getZ() - c.getZ();
            return dx * dx + dy * dy + dz * dz;
        }).reversed());

        blockRestoreIndex = 0;
        phase = Phase.NONE;
        LOGGER.info("[DomainExpansion] restoring {} blocks over ~{} ticks",
                blocksToRestore.size(), 1 + blocksToRestore.size() / RESTORE_PER_TICK);
    }

    private void restoreStep(ServerLevel server) {
        int budget = RESTORE_PER_TICK;
        while (budget-- > 0 && blockRestoreIndex < blocksToRestore.size()) {
            restoreOne(server, blocksToRestore.get(blockRestoreIndex++));
        }
        if (blockRestoreIndex >= blocksToRestore.size()) {
            LOGGER.info("[DomainExpansion] restored {} blocks", blocksToRestore.size());
            originalBlocks.clear();
            blocksToRestore.clear();
            blockRestoreIndex = 0;
            restoring = false;
            ACTIVE.remove(this);
            if (discardAfterRestore) {
                discardAfterRestore = false;
                discard();
            }
        }
    }

    private void restoreOne(ServerLevel server, BlockPos pos) {
        BlockData data = originalBlocks.get(pos);
        if (data == null || data.state() == null) {
            return;
        }
        BlockEntity existing = server.getBlockEntity(pos);
        if (existing != null) {
            server.removeBlockEntity(pos);
        }
        server.setBlock(pos, data.state(), PLACE_FLAGS);
        if (data.nbt() != null) {
            BlockEntity be = server.getBlockEntity(pos);
            if (be != null) {
                be.load(data.nbt());
                be.setChanged();
            }
        }
    }

    /** Restores everything at once; only used when the entity is going away for good. */
    private void restoreAllNow(ServerLevel server) {
        for (Map.Entry<BlockPos, BlockData> entry : originalBlocks.entrySet()) {
            restoreOne(server, entry.getKey());
        }
        originalBlocks.clear();
        blocksToRestore.clear();
        restoring = false;
    }

    /**
     * Cleanup for an entity that came back from a save without its record of what it
     * displaced - that record is deliberately not serialised, since it can hold tens of
     * thousands of entries. Best effort: any sphere block still standing goes back to air,
     * so no permanent shell is left in the world.
     */
    private void recover(ServerLevel server) {
        needsRecovery = false;
        if (shellState == null) {
            shellState = ModBlocks.DOMAIN_SHELL.get().defaultBlockState();
            floorState = ModBlocks.DOMAIN_FLOOR.get().defaultBlockState();
            airState = ModBlocks.DOMAIN_AIR.get().defaultBlockState();
        }
        int cleaned = 0;
        if (domainCenter != null) {
            int r = (int) DomainConfig.DOMAIN_RADIUS;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = -1; y <= r; y++) {
                        cursor.set(domainCenter.getX() + x, domainCenter.getY() + y, domainCenter.getZ() + z);
                        BlockState state = server.getBlockState(cursor);
                        if (state.is(shellState.getBlock()) || state.is(floorState.getBlock())
                                || state.is(airState.getBlock())) {
                            server.setBlock(cursor, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
                            cleaned++;
                        }
                    }
                }
            }
        }
        LOGGER.warn("[DomainExpansion] recovered a reloaded domain: cleared {} leftover blocks", cleaned);
        ACTIVE.remove(this);
        discard();
    }

    /** Called by the spell when the caster recasts while a domain is already open. */
    public void closeForRecast(ServerLevel server) {
        LOGGER.info("[DomainExpansion] closing existing domain for recast (open {}s)", lifeTicks / 20);
        closeDomain();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            if (!originalBlocks.isEmpty() && !restoring) {
                LOGGER.info("[DomainExpansion] removing domain ({}) - restoring {} blocks immediately",
                        reason, originalBlocks.size());
                restoreAllNow(server);
            }
            dismissSupportSpells();
            ACTIVE.remove(this);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------
    // barrier and support spells
    // ------------------------------------------------------------------

    /**
     * Keeps everything that was inside at cast time inside, and everything that was outside
     * out. The caster is always free to come and go.
     *
     * The counts are logged because this is otherwise completely silent: a working barrier
     * and a barrier whose teleports never fire look identical in the log, and "the mob walked
     * through it" cannot be told apart from "the mob was never in the sphere" without them.
     * The method only runs once a second, so this cannot flood.
     */
    private void maintainBarrier(ServerLevel server) {
        Vec3 c = center();
        double radius = radius();
        int heldInside = 0;
        int keptOut = 0;

        AABB box = new AABB(c, c).inflate(radius + 2.0D);
        for (LivingEntity entity : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getUUID().equals(ownerUuid)) {
                continue;
            }
            Vec3 offset = entity.position().subtract(c);
            double dist = offset.length();
            if (dist < 1.0E-4D) {
                continue;
            }
            boolean isCaptured = captured.contains(entity.getUUID());
            // the arithmetic lives in Barrier, where it is unit tested: a teleport either
            // happens or it does not, so it cannot be confirmed from a log
            double corrected = Barrier.correctedDistance(dist, isCaptured, radius);
            if (corrected <= 0.0D) {
                continue;
            }
            Vec3 target = c.add(offset.scale(corrected / dist));
            entity.teleportTo(server, target.x, target.y, target.z,
                    Set.of(), entity.getYRot(), entity.getXRot());
            if (isCaptured) {
                heldInside++;
            } else {
                keptOut++;
            }
        }

        if (heldInside > 0 || keptOut > 0) {
            barrierHeldInside += heldInside;
            barrierKeptOut += keptOut;
            LOGGER.info("[DomainExpansion] barrier at t={}s: {} held inside, {} kept out (totals {} / {})",
                    lifeTicks / 20, heldInside, keptOut, barrierHeldInside, barrierKeptOut);
        }
    }

    /**
     * Keeps Overflow and Rainfall up for the whole domain. A direct reference is held and
     * the entity is only replaced once it is genuinely gone; testing "is it alive" was
     * false for an entity that had just been added, which spawned a new pair every tick.
     */
    private void maintainSupportSpells(ServerLevel server) {
        Entity ownerEntity = ownerUuid != null ? server.getEntity(ownerUuid) : null;
        if (!(ownerEntity instanceof LivingEntity caster) || !caster.isAlive()) {
            return;
        }

        if (overflow == null || overflow.isRemoved()) {
            overflow = new FloodPoolEntity(server);
            overflow.setOwner(caster);
            overflow.setRadius(5.0F + spellLevel * 5.0F);
            overflow.setDuration(DomainConfig.DOMAIN_DURATION_TICKS);
            overflow.setCircular();
            overflow.setPos(center().x, center().y, center().z);
            server.addFreshEntity(overflow);
            // logged because the support spells are otherwise invisible in a log, and their
            // absence would be indistinguishable from a working domain in every other line
            LOGGER.info("[DomainExpansion] overflow active at t={}s radius={} level={}",
                    lifeTicks / 20, overflow.getRadius(), spellLevel);
        }

        // Rainfall discards itself on a fixed cadence, so it is re-summoned to keep up
        if (rainfall == null || rainfall.isRemoved()) {
            rainfall = new RainfallAoe(server);
            rainfall.setOwner(caster);
            rainfall.setRadius(DomainConfig.DOMAIN_RADIUS);
            rainfall.setDuration(DomainConfig.DOMAIN_DURATION_TICKS);
            rainfall.setCircular();
            rainfall.setPos(center().x, center().y, center().z);
            server.addFreshEntity(rainfall);
            LOGGER.info("[DomainExpansion] rainfall active at t={}s radius={} level={}",
                    lifeTicks / 20, rainfall.getRadius(), spellLevel);
        }
    }

    private void dismissSupportSpells() {
        if (overflow != null) {
            overflow.discard();
            overflow = null;
        }
        if (rainfall != null) {
            rainfall.discard();
            rainfall = null;
        }
    }

    // ------------------------------------------------------------------
    // entity plumbing
    // ------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        lifeTicks = tag.getInt("LifeTicks");
        domainMillis = tag.getLong("DomainMillis");
        spellLevel = tag.getInt("SpellLevel");
        if (tag.hasUUID("DomainOwner")) {
            ownerUuid = tag.getUUID("DomainOwner");
        }
        if (tag.contains("DomainCenter")) {
            domainCenter = NbtUtils.readBlockPos(tag.getCompound("DomainCenter"));
        }
        if (tag.contains("Captured")) {
            captured.clear();
            ListTag list = tag.getList("Captured", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                captured.add(list.getCompound(i).getUUID("Id"));
            }
        }
        if (tag.getBoolean("WasActive") && originalBlocks.isEmpty()) {
            needsRecovery = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", lifeTicks);
        tag.putLong("DomainMillis", domainMillis);
        tag.putInt("SpellLevel", spellLevel);
        if (ownerUuid != null) {
            tag.putUUID("DomainOwner", ownerUuid);
        }
        if (domainCenter != null) {
            tag.put("DomainCenter", NbtUtils.writeBlockPos(domainCenter));
        }
        tag.putBoolean("WasActive", !originalBlocks.isEmpty() || !buildComplete);
        ListTag list = new ListTag();
        for (UUID id : captured) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            list.add(entry);
        }
        tag.put("Captured", list);
    }
}
