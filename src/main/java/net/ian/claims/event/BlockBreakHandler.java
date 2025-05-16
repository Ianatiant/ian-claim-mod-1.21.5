package net.ian.claims.event;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.ian.claims.util.ClaimManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public class BlockBreakHandler {
    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity)) {
                return true; // Only check server players
            }

            return ClaimManager.canModifyBlock(
                    (ServerPlayerEntity) player,
                    pos.getX(),
                    pos.getZ()
            );
        });
    }
}