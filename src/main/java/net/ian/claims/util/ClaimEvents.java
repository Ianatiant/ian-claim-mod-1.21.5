package net.ian.claims.util;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ClaimEvents {

    public static void register() {
        // Block breaking protection
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!ClaimManager.canModifyAt(serverPlayer, pos.getX(), pos.getZ())) {
                    player.sendMessage(Text.literal("§cYou can't break blocks here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Block placement protection
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                BlockPos pos = hitResult.getBlockPos();
                if (!ClaimManager.canModifyAt(serverPlayer, pos.getX(), pos.getZ())) {
                    player.sendMessage(Text.literal("§cYou can't place blocks here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Item usage protection (simplified without TypedActionResult)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!ClaimManager.canModifyAt(serverPlayer,
                        player.getBlockX(), player.getBlockZ())) {
                    player.sendMessage(Text.literal("§cYou can't use items here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // Entity interaction protection
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (!ClaimManager.canModifyAt(serverPlayer,
                        entity.getBlockX(), entity.getBlockZ())) {
                    player.sendMessage(Text.literal("§cYou can't attack entities here!"), false);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });
    }
}