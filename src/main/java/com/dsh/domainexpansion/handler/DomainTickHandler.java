package com.dsh.domainexpansion.handler;

import com.dsh.domainexpansion.entity.DomainEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives every open domain from the server tick event.
 *
 * This deliberately does not rely on the domain entity's own {@code tick()}. The first
 * in-game test showed the domain updating for only about ten seconds: the shell and
 * support spells stopped and the entity stopped responding, because the domain's work
 * was being done inside an overridden {@code tick()} that skipped
 * {@code super.tick()}. Driving the logic from here decouples the domain's lifetime
 * from the anchor entity's, which is the behaviour the spell actually needs - a domain
 * lasts two minutes regardless of what happens to the entity that represents it.
 */
public final class DomainTickHandler {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private DomainTickHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel server)) {
            return;
        }

        List<DomainEntity> active = DomainEntity.activeDomains();
        if (active.isEmpty()) {
            return;
        }

        // iterate over a copy: a domain that expires removes itself during the loop
        for (DomainEntity domain : new ArrayList<>(active)) {
            if (domain.isRemoved()) {
                continue;
            }
            // Only advance domains that live in the level being ticked.
            //
            // activeDomains() is a global list, and Forge fires LevelTickEvent once per
            // loaded level - the overworld, the nether and the end all tick in single
            // player once they have been visited. Without this check a domain opened in the
            // overworld would be advanced once per level per tick, and every block write
            // would go through the other level's ServerLevel at the same coordinates: the
            // build would run at triple speed and carve a matching sphere into the nether.
            if (domain.level() != server) {
                continue;
            }
            try {
                domain.advance(server);
            } catch (Exception e) {
                // one misbehaving domain must not stop the others, and the trace is what
                // makes a failed test run diagnosable
                LOGGER.error("[DomainExpansion] domain tick failed, removing it", e);
                active.remove(domain);
                domain.discard();
            }
        }
    }
}
