package net.ian.claims.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimNotifier {
    private static final Map<UUID, BlockPos> lastPositions = new HashMap<>();

    public static void checkAndNotify(ServerPlayerEntity player) {
        BlockPos currentPos = player.getBlockPos();
        BlockPos lastPos = lastPositions.get(player.getUuid());

        if (lastPos == null || lastPos.getSquaredDistance(currentPos) > 16*16) {
            ClaimManager.getClaimAt(player).ifPresent(claim -> {
                player.sendMessage(Text.literal(
                        "Entered claim '" + claim.getOwnerUUID() +
                                "' owned by " + ClaimManager.getOwnerName(claim)
                ), false);
            });
            lastPositions.put(player.getUuid(), currentPos);
        }
    }
}