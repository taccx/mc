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
 * A piece of scenery a domain puts up: the ram skull at the centre of a crimson domain, or one of
 * the four torii around an aqua one.
 *
 * An entity rather than blocks, for two reasons that decided it. Blocks in Minecraft are
 * axis-aligned - they can face one of four directions and nothing else - and the four torii were
 * asked to lean at different angles, which no arrangement of blocks can do. And a model touches no
 * world state at all: there is nothing to record, nothing to restore, and nothing for the barrier
 * logic to trip over.
 *
 * The rise is done in the renderer from {@link #tickCount} rather than by moving the entity, so it
 * needs no server-side animation and no position packets, and it stays smooth on a client that
 * receives the entity part way through.
 */
public class DomainDecorationEntity extends Entity {

    /** The ram skull that stands at the middle of a crimson domain. */
    public static final int SKULL = 0;
    /** A torii gate, four of which stand around an aqua domain. */
    public static final int TORII = 1;

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(DomainDecorationEntity.class, EntityDataSerializers.INT);

    /**
     * How long the decoration takes to come up out of the floor, in ticks.
     *
     * Forty, which is the two seconds asked for. It is measured from the entity's own age, so it
     * runs on the client alone.
     */
    public static final int RISE_TICKS = 40;

    /**
     * How far below its resting place a decoration starts, in blocks.
     *
     * The full height of the model, so it begins completely under the floor and appears to come up
     * through it - the floor is solid and drawn with depth, so the buried part is hidden by the
     * same pass that hides anything else below ground.
     */
    private static final float RISE_FROM = 5.5F;

    /**
     * The lean applied to this decoration, in degrees.
     *
     * Kept on the entity rather than chosen by the renderer, because the renderer runs every frame
     * and a lean that changed every frame would be a decoration that never stops wobbling. It is
     * set once when the domain places the thing.
     */
    public float leanX;
    public float leanZ;

    public DomainDecorationEntity(EntityType<? extends DomainDecorationEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
    }

    public DomainDecorationEntity(Level level) {
        this(ModEntities.DOMAIN_DECORATION.get(), level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VARIANT, SKULL);
    }

    public int variant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    /** The height of the model, which is also how far it rises. */
    public static float riseFrom() {
        return RISE_FROM;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float height = variant() == TORII ? 5.5F : 1.6F;
        float width = variant() == TORII ? 4.0F : 1.4F;
        return EntityDimensions.scalable(width, height);
    }

    @Override
    public void tick() {
        super.tick();
        // nothing to simulate: it is scenery, and the only thing that changes is its age, which
        // the renderer reads for the rise
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_VARIANT, tag.getInt("Variant"));
        this.leanX = tag.getFloat("LeanX");
        this.leanZ = tag.getFloat("LeanZ");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Variant", variant());
        tag.putFloat("LeanX", leanX);
        tag.putFloat("LeanZ", leanZ);
    }
}
