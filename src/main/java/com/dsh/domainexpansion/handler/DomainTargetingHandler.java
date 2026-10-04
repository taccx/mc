package com.dsh.domainexpansion.handler;

import com.dsh.domainexpansion.entity.DomainEntity;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
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
 * Makes lock-on spells find a target inside the caster's own domain, without the crosshair.
 *
 * Iron's Spellbooks has two different ways of acquiring a target, and they need two different
 * answers. Both are handled here.
 *
 * <b>Spells that are told their target.</b> The client raycasts along the crosshair, highlights
 * what it found, syncs it to the server, and the spell reads it back through
 * {@code MagicData.getAdditionalCastData()}. Such a spell does not look for itself, so filling
 * that field before the cast is enough. {@link SpellPreCastEvent} is posted at the top of
 * {@code AbstractSpell.castSpell}, ahead of any {@code onCast}.
 *
 * <b>Spells that look for themselves.</b> About a dozen run their own
 * {@code Utils.raycastForEntity} inside {@code onCast} and overwrite whatever was put in the
 * field, so for those the aim itself has to be right. Rather than mixing into Iron's code, this
 * turns the caster towards the nearest trapped entity for the duration of the cast and puts the
 * rotation back afterwards.
 *
 * That second trick is safe because nothing is sent to the client: the server only tells the
 * client about the caster's rotation when the caster moves, so a rotation set and restored
 * inside one tick is never rendered. The camera does not move. The rotation is restored at the
 * end of the tick rather than after {@code onCast}, because both of Iron's cast events fire
 * *before* the spell runs - there is no after-hook to use - and holding the wrong rotation for
 * even one extra tick risks the server disagreeing with the client about which way the player
 * is facing.
 *
 * Known limitation: the nearest entity is chosen without regard to whether the spell is
 * offensive, so a supportive spell can pick up a hostile target. The log says which spell took
 * which target, which makes that easy to spot if it happens.
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

    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        // PlayerEvent.getEntity() is already a Player, so no pattern match is needed
        Player caster = event.getEntity();
        if (caster == null || caster.level().isClientSide()) {
            return;
        }
        if (!(caster.level() instanceof ServerLevel server)) {
            return;
        }

        DomainEntity domain = DomainEntity.activeFor(caster);
        if (domain == null || !domain.contains(caster)) {
            return;
        }

        LivingEntity target = domain.nearestTargetTo(caster);
        if (target == null) {
            return;
        }

        // 1. for the spells that are told their target
        MagicData data = MagicData.getPlayerMagicData(caster);
        if (data != null) {
            data.setAdditionalCastData(new TargetEntityCastData(target));
        }

        // 2. for the spells that raycast for themselves: bend the aim, remember the original
        BENT_AIM.put(caster.getUUID(), new float[]{caster.getYRot(), caster.getXRot()});
        aimAt(caster, target);

        long now = server.getGameTime();
        if (now - lastLogTick >= 5) {
            lastLogTick = now;
            LOGGER.info("[DomainExpansion] auto-target for {}: {} -> {}",
                    event.getSpellId(), caster.getName().getString(), target.getName().getString());
        }
    }

    /**
     * Puts every bent aim back at the end of the tick it was bent in.
     *
     * Runs on the level tick rather than on a cast callback because Iron's two cast events both
     * fire before the spell body, so there is no post-cast hook to hang this on.
     */
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
