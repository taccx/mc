package com.dsh.domainexpansion.handler;

import com.dsh.domainexpansion.entity.DomainEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Implements the domain rule: any damage the caster deals inside their own domain is
 * applied to every other entity inside it.
 *
 * This is deliberately not a "make hits land" hook. The requested behaviour is that a
 * single attack, however it was aimed, becomes an attack on everyone in the domain -
 * so the damage is broadcast rather than merely made unavoidable. Consequences:
 *
 *   - it does not matter whether the original target was actually struck;
 *   - the broadcast amount is the damage the hit was going to deal;
 *   - lock-on, ground-AOE and projectile attacks all funnel through the same place,
 *     so all three behave as "hits everyone" without needing a per-spell implementation.
 *
 * Re-entrancy is the main hazard: the broadcast damages entities, which fires this same
 * event for each of them. Those re-entrant calls are recognised and ignored, both by a
 * flag and by checking that the damage source is the caster's, so the broadcast cannot
 * recurse or multiply.
 */
public final class DomainHitHandler {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Guards against the broadcast's own damage being broadcast again. */
    private static boolean broadcasting;

    /** Rate limit for the diagnostic line, so a long fight cannot flood the log. */
    private static long lastLogTick;

    private DomainHitHandler() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }

        // the attacker may be the direct source (melee) or only the indirect one
        // (projectiles), so accept either
        Entity source = event.getSource().getEntity();
        if (source == null) {
            source = event.getSource().getDirectEntity();
        }
        if (!(source instanceof Player attacker)) {
            return;
        }

        DomainEntity domain = DomainEntity.activeFor(attacker);
        if (domain == null) {
            log(attacker, "no open domain for " + attacker.getName().getString());
            return;
        }
        // activeFor matches on the caster's id alone, so a domain left open in another
        // dimension would otherwise be consulted for a fight happening here, where its
        // centre and radius mean nothing
        if (domain.level() != target.level()) {
            return;
        }
        if (!domain.contains(attacker)) {
            log(attacker, "attacker outside domain");
            return;
        }

        // Always make the original hit land: drop the i-frame window so it cannot be
        // shrugged off or skipped.
        target.invulnerableTime = 0;
        target.hurtTime = 0;

        float amount = event.getAmount();
        if (amount <= 0.0F || broadcasting) {
            // a zero-damage interaction has nothing to broadcast, and a re-entrant call
            // is our own broadcast arriving at one of its targets
            log(attacker, "hit on " + name(target) + " for " + amount
                    + (broadcasting ? " (broadcast, not re-broadcast)" : " (no damage to broadcast)"));
            return;
        }

        Broadcast result = broadcast(domain, attacker, target, amount);
        log(attacker, "hit " + name(target) + " for " + amount + " -> " + result.describe());
    }

    /**
     * What a broadcast actually did.
     *
     * Counted separately rather than collapsed into one number: a live run logged
     * "broadcast to 0 other entities" over and over, and that line alone could not say
     * whether the domain was empty, whether every other entity inside was already dead, or
     * whether the damage was being refused. Those are three very different bugs, so the
     * counts have to survive into the log.
     */
    private record Broadcast(int inRange, int outside, int dead, int landed) {

        String describe() {
            return "broadcast: " + inRange + " entities in range, " + landed + " damaged, "
                    + dead + " already dead, " + outside + " outside the sphere";
        }
    }

    private static Broadcast broadcast(DomainEntity domain, Player attacker, LivingEntity originalTarget,
                                       float amount) {
        if (!(attacker.level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return new Broadcast(0, 0, 0, 0);
        }

        AABB box = new AABB(domain.center(), domain.center()).inflate(domain.radius());
        List<LivingEntity> candidates = server.getEntitiesOfClass(LivingEntity.class, box);

        DamageSource source = attacker.damageSources().playerAttack(attacker);
        int inRange = 0;
        int outside = 0;
        int dead = 0;
        int landed = 0;

        broadcasting = true;
        try {
            for (LivingEntity candidate : candidates) {
                if (candidate == attacker || candidate == originalTarget) {
                    // excluded by design: the caster is not his own target, and the entity
                    // that was actually struck already took the hit
                    continue;
                }
                inRange++;
                if (!candidate.isAlive()) {
                    dead++;
                    continue;
                }
                if (!domain.contains(candidate)) {
                    outside++;
                    continue;
                }
                // clear the window per target, otherwise a target that was just hit would
                // absorb the broadcast into its i-frames
                candidate.invulnerableTime = 0;
                candidate.hurtTime = 0;
                if (candidate.hurt(source, amount)) {
                    landed++;
                }
            }
        } finally {
            broadcasting = false;
        }
        // self and the original target are excluded by design, so they are not reported
        return new Broadcast(inRange, outside, dead, landed);
    }

    private static String name(LivingEntity entity) {
        return entity.getName().getString();
    }

    private static void log(Player attacker, String message) {
        long now = attacker.level().getGameTime();
        if (now - lastLogTick < 5) {
            return;
        }
        lastLogTick = now;
        LOGGER.info("[DomainExpansion] hit-rule: {}", message);
    }
}
