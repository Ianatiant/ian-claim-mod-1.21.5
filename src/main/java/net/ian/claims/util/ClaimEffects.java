package net.ian.claims.util;

import net.ian.claims.data.LandClaim;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffect;
import java.util.Optional;

public class ClaimEffects {
    private static final int SPEED_BOOST_AMPLIFIER = 2;
    private static  final int HEALTH_REGEN = 255;
    private static  final int NIGHT_VISION = 1;

    public static boolean handleMobDamage(LivingEntity target, DamageSource source) {
        // Only protect players
        if (!(target instanceof ServerPlayerEntity)) {
            return true; // allow normal damage
        }

        ServerPlayerEntity player = (ServerPlayerEntity) target;
        Optional<LandClaim> claim = ClaimManager.getClaimAt(player.getBlockPos().getX(), player.getBlockPos().getZ());

        // If not in a claim, allow normal damage
        if (claim.isEmpty()) {
            return true;
        }

        // Check if damage is from a mob and player is owner/trusted
        if (source.getAttacker() instanceof MobEntity &&
                ClaimManager.isOwnerOrTrusted(player, claim.get())) {
            return false; // cancel the damage
        }

        return true; // allow normal damage
    }

    public static void applyClaimEffects(ServerPlayerEntity player) {
        Optional<LandClaim> claim = ClaimManager.getClaimAt(player.getBlockX(), player.getBlockZ());

        // Only apply effects if in a claim and is owner/trusted
        if (claim.isPresent() && ClaimManager.isOwnerOrTrusted(player, claim.get())) {
            // Heal player if damaged
            if (player.getHealth() < player.getMaxHealth()) {
               player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,
                       Integer.MAX_VALUE,HEALTH_REGEN,
                       true,false));
            }

            // Apply infinite speed boost
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED,
                    Integer.MAX_VALUE, // Infinite duration
                    SPEED_BOOST_AMPLIFIER,
                    true, // ambient (less intrusive particles)
                    false
                    // don't show icon unless player checks effects
            ));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    Integer.MAX_VALUE,
                    NIGHT_VISION,
                    true,
                    false
            ));
        } else {
            // Remove speed boost when leaving claim or not trusted
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.REGENERATION);
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}