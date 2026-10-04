package com.dsh.domainexpansion.registry;

import com.dsh.domainexpansion.DomainExpansion;
import com.dsh.domainexpansion.spell.CrimsonDomainExpansionSpell;
import com.dsh.domainexpansion.spell.DomainExpansionSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Spell registration.
 *
 * The registry is created against {@link SpellRegistry#SPELL_REGISTRY_KEY} and
 * registered under this addon's own namespace. Entering a bare spell into a
 * book/scroll in game is done through Iron's Spellbooks' own inscription system
 * (or the {@code /cast} command), so no custom item is required.
 *
 * Pattern mirrors traveloptics' own {@code TravelopticsSpells} class.
 */
public final class ModSpells {

    private ModSpells() {
    }

    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, DomainExpansion.MODID);

    /** 领域展开, the Aqua (源流) variant. */
    public static final RegistryObject<AbstractSpell> DOMAIN_EXPANSION =
            SPELLS.register("domain_expansion", DomainExpansionSpell::new);

    /** 领域展开, the crimson (猩红 / Blood) variant. */
    public static final RegistryObject<AbstractSpell> CRIMSON_DOMAIN_EXPANSION =
            SPELLS.register("crimson_domain_expansion", CrimsonDomainExpansionSpell::new);
}
