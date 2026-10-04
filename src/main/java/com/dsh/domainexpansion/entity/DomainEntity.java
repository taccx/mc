package com.dsh.domainexpansion.entity;

import com.dsh.domainexpansion.DomainConfig;
import com.dsh.domainexpansion.domain.Barrier;
import com.dsh.domainexpansion.domain.DomainKind;
import com.dsh.domainexpansion.domain.Guidance;
import com.dsh.domainexpansion.domain.SphereShape;
import com.dsh.domainexpansion.registry.ModBlocks;
import com.dsh.domainexpansion.registry.ModEntities;
import com.dsh.domainexpansion.entity.SlashLineEntity;
import com.dsh.domainexpansion.spell.CrimsonSlash;
import com.dsh.domainexpansion.spell.SlashLines;
import com.dsh.domainexpansion.spell.SupportSpells;
import com.gametechbc.traveloptics.entity.projectiles.RainfallAoe;
import com.gametechbc.traveloptics.entity.projectiles.overflow.FloodPoolEntity;
import com.gametechbc.traveloptics.util.TravelopticsParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
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
     * How far a guided projectile turns towards its target each tick, as a fraction of its
     * velocity. Low enough that the flight path visibly curves rather than snapping onto the
     * target the instant anything enters the domain.
     */
    private static final double GUIDANCE_TURN = 0.30D;

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

    /**
     * Which domain this is. Saved with the entity, so a domain that survives a reload keeps its
     * own blocks rather than restoring Aqua ones over everything.
     */
    private DomainKind kind = DomainKind.AQUA;
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

    /** How many projectile course corrections have been made, reported in the heartbeat. */
    private int projectilesGuided;

    /** The particle shell drawn over the finished wall. */
    private List<Vec3> particlePoints = List.of();
    private int particleCursor;

    private FloodPoolEntity overflow;
    private RainfallAoe rainfall;
    private final Set<UUID> captured = new HashSet<>();

    /**
     * Slash lines currently alive. The lines are textured entities now rather than particles, so
     * the domain keeps track of them to hold {@link SlashLines#MAX_LIVE_LINES}; they tick and
     * expire on their own.
     */
    private final List<SlashLineEntity> slashLines = new ArrayList<>();

    /**
     * Lines started in the sphere every second, whether or not anything is being cut, so a domain
     * with nothing in it still has something moving across it.
     *
     * Three hundred, doubled from a hundred and fifty once the effect was seen in play, and spent
     * through the same allocator as the slash rate. Three hundred divides into twenty ticks
     * cleanly - fifteen a tick - so the allocator has nothing to spread, but it stays in use so
     * that changing this number to one that does not divide cannot silently round the rate down.
     *
     * These are now the only lines drawn: the per-victim ones were removed, so the automatic
     * attack is damage and blood with no entity of its own. They still share
     * {@link SlashLines#MAX_LIVE_LINES}.
     */
    private static final int AMBIENT_LINES_PER_SECOND = 300;

    /**
     * Half-angle of the cone the ambient lines are placed in, in degrees.
     *
     * Sixty, sized to a field of view setting of ninety degrees. That setting is the vertical
     * field of view, and on a 16:9 screen a ninety degree vertical field is about a hundred and
     * twenty-one degrees horizontally, so the horizontal half-angle is about sixty. Matching the
     * horizontal extent rather than the vertical is what keeps lines all the way to the sides of
     * the screen instead of stopping short of them.
     *
     * The client's actual field of view is a setting the server is never told, so this is an
     * estimate and the reason the placement is a bias rather than a filter. A player on the
     * default seventy-six vertical would want about forty-five here; on a hundred and ten, about
     * sixty-six.
     */
    private static final double VIEW_CONE_DEGREES = 60.0D;

    /**
     * How close to the eye a line may be placed, in blocks.
     *
     * Four, so nothing spawns in the player's face. At a third of a block wide, a line at arm's
     * length would fill the screen for its whole life.
     */
    private static final double VIEW_MIN_DISTANCE = 4.0D;

    /** Strikes landed, reported in the heartbeat so the rate can be checked. */
    private int slashHits;
    /** Blood bursts actually spattered. */
    private int bloodBursts;
    /** Blood bursts dropped by the global per-tick ceiling. */
    private int bloodBurstsSkipped;
    /** Slash lines actually started. */
    private int slashLineSpawns;
    /** Slash lines dropped because the live ceiling was reached. */
    private int slashLineSkipped;

    /** Running totals of barrier corrections, so the log can prove the barrier is working. */
    private int barrierHeldInside;
    private int barrierKeptOut;

    /**
     * Which announcement has been shown: 0 = none yet, 1 = the name, 2 = the epithet.
     *
     * Sent as ordinary vanilla title packets rather than anything custom, so it lands in the
     * middle of the screen exactly like a /title command does - which is what was asked for.
     */
    private int titleStage;

    /** The name is on screen for this long before the epithet follows it. */
    private static final int TITLE_NAME_STAY = 35;
    private static final int TITLE_EPITHET_AT = TITLE_NAME_STAY + 15;

    public DomainEntity(EntityType<? extends DomainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public DomainEntity(Level level, LivingEntity owner, int spellLevel, DomainKind kind) {
        this(ModEntities.DOMAIN.get(), level);
        this.ownerUuid = owner.getUUID();
        this.spellLevel = spellLevel;
        this.kind = kind;
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    public DomainKind kind() {
        return kind;
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
        shellState = kind.shellState();
        floorState = kind.floorState();
        airState = kind.airState();

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
        return containsPoint(entity.position());
    }

    /** The same test for a bare position, used by the streak particles. */
    public boolean containsPoint(Vec3 position) {
        return position.distanceToSqr(center()) <= radius() * radius();
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
            LOGGER.info("[DomainExpansion] t={}s phase={} r={} y={} written={} captured={} guided={}"
                            + " slashes={} blood={}(+{} capped) lines={}(+{} capped)",
                    lifeTicks / 20, phase, currentRadius, currentVerticalY, originalBlocks.size(),
                    captured.size(), projectilesGuided, slashHits, bloodBursts, bloodBurstsSkipped,
                    slashLineSpawns, slashLineSkipped);
        }

        if (lifeTicks % PERIODIC_INTERVAL == 0) {
            maintainBarrier(server);
            if (kind.hasSupportSpells()) {
                maintainSupportSpells(server);
            }
        }
        announce(server);

        if (buildComplete) {
            if (kind.hasCrimsonSlash()) {
                crimsonSlashStep(server);
            }
            guideProjectiles(server);
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
            server.sendParticles(kind.boundaryParticle(),
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
            shellState = kind.shellState();
            floorState = kind.floorState();
            airState = kind.airState();
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
    // guidance for thrown spells
    // ------------------------------------------------------------------

    /**
     * Steers the caster's own projectiles towards the nearest other entity inside the domain.
     *
     * Thrown spells pick their direction at the moment they are launched, so inside a domain
     * they would otherwise sail past everything. Rather than reimplementing any particular
     * spell, this turns each in-flight projectile a fraction of the way towards a target every
     * tick: the path curves, and whatever the spell does when it arrives still happens
     * normally.
     *
     * Only the caster's projectiles are touched, and only while they are inside their own
     * domain - this must not hijack anyone else's arrows, or the caster's own once they leave.
     */
    private void guideProjectiles(ServerLevel server) {
        AABB box = new AABB(center(), center()).inflate(radius());
        List<Projectile> projectiles = server.getEntitiesOfClass(Projectile.class, box);
        if (projectiles.isEmpty()) {
            return;
        }
        // gathered once per tick rather than once per projectile
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, box);

        for (Projectile projectile : projectiles) {
            if (projectile.isRemoved() || projectile.onGround()) {
                continue;
            }
            Entity owner = projectile.getOwner();
            if (owner == null || !owner.getUUID().equals(ownerUuid)) {
                continue;
            }
            if (!contains(projectile)) {
                continue;
            }
            Vec3 motion = projectile.getDeltaMovement();
            if (motion.lengthSqr() < 1.0E-6D) {
                continue;
            }
            LivingEntity target = nearestTarget(projectile, targets);
            if (target == null) {
                continue;
            }

            Vec3 aim = target.position()
                    .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                    .subtract(projectile.position());
            if (aim.lengthSqr() < 1.0E-6D) {
                continue;
            }
            // the steering lives in Guidance, where it is unit tested: the obvious
            // implementation quietly bleeds speed on every steered tick
            Guidance.Vector steered = Guidance.steer(
                    new Guidance.Vector(motion.x, motion.y, motion.z),
                    new Guidance.Vector(aim.x, aim.y, aim.z),
                    GUIDANCE_TURN);
            projectile.setDeltaMovement(steered.x(), steered.y(), steered.z());
            // makes the server resend the velocity, otherwise the client keeps drawing the old path
            projectile.hurtMarked = true;
            projectilesGuided++;
        }
    }

    private LivingEntity nearestTarget(Projectile projectile, List<LivingEntity> candidates) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (candidate.getUUID().equals(ownerUuid) || !candidate.isAlive()) {
                continue;
            }
            if (candidate == projectile.getOwner() || !contains(candidate)) {
                continue;
            }
            double d = candidate.position().distanceToSqr(projectile.position());
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // announcement
    // ------------------------------------------------------------------

    /**
     * Shows the domain's name across the middle of the screen, then its epithet once the
     * first has finished.
     *
     * Sent to the caster, and to anyone else trapped inside - a domain announcing itself to
     * the people it has closed in is the point - as plain vanilla title packets, which is
     * what the requested "/title-like" behaviour actually is. The title's size is fixed by
     * the client; there is no packet-level scale, so "big but not too big" is the vanilla
     * title size.
     */
    private void announce(ServerLevel server) {
        if (titleStage >= 2) {
            return;
        }
        if (titleStage == 0) {
            for (ServerPlayer player : audience(server)) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, TITLE_NAME_STAY, 10));
                player.connection.send(new ClientboundSetTitleTextPacket(
                        title("domain_expansion.title.name", kind.titleNameColor())));
            }
            titleStage = 1;
            return;
        }
        if (lifeTicks >= TITLE_EPITHET_AT) {
            for (ServerPlayer player : audience(server)) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 45, 10));
                player.connection.send(new ClientboundSetTitleTextPacket(
                        title(kind.epithetKey(), kind.titleEpithetColor())));
            }
            titleStage = 2;
            LOGGER.info("[DomainExpansion] announced both titles by t={}s", lifeTicks / 20);
        }
    }

    /**
     * A title card: blue, and explicitly in the vanilla bitmap font.
     *
     * The font is named rather than left to the default because this pack ships ModernUI, which
     * replaces the interface fonts with smooth ones; asking for {@code minecraft:default}
     * directly is the only lever a server has over that, and it is the pixel font the title was
     * asked to use.
     */
    private Component title(String translationKey, ChatFormatting colour) {
        return Component.translatable(translationKey).withStyle(Style.EMPTY
                .withColor(colour)
                .withFont(ResourceLocation.withDefaultNamespace("default")));
    }

    /** The caster, plus anyone inside the sphere. */
    private List<ServerPlayer> audience(ServerLevel server) {
        List<ServerPlayer> players = new ArrayList<>();
        AABB box = new AABB(center(), center()).inflate(radius());
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (player.getUUID().equals(ownerUuid) || contains(player)) {
                players.add(player);
            }
        }
        return players;
    }

    // ------------------------------------------------------------------
    // target selection
    // ------------------------------------------------------------------

    /**
     * The nearest living entity inside the domain that is not the caster, for spells that are
     * told their target rather than finding one themselves.
     *
     * Iron's Spellbooks normally gets a target from the client's crosshair: the client
     * raycasts, highlights what it found and syncs it to the server, which is why a lock-on
     * spell still needs aiming inside a domain. Spells of that kind read
     * {@code MagicData.getAdditionalCastData()} rather than raycasting themselves, so setting
     * that field before the cast hands them a target with no client involvement at all.
     */
    public LivingEntity nearestTargetTo(LivingEntity caster) {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
        Vec3 c = center();
        AABB box = new AABB(c, c).inflate(radius());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == caster || candidate.getUUID().equals(ownerUuid) || !candidate.isAlive()) {
                continue;
            }
            if (!contains(candidate)) {
                continue;
            }
            double d = candidate.position().distanceToSqr(caster.position());
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // crimson slash
    // ------------------------------------------------------------------

    /**
     * Cuts everything inside that is not the caster, five times a tick - a hundred a second.
     *
     * Runs off the same containment test as the barrier, so "inside" means the same thing to
     * both. Each victim is struck individually rather than all at once, because the strike
     * clears that victim's hurt cooldown immediately before damaging them: a shared hit would
     * put every entity in the sphere on the same invulnerability timer and collapse the rate.
     *
     * The two visible effects are budgeted per second <em>per victim</em>, because a hundred
     * strikes a second is far more than either is worth drawing for:
     *
     *   - blood, {@link CrimsonSlash#BLOOD_PER_VICTIM_PER_SECOND}, spattered on the victim;
     *   - slash lines, {@link CrimsonSlash#LINES_PER_VICTIM_PER_SECOND}, started on the victim.
     *
     * Both allowances are therefore per entity, as asked, and both are backed by a global ceiling
     * - {@link CrimsonSlash#MAX_BLOOD_BURSTS_PER_TICK} and {@link SlashLines#MAX_LIVE_LINES} -
     * because a per-victim rate multiplied by a sphere full of entities is not a number the
     * client can draw. The ambient lines share the line ceiling, and are drawn whether or not
     * anything is inside to be cut.
     *
     * See {@link CrimsonSlash} for why the damage carries no attacker, and why only some strikes
     * are allowed to make a sound.
     */
    private void crimsonSlashStep(ServerLevel server) {
        Entity ownerEntity = ownerUuid != null ? server.getEntity(ownerUuid) : null;
        if (!(ownerEntity instanceof LivingEntity caster) || !caster.isAlive()) {
            return;
        }

        // the lines tick and expire themselves; this only keeps the ceiling honest
        slashLines.removeIf(Entity::isRemoved);

        // the ambient layer first, so an empty domain is not a dead one
        int ambient = CrimsonSlash.allocationsThisTick(AMBIENT_LINES_PER_SECOND, lifeTicks);
        for (int i = 0; i < ambient; i++) {
            spawnSlashLine(server, randomPointInside());
        }

        Vec3 c = center();
        AABB box = new AABB(c, c).inflate(radius());
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate.getUUID().equals(ownerUuid) || !candidate.isAlive()) {
                continue;
            }
            if (contains(candidate)) {
                victims.add(candidate);
            }
        }
        if (victims.isEmpty()) {
            return;
        }

        int hits = CrimsonSlash.hitsForTick(lifeTicks);
        boolean audibleTick = lifeTicks % CrimsonSlash.AUDIBLE_EVERY_TICKS == 0;
        float damage = CrimsonSlash.damage(spellLevel, caster);

        // per victim, not per tick: each entity gets its own allowance
        int bloodPerVictim = CrimsonSlash.allocationsThisTick(
                CrimsonSlash.BLOOD_PER_VICTIM_PER_SECOND, lifeTicks);
        int bloodSpent = 0;

        for (LivingEntity victim : victims) {
            for (int hit = 0; hit < hits; hit++) {
                // one sound per audible tick across all of them, not one per blow
                CrimsonSlash.strike(server, victim, damage, audibleTick && hit == 0);
                slashHits++;
            }

            for (int i = 0; i < bloodPerVictim; i++) {
                if (bloodSpent >= CrimsonSlash.MAX_BLOOD_BURSTS_PER_TICK) {
                    bloodBurstsSkipped++;
                    continue;
                }
                CrimsonSlash.bloodBurst(server, victim);
                bloodSpent++;
                bloodBursts++;
            }

        }
    }

    /** Starts one slash line, unless the ceiling is reached. */
    private boolean spawnSlashLine(ServerLevel server, Vec3 at) {
        if (slashLines.size() >= SlashLines.MAX_LIVE_LINES) {
            slashLineSkipped++;
            return false;
        }
        slashLines.add(SlashLines.spawn(server, at));
        slashLineSpawns++;
        return true;
    }

    /**
     * A point inside the sphere, biased into whatever the caster is looking at.
     *
     * Asked for as a way to get more out of fewer lines: the same count spread over the whole
     * sphere spends most of itself behind the player, so concentrating it in front reads as a much
     * denser domain for the same cost. That is what pays for the lines being thinner again.
     *
     * The server does know the caster's aim - it arrives with the movement packets - so no client
     * work is needed. Two honest limitations:
     *
     *   - the field of view is a client setting, anywhere from thirty to a hundred and ten
     *     degrees, and the server is not told which. {@link #VIEW_CONE_DEGREES} is a seventy-six
     *     degree estimate: a player on a very wide setting sees empty edges, one on a narrow
     *     setting sees lines outside the frame.
     *   - the aim is up to one tick old, so a fast turn leaves the lines slightly behind where the
     *     player is now looking. Which is why this stays a bias and not a hard filter - nothing
     *     is ever placed somewhere that could not have been reached anyway.
     *
     * Falls back to the whole sphere when there is no caster to aim from.
     */
    private Vec3 randomPointInside() {
        Entity owner = ownerUuid != null && level() instanceof ServerLevel server
                ? server.getEntity(ownerUuid) : null;
        if (!(owner instanceof LivingEntity caster)) {
            return randomPointInSphere();
        }

        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            return randomPointInSphere();
        }
        look = look.normalize();

        // a direction inside a cone about the aim, cosine weighted so it is even across the cone
        // rather than clustered along its axis
        double cosMax = Math.cos(Math.toRadians(VIEW_CONE_DEGREES));
        double cosTheta = 1.0D - random.nextDouble() * (1.0D - cosMax);
        double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));
        double phi = random.nextDouble() * Math.PI * 2.0D;

        // an orthonormal basis around the aim to build that direction in
        Vec3 helper = Math.abs(look.y) > 0.99D
                ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(helper).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 direction = look.scale(cosTheta)
                .add(right.scale(sinTheta * Math.cos(phi)))
                .add(up.scale(sinTheta * Math.sin(phi)));

        // a distance out along it, kept off the camera and inside the wall
        double min = Math.min(VIEW_MIN_DISTANCE, radius() * 0.5D);
        double distance = min + (radius() - 1.0D - min) * Math.sqrt(random.nextDouble());
        Vec3 eye = caster.position().add(0.0D, caster.getEyeHeight(), 0.0D);
        Vec3 point = eye.add(direction.scale(distance));

        // the eye is not the sphere's centre, so a point on the boundary can land outside it
        Vec3 c = center();
        Vec3 offset = point.subtract(c);
        double limit = radius() - 0.5D;
        if (offset.lengthSqr() > limit * limit) {
            point = c.add(offset.normalize().scale(limit * 0.95D));
        }
        return point;
    }

    /** The unbiased version, for a domain with no caster to aim from. */
    private Vec3 randomPointInSphere() {
        double r = radius() * (0.35D + 0.6D * Math.sqrt(random.nextDouble()));
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
        Vec3 c = center();
        return c.add(
                r * Math.sin(phi) * Math.cos(theta),
                r * Math.cos(phi) * 0.85D,
                r * Math.sin(phi) * Math.sin(theta));
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
     *
     * Both are configured by {@link SupportSpells}, which reproduces what those two spells
     * would produce if cast one level above the domain - the level relationship was asked for
     * explicitly - while forcing their durations to match the domain's.
     */
    private void maintainSupportSpells(ServerLevel server) {
        Entity ownerEntity = ownerUuid != null ? server.getEntity(ownerUuid) : null;
        if (!(ownerEntity instanceof LivingEntity caster) || !caster.isAlive()) {
            return;
        }

        if (overflow == null || overflow.isRemoved()) {
            overflow = SupportSpells.createOverflow(server, caster, spellLevel);
            server.addFreshEntity(overflow);
            int[] wet = SupportSpells.overflowWetRange(spellLevel, caster);
            LOGGER.info("[DomainExpansion] overflow active at t={}s spellLevel={} level={} radius={} wet={}-{}",
                    lifeTicks / 20, spellLevel, SupportSpells.levelFor(spellLevel),
                    overflow.getRadius(), wet[0], wet[1]);
        }

        // Rainfall discards itself on a fixed cadence, so it is re-summoned to keep up
        if (rainfall == null || rainfall.isRemoved()) {
            rainfall = SupportSpells.createRainfall(server, caster, spellLevel,
                    center().x, center().y, center().z);
            server.addFreshEntity(rainfall);
            LOGGER.info("[DomainExpansion] rainfall active at t={}s spellLevel={} level={} radius={} wet={}",
                    lifeTicks / 20, spellLevel, SupportSpells.levelFor(spellLevel),
                    rainfall.getRadius(), SupportSpells.rainfallWet(spellLevel, caster));
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
        // defaults to AQUA, so a domain saved before crimson existed still loads
        kind = DomainKind.byName(tag.getString("DomainKind"));
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
        tag.putString("DomainKind", kind.name());
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
