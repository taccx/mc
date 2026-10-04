package com.dsh.domainexpansion.domain;

import com.dsh.domainexpansion.registry.ModBlocks;
import com.gametechbc.traveloptics.util.TravelopticsParticleHelper;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
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

    /**
     * The colour the first title card - the domain's shared name - is drawn in.
     *
     * Per kind, and per card, because the crimson domain's were asked to differ from each other:
     * black for the name and red for the epithet, against the Aqua domain's blue for both.
     *
     * Worth knowing before changing this: a title has no background, so it is drawn straight
     * over whatever the player is looking at. Black is invisible against the dark of a cave or a
     * night sky; it reads best in daylight.
     */
    public ChatFormatting titleNameColor() {
        return this == CRIMSON ? ChatFormatting.BLACK : ChatFormatting.BLUE;
    }

    /** The colour the second title card - the epithet - is drawn in. */
    public ChatFormatting titleEpithetColor() {
        return this == CRIMSON ? ChatFormatting.RED : ChatFormatting.BLUE;
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

    /**
     * Whether thin slashes streak across the inside of the sphere.
     *
     * Crimson only, because that is where it was asked for. Turning it on for Aqua is this one
     * line - {@code SlashStreaks} has no opinion about which domain it is drawing in.
     */
    public boolean hasSlashStreaks() {
        return this == CRIMSON;
    }

    /**
     * The motes drawn along the finished wall.
     *
     * Per-kind rather than fixed, which is a fix rather than a flourish: the Aqua wall's water
     * sparks were being emitted for both domains, so a crimson domain rained water droplets
     * inside. The crimson wall shows blood motes instead.
     *
     * Resolved inside the method rather than stored, so this class does not reach into another
     * mod's static fields while it is being initialised.
     */
    public ParticleOptions boundaryParticle() {
        return this == CRIMSON
                ? ParticleHelper.BLOOD
                : TravelopticsParticleHelper.WATER_SPARKS;
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
