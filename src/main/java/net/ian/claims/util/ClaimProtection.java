package net.ian.claims.util;

import net.ian.claims.data.LandClaim;
import net.minecraft.block.BlockState;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

import java.util.Optional;

public class ClaimProtection {
    public static boolean canModifyBlock(World world, BlockPos pos, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) return true;
        return !ClaimManager.isInClaim(pos) ||
                ClaimManager.isOwnerOrTrusted((ServerPlayerEntity) player, ClaimManager.getClaimAt(pos.getX(), pos.getZ()).orElse(null));
    }
    public static boolean canExplosionDestroy(World world, Explosion explosion, BlockPos pos) {
        Optional<LandClaim> claim = ClaimManager.getClaimAt(pos.getX(), pos.getZ());
        if (claim.isEmpty()) return true; // No claim, allow destruction

        // Check if explosion is caused by TNT or creeper
        Entity source = explosion.getEntity();
        if (source instanceof TntEntity || source instanceof CreeperEntity) {
            return false; // Prevent TNT and creeper explosions in claims
        }

        return true;
    }

    public static boolean canEntityGrief(Entity entity) {
        if (entity instanceof CreeperEntity || entity instanceof TntEntity) {
            Optional<LandClaim> claim = ClaimManager.getClaimAt(
                    (int) entity.getX(),
                    (int) entity.getZ()
            );
            return claim.isEmpty(); // Only allow griefing outside claims
        }
        return true;
    }

    public static Optional<LandClaim> getClaimAt(BlockPos pos) {
        return ClaimManager.getClaimAt(pos.getX(), pos.getZ());
    }
}