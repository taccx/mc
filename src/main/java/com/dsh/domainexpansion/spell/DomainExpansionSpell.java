package com.dsh.domainexpansion.spell;

import com.dsh.domainexpansion.DomainConfig;
import com.dsh.domainexpansion.entity.DomainEntity;
import com.dsh.domainexpansion.registry.ModSpells;
import com.gametechbc.traveloptics.api.init.TravelopticsSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 领域展开 / Domain Expansion - an Aqua (源流) ultimate.
 *
 * Casting it opens a 30 block radius domain around the caster for two minutes.
 * See {@link DomainEntity} for the rules it enforces while open, and
 * {@code DomainHitHandler} for the forced-hit rule.
 */
public class DomainExpansionSpell extends AbstractSpell {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private final ResourceLocation spellId =
            ResourceLocation.fromNamespaceAndPath("domain_expansion", "domain_expansion");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(TravelopticsSchools.AQUA_RESOURCE)
            .setMaxLevel(DomainConfig.MAX_LEVEL)
            .setCooldownSeconds(DomainConfig.COOLDOWN_SECONDS)
            .build();

    /**
     * The hand seal played during the wind-up: both arms already pointing forward and slightly
     * apart, closing together over the first 1.5 seconds and then holding still for the last
     * 0.5. Shipped as {@code player_animation/domain_seal.json}.
     *
     * Iron's own animations were tried first and none of them fits. {@code long_cast} is five
     * seconds of two poses alternating every half second, a charging tremor rather than a motion
     * towards anything. {@code self_cast_two_hands} is a pose, but a single keyframe: playing it
     * snaps the hands into place instead of moving them. A search of all 75 animations for one
     * where both arms point forward and are spread apart returned nothing.
     *
     * So the animation is authored, and the axis to animate was found the hard way - two earlier
     * attempts looked wrong in game before the model was understood. Minecraft applies a model
     * part's rotation as X, then Y, then Z, so the same axis means different things depending on
     * what the others are doing:
     *
     * <pre>
     *   X   swings the arm forward. Applied first, and -90 points it straight ahead - the value
     *       continuous_thrust holds.
     *   Y   yaws an already-forward arm sideways. Applied after X, so this is the axis that
     *       opens and closes a forward-pointing pair of hands.
     *   Z   swings a hanging arm out to the side; cast_t_pose uses Z = +95 / -97.5 with X = 0.
     *       It is the wrong axis here: with X near -90 the arm already points away from the Z
     *       axis, so changing Z spins it in place rather than moving it.
     * </pre>
     *
     * The first attempt changed X and Z together, which read as the hands being raised and then
     * lowered. The second held X at -95 and animated Z, which read as the arms rotating half a
     * turn and only meeting in the final moment. This one holds X at -90 and Z at 0, and
     * animates Y alone from -45 / +45 to -4 / +4, so the hands stay pointed forward throughout
     * and do nothing but close, finishing well before the hold.
     *
     * The signs were worked out from the model rather than guessed. The player model faces +Z,
     * and rotating the right arm's X by -90 swings it from hanging down onto +Z, which is why
     * -90 is the value continuous_thrust uses to point forward. Facing +Z puts the right arm on
     * the -X side, and a positive Y rotation sends the arm towards +X, so negative Y is what
     * swings the right arm outward - hence -45 for the right and +45 for the left. If the arms
     * ever visibly converge instead of spreading, those signs are the thing to flip.
     *
     * There is no position channel either. An earlier version also translated the arm pivots
     * 2 units inward as the hands met, and between that and the shoulder swing the motion read
     * as turning while also rotating inward. Rotation alone is what was asked for.
     *
     * One thing a single-bone arm cannot avoid: because the player model has no separate
     * forearm or hand bone here, swinging the arm at the shoulder necessarily turns the hand's
     * facing as well. That is the shoulder motion itself rather than an extra twist, which is
     * why Z is held at exactly 0.
     */
    private static final AnimationHolder SEAL_ANIMATION = new AnimationHolder(
            ResourceLocation.fromNamespaceAndPath("domain_expansion", "domain_seal"), true);

    public DomainExpansionSpell() {
        this.baseManaCost = DomainConfig.BASE_MANA_COST;
        this.manaCostPerLevel = DomainConfig.MANA_COST_PER_LEVEL;
        this.castTime = DomainConfig.CAST_TIME;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
    }

    /**
     * Long: the domain does not open the instant the spell is cast. The caster draws their hands
     * together, holds the seal, and only then does the sphere appear.
     *
     * Iron's uses {@code castTime} for this, so {@link DomainConfig#CAST_TIME} is the wind-up in
     * ticks, and the cast fires by itself when it elapses - the key is pressed once and the
     * player does not have to keep holding it, which is the difference between LONG and
     * CONTINUOUS.
     */
    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    /**
     * The wind-up is pinned to {@link DomainConfig#CAST_TIME} rather than left to
     * {@code AbstractSpell}'s default, which scales the cast time by the caster's cast-time
     * reduction attribute.
     *
     * The animation is authored for exactly this duration: the arms reach the seal at 1.5s and
     * hold to 2.0s. Shortening the cast would cut the motion off before the hands ever meet, so
     * the seal would never be seen complete. Gear that reduces cast time therefore does not
     * shorten this spell, which is a deliberate trade - the alternative is an animation that
     * stops half way.
     */
    @Override
    public int getEffectiveCastTime(int spellLevel, LivingEntity entity) {
        return DomainConfig.CAST_TIME;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SEAL_ANIMATION;
    }

    /**
     * No release flourish. There was one - Iron's {@code long_cast_finish}, a short forward jab
     * on the frame the domain opens - and it was asked to be removed, so the seal simply holds
     * and the sphere appears.
     */
    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.none();
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && level instanceof ServerLevel server) {
            // A domain is a claim on the area: one caster, one domain at a time.
            // Casting again while one is open would stack two full block spheres on top
            // of each other (which is exactly what happened when the log showed two
            // "domain opened" lines and doubled build work). The old domain is closed
            // cleanly - restoring its blocks - before the new one opens.
            DomainEntity existing = DomainEntity.activeFor(entity);
            if (existing != null) {
                existing.closeForRecast(server);
            }

            DomainEntity domain = new DomainEntity(server, entity, spellLevel);
            server.addFreshEntity(domain);
            // Initialise explicitly rather than relying on the loader's onAddedToWorld
            // callback. Registration with the tick handler happens inside, and if that step
            // is ever missed the spell silently does nothing at all - the exact symptom that
            // wasted several attempts. onAddedToWorld still calls the same idempotent method,
            // so whichever runs first wins.
            domain.initialize(server);
            LOGGER.info("[DomainExpansion] cast by {} at level {} from {}",
                    entity.getName().getString(), spellLevel, castSource);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * The lines a scroll shows for this spell.
     *
     * The support spells are listed with the level they actually run at, which is one above the
     * domain and therefore not obvious from the spell's own level. Their wet-effect amplifiers
     * are included too, since that is what those levels translate into in practice: a level 3
     * domain runs a level 4 Overflow, which is its maximum.
     */
    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        int supportLevel = SupportSpells.levelFor(spellLevel);
        int[] overflowWet = SupportSpells.overflowWetRange(spellLevel, caster);
        int rainfallWet = SupportSpells.rainfallWet(spellLevel, caster);

        return List.of(
                Component.translatable("ui.domain_expansion.domain_main"),
                Component.translatable("ui.domain_expansion.radius",
                        Utils.stringTruncation(DomainConfig.DOMAIN_RADIUS, 0)),
                Component.translatable("ui.domain_expansion.duration",
                        Utils.timeFromTicks(DomainConfig.DOMAIN_DURATION_TICKS, 2)),
                Component.translatable("ui.domain_expansion.forced_hit"),
                Component.translatable("ui.domain_expansion.overflow_level",
                        supportLevel, overflowWet[0], overflowWet[1]),
                Component.translatable("ui.domain_expansion.rainfall_level",
                        supportLevel, rainfallWet));
    }

    @Override
    public boolean stopSoundOnCancel() {
        return true;
    }
}
