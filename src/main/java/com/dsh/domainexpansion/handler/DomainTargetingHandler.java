package com.dsh.domainexpansion.handler;

import com.dsh.domainexpansion.entity.DomainEntity;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.spells.TargetedTargetAreaCastData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes spells find a target inside the caster's own domain, without aiming.
 *
 * Iron's Spellbooks acquires targets in several different places, and the ordering is what
 * makes this more than a one-line job. Reading {@code AbstractSpell}:
 *
 * <pre>
 *   attemptInitiateCast
 *     L264  this.checkPreCastConditions(...)     the pre-cast hook - some spells target HERE
 *     L265  EVENT_BUS.post(SpellPreCastEvent)    the first event available to a mod
 *     L271  playerMagicData.initiateCast(...)    the wind-up starts
 *     ...
 *   castSpell                                      (after the wind-up)
 *     L296  EVENT_BUS.post(SpellOnCastEvent)
 *     L303  this.onCast(...)                     raycasts and look-direction snapshots happen HERE
 * </pre>
 *
 * Two consequences:
 *
 * <ul>
 *   <li>{@code SpellOnCastEvent} fires immediately before {@code onCast}, so it is the hook
 *       that matters. Bending the caster's aim there is seen by everything {@code onCast}
 *       does - {@code Utils.raycastForEntity}, and spells like Lightning Lance that simply
 *       shoot along {@code getLookAngle()}.</li>
 *   <li>A spell that targets in {@code checkPreCastConditions} has already chosen by the time
 *       any event fires, and {@code Utils.preCastTargetHelper} always raycasts and overwrites
 *       the cast data, so nothing can be put in place early enough. Those are re-targeted
 *       instead: the data they stored is swapped for the intended target just before
 *       {@code onCast} reads it back. Rainfall is the example - it wraps its target in a
 *       {@code TargetedTargetAreaCastData} and {@code onCast} moves the downpour onto
 *       whatever that holds.</li>
 * </ul>
 *
 * Bending the aim is safe because nothing is sent to the client: the server only tells a
 * client about its own rotation when that player moves, so a rotation set and restored within
 * one tick is never rendered. The camera does not move. It is restored on the level tick
 * because both of Iron's cast events fire *before* the spell body - there is no post-cast
 * hook - and holding a wrong rotation for longer risks the server and client disagreeing about
 * which way the player faces.
 *
 * Known limitation: the nearest entity is chosen without regard to whether the spell is
 * offensive, so a supportive spell can pick up a hostile target. {@code Utils.preCastTargetHelper}
 * sends its own "cast on self" message when it finds nothing, which this cannot suppress.
 */
public final class DomainTargetingHandler {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Rate limit for the log, so a spell spammer cannot flood it. */
    private static long lastLogTick;

    /**
     * Casters whose aim was temporarily bent towards a target, with the rotation to put back.
     * Keyed by player id; each entry lives at most one tick.
     */
    private static final Map<UUID, float[]> BENT_AIM = new HashMap<>();

    private DomainTargetingHandler() {
    }

    /**
     * Fills the cast data early, before the wind-up.
     *
     * Not sufficient on its own - it runs after {@code checkPreCastConditions} - but harmless,
     * and it is the earliest point at which the field can be set for spells that read it
     * during the wind-up rather than at the end.
     */
    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        Player caster = event.getEntity();
        DomainEntity domain = domainOf(caster);
        if (domain == null) {
            return;
        }
        LivingEntity target = domain.nearestTargetTo(caster);
        if (target == null) {
            return;
        }
        MagicData data = MagicData.getPlayerMagicData(caster);
        if (data != null) {
            data.setAdditionalCastData(new TargetEntityCastData(target));
        }
    }

    /**
     * The hook that actually decides where the spell goes: it fires immediately before
     * {@code onCast}, and everything the spell does next sees what is arranged here.
     */
    @SubscribeEvent
    public static void onSpellOnCast(SpellOnCastEvent event) {
        Player caster = event.getEntity();
        DomainEntity domain = domainOf(caster);
        if (domain == null) {
            return;
        }
        LivingEntity target = domain.nearestTargetTo(caster);
        if (target == null) {
            return;
        }

        MagicData data = MagicData.getPlayerMagicData(caster);
        if (data != null) {
            retarget(data, target);
        }
        // the aim is what onCast's own raycasts and look-direction snapshots read
        BENT_AIM.put(caster.getUUID(), new float[]{caster.getYRot(), caster.getXRot()});
        aimAt(caster, target);

        if (caster.level() instanceof ServerLevel server) {
            long now = server.getGameTime();
            if (now - lastLogTick >= 5) {
                lastLogTick = now;
                LOGGER.info("[DomainExpansion] auto-target for {}: {} -> {}",
                        event.getSpellId(), caster.getName().getString(), target.getName().getString());
            }
        }
    }

    /**
     * Points the cast data at the intended target, preserving any wrapper the spell already
     * put it in.
     *
     * A spell that targeted itself during {@code checkPreCastConditions} may have wrapped the
     * target in a {@code TargetedTargetAreaCastData} holding a marker entity it spawned. That
     * wrapper is rebuilt around the new target rather than discarded, so the spell still finds
     * the kind of data it expects and the marker keeps existing.
     */
    private static void retarget(MagicData data, LivingEntity target) {
        ICastData existing = data.getAdditionalCastData();
        if (existing instanceof TargetedTargetAreaCastData areaData) {
            if (areaData.getAreaEntity() != null) {
                data.setAdditionalCastData(new TargetedTargetAreaCastData(target, areaData.getAreaEntity()));
                return;
            }
        }
        data.setAdditionalCastData(new TargetEntityCastData(target));
    }

    /** Puts every bent aim back at the end of the tick it was bent in. */
    @SubscribeEvent
    public static void restoreAim(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BENT_AIM.isEmpty()) {
            return;
        }
        if (!(event.level instanceof ServerLevel server)) {
            BENT_AIM.clear();
            return;
        }
        for (Map.Entry<UUID, float[]> entry : BENT_AIM.entrySet()) {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.setYRot(entry.getValue()[0]);
                player.setXRot(entry.getValue()[1]);
                // keep the previous-tick values consistent too, so nothing interpolates oddly
                player.yRotO = entry.getValue()[0];
                player.xRotO = entry.getValue()[1];
            }
        }
        BENT_AIM.clear();
    }

    /** The caster's own domain, if they are standing inside it. */
    private static DomainEntity domainOf(Player caster) {
        if (caster == null || caster.level().isClientSide()) {
            return null;
        }
        DomainEntity domain = DomainEntity.activeFor(caster);
        return domain != null && domain.contains(caster) ? domain : null;
    }

    /** Points the caster's head at the target, from the eye to the target's middle. */
    private static void aimAt(Player caster, LivingEntity target) {
        double dx = target.getX() - caster.getX();
        double dz = target.getZ() - caster.getZ();
        double dy = (target.getY() + target.getBbHeight() * 0.5D)
                - (caster.getY() + caster.getEyeHeight());

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        caster.setYRot(yaw);
        caster.setXRot(pitch);
        caster.yRotO = yaw;
        caster.xRotO = pitch;
    }
}
