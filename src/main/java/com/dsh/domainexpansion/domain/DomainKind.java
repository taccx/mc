package com.dsh.domainexpansion.domain;

import com.dsh.domainexpansion.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

/**
 * Which domain is open, and what that changes.
 *
 * The two domains share almost everything - the sphere, the barrier, the forced hits, the two
 * minute duration and the restore - so they share one entity class. What differs is the block
 * set, whether the Aqua support spells run, and whether the crimson slash runs.
 */
public enum DomainKind {

    /** 源流: black wall, teal floor, and Overflow and Rainfall maintained for the duration. */
    AQUA(ModBlocks.DOMAIN_SHELL, ModBlocks.DOMAIN_FLOOR, ModBlocks.DOMAIN_AIR, true, false,
            "domain_expansion.title.epithet"),

    /**
     * 猩红: a black void wall and a water-surfaced floor, and no support spells at all - instead
     * everything inside that is not the caster is cut, five times a second.
     */
    CRIMSON(ModBlocks.CRIMSON_DOMAIN_SHELL, ModBlocks.CRIMSON_DOMAIN_FLOOR,
            ModBlocks.CRIMSON_DOMAIN_AIR, false, true,
            "domain_expansion.title.crimson_epithet");

    private final RegistryObject<Block> shell;
    private final RegistryObject<Block> floor;
    private final RegistryObject<Block> air;
    private final boolean supportSpells;
    private final boolean crimsonSlash;
    private final String epithetKey;

    DomainKind(RegistryObject<Block> shell, RegistryObject<Block> floor, RegistryObject<Block> air,
               boolean supportSpells, boolean crimsonSlash, String epithetKey) {
        this.shell = shell;
        this.floor = floor;
        this.air = air;
        this.supportSpells = supportSpells;
        this.crimsonSlash = crimsonSlash;
        this.epithetKey = epithetKey;
    }

    /** The translation key of the second title card. The first is shared. */
    public String epithetKey() {
        return epithetKey;
    }

    public BlockState shellState() {
        return shell.get().defaultBlockState();
    }

    public BlockState floorState() {
        return floor.get().defaultBlockState();
    }

    public BlockState airState() {
        return air.get().defaultBlockState();
    }

    /** Whether Overflow and Rainfall are kept up for the whole duration. */
    public boolean hasSupportSpells() {
        return supportSpells;
    }

    /** Whether everything inside is cut repeatedly for the whole duration. */
    public boolean hasCrimsonSlash() {
        return crimsonSlash;
    }

    /** Resolves a saved name, falling back to the Aqua domain for anything unrecognised. */
    public static DomainKind byName(String name) {
        for (DomainKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return AQUA;
    }
}
