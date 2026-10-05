package com.dsh.domainexpansion.entity;

import com.dsh.domainexpansion.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/**
 * One slash line, drawn as a textured quad rather than as particles.
 *
 * This is how the effect should have been done from the start, and it took seeing a reference to
 * get there. A line drawn from particles needs five to the block to close its gaps, so a single
 * line costs sixty particles over its life and a screen full of them costs thousands a tick - and
 * the more of them there are, the worse each one looks, because the dot spacing is what the eye
 * reads. One entity with one texture is a line of any length for the price of a single quad, so
 * the screen can be full of them and the frame rate does not notice.
 *
 * Nothing about this entity is a game object: it has no collision, no gravity, no AI, never
 * moves, deals no damage and lives for a fixed number of ticks. It exists to be rendered.
 */
public class SlashLineEntity extends Entity {

    /** White or red. Both are shipped; the choice is made once when the line is spawned. */
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(SlashLineEntity.class, EntityDataSerializers.INT);

    /**
     * How big the mark is, in blocks, on both axes.
     *
     * One number rather than a length and a thickness, because the mark is square now. It was a
     * long strip and it was tuned several times without ever reading as a slash - a thin bar is a
     * line however hard its edge - so it is drawn as what it is: two crossing cuts, which need a
     * square to live in rather than a strip.
     */
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(SlashLineEntity.class, EntityDataSerializers.FLOAT);

    /**
     * How long a mark lives. Eight ticks, which at a quarter of a second is long enough to be
     * read as a slash crossing the view and short enough that a crowd of them does not linger.
     */
    public static final int LIFETIME_TICKS = 8;

    public SlashLineEntity(EntityType<? extends SlashLineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public SlashLineEntity(Level level) {
        this(ModEntities.SLASH_LINE.get(), level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_SIZE, 8.0F);
    }

    public int variant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    public float size() {
        return this.entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        this.entityData.set(DATA_SIZE, size);
        // the bounding box is the mark, so the client knows how big to draw it without extra data
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(size(), size());
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount >= LIFETIME_TICKS) {
            this.discard();
        }
    }

    /** How far through its life the line is, 0 at spawn and 1 at death. Used to fade it out. */
    public float progress(float partialTick) {
        return Math.min(1.0F, (this.tickCount + partialTick) / (float) LIFETIME_TICKS);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // the domain is thirty blocks across; anything inside it should draw
        return distance < 4096.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // nothing persists: a slash line has no meaning outside the tick it was spawned in
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
