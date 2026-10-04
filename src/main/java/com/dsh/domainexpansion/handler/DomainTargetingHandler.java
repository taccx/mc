package com.dsh.domainexpansion.handler;

import com.dsh.domainexpansion.entity.DomainEntity;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Gives lock-on spells a target inside the caster's own domain, so they no longer need the
 * crosshair pointed at anything.
 *
 * How Iron's Spellbooks does targeting normally: the client raycasts along the crosshair,
 * highlights whatever it found and syncs it to the server, and the spell's {@code onCast}
 * reads it back through {@code MagicData.getAdditionalCastData()}. That is why a spell like
 * Slow still needs aiming even when everything nearby is already trapped in a domain.
 *
 * The spells that behave this way *read* that field rather than doing their own raycast, so
 * filling it in before the cast is enough to hand them a target. {@link SpellPreCastEvent} is
 * posted at the top of {@code AbstractSpell.castSpell}, ahead of any {@code onCast}, which
 * makes it the right place to do it.
 *
 * Two honest limitations, both worth knowing before assuming this covers everything:
 *
 *  - spells that run their own raycast and build the target data themselves are not affected,
 *    because they overwrite whatever was put here. Covering those needs a mixin into
 *    {@code Utils.raycastForEntity}, since the choice is made inside someone else's code.
 *  - the nearest entity is chosen without regard to whether the spell is offensive or
 *    supportive, so a healing spell can pick up a hostile target the same way. The log line
 *    says which spell took which target, so that is easy to spot if it happens.
 */
public final class DomainTargetingHandler {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Rate limit, so a spell spammer cannot flood the log. */
    private static long lastLogTick;

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

        MagicData data = MagicData.getPlayerMagicData(caster);
        if (data == null) {
            return;
        }
        data.setAdditionalCastData(new TargetEntityCastData(target));

        long now = server.getGameTime();
        if (now - lastLogTick >= 5) {
            lastLogTick = now;
            LOGGER.info("[DomainExpansion] auto-target for {}: {} -> {}",
                    event.getSpellId(), caster.getName().getString(), target.getName().getString());
        }
    }
}
