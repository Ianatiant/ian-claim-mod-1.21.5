package net.ian.claims.util;

import net.ian.claims.data.LandClaim;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

public class ClaimProtection {
    public static boolean canModifyBlock(World world, BlockPos pos, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) return true;

        Optional<LandClaim> claim = ClaimManager.getClaimAt(pos.getX(), pos.getZ());
        return claim.isEmpty() ||
                ClaimManager.isOwnerOrTrusted((ServerPlayerEntity) player, claim.get());
    }
}