package net.ian.claims.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.ian.claims.data.LandClaim;
import net.ian.claims.util.ClaimManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.ian.claims.util.ClaimManager.*;
import static net.minecraft.server.command.CommandManager.*;

public final class ClaimCommand {
    private ClaimCommand() {} // Prevent instantiation

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Main claim command
        LiteralArgumentBuilder<ServerCommandSource> claimCommand = literal("claim")
                .then(createSizeCommand("16x16", 16))
                .then(createSizeCommand("32x32", 32))
                .then(createTrustCommand(true))  // trust
                .then(createTrustCommand(false)) // untrust
                .then(literal("info")
                        .executes(ClaimCommand::infoCurrent)
                        .then(argument("claim", StringArgumentType.word())
                                .executes(ctx -> infoByName(ctx, StringArgumentType.getString(ctx, "claim")))
                        )
                )
                .then(literal("sell")
                        .then(argument("claim", StringArgumentType.word())
                                .then(argument("price", IntegerArgumentType.integer(1))
                                        .executes(ctx -> sellLand(
                                                ctx,
                                                StringArgumentType.getString(ctx, "claim"),
                                                IntegerArgumentType.getInteger(ctx, "price")
                                        ))
                                )
                        )
                )
                .then(literal("buy")
                        .then(argument("claim", StringArgumentType.word())
                                .executes(ctx -> buyLand(
                                        ctx, StringArgumentType.getString(ctx, "claim")
                                ))
                        )
                )
                .then(literal("transfer")
                        .then(argument("claim", StringArgumentType.word())
                                .then(argument("newOwner", EntityArgumentType.player())
                                        .executes(ctx -> executeTransfer(
                                                ctx,
                                                StringArgumentType.getString(ctx, "claim"),
                                                EntityArgumentType.getPlayer(ctx, "newOwner")
                                        ))
                                )
                        )
                )
                .then(literal("list")
                        .requires(source -> source.hasPermissionLevel(2)) // Admin only
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> listPlayerClaims(
                                        ctx, EntityArgumentType.getPlayer(ctx, "player")
                                ))
                        )
                )
                // New remove commands
                .then(literal("remove")
                        .then(argument("claim", StringArgumentType.word())
                                .executes(ctx -> removeClaim(
                                        ctx, StringArgumentType.getString(ctx, "claim")
                                ))
                        )
                )
                .then(literal("removeall")
                        .requires(source -> source.hasPermissionLevel(4)) // High-level admin only
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> removeAllClaims(
                                        ctx, EntityArgumentType.getPlayer(ctx, "player")
                                ))
                        )
                );

        dispatcher.register(claimCommand);
    }
    private static int listPlayerClaims(
            CommandContext<ServerCommandSource> ctx,
            ServerPlayerEntity targetPlayer
    ) {
        ServerPlayerEntity admin = ctx.getSource().getPlayer();
        if (admin == null) return 0;

        ClaimManager.listPlayerClaims(admin, targetPlayer);
        return Command.SINGLE_SUCCESS;
    }
    private static int transferClaim(CommandContext<ServerCommandSource> ctx, String claimName, ServerPlayerEntity newOwner) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (ClaimManager.transferClaim(player, claimName, newOwner)) {
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }
    private static int executeTransfer(
            CommandContext<ServerCommandSource> ctx,
            String claimName,
            ServerPlayerEntity newOwner
    ) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("§cOnly players can use this command"));
            return 0;
        }

        if (ClaimManager.transferClaim(player, claimName, newOwner)) {
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createSizeCommand(String sizeName, int size) {
        return literal(sizeName)
                .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> claimLand(ctx, size, StringArgumentType.getString(ctx, "name")))
                );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createTrustCommand(boolean isTrust) {
        return literal(isTrust ? "trust" : "untrust")
                .then(argument("claim", StringArgumentType.word())
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> modifyTrust(
                                        ctx,
                                        StringArgumentType.getString(ctx, "claim"),
                                        EntityArgumentType.getPlayer(ctx, "player"),
                                        isTrust
                                ))
                        )
                );
    }

    private static int claimLand(CommandContext<ServerCommandSource> ctx, int size, String name) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (ClaimManager.createClaim(player, name, size)) {
            sendSuccess(ctx, Text.literal("Claimed '")
                    .append(Text.literal(name).formatted(Formatting.GREEN))
                    .append(Text.literal(String.format("' (%dx%d)", size, size)))
            );
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    private static int modifyTrust(CommandContext<ServerCommandSource> ctx, String claimName, ServerPlayerEntity target, boolean addTrust) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        boolean success = addTrust
                ? ClaimManager.addTrustedPlayer(player, claimName, target)
                : ClaimManager.removeTrustedPlayer(player, claimName, target);

        if (success) {
            sendSuccess(ctx, Text.literal(addTrust ? "Added " : "Removed ")
                    .append(target.getDisplayName())
                    .append(" to '")
                    .append(Text.literal(claimName).formatted(Formatting.GOLD))
                    .append("'")
            );
            return Command.SINGLE_SUCCESS;
        }

        sendError(ctx, Text.literal("Failed to ")
                .append(addTrust ? "trust" : "untrust")
                .append(" player. Check claim ownership."));
        return 0;
    }

    private static int infoCurrent(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        return ClaimManager.getClaimAt(player)
                .map(claim -> sendClaimInfo(ctx, claim))
                .orElseGet(() -> {
                    sendError(ctx, Text.literal("§6[IanClaims]§4 No claim at your location"));
                    return 0;
                });
    }

    private static int infoByName(CommandContext<ServerCommandSource> ctx, String claimName) {
        return ClaimManager.getClaimByName(claimName)
                .map(claim -> sendClaimInfo(ctx, claim))
                .orElseGet(() -> {
                    sendError(ctx, Text.literal("§6[IanClaims]§4 No claim named '")
                            .append(Text.literal(claimName).formatted(Formatting.GOLD))
                            .append("'"));
                    return 0;
                });
    }

    private static int sendClaimInfo(CommandContext<ServerCommandSource> ctx, LandClaim claim) {
        Text info = Text.literal("\n=== Claim Info ===\n")
                .formatted(Formatting.GOLD)
                .append(createInfoLine("Land", claim.getLandName()))
                .append(createInfoLine("Owner", ClaimManager.getOwnerName(claim)))
                .append(createInfoLine("Size", claim.getSize() + "x" + claim.getSize()))
                .append(createInfoLine("Location",
                        String.format("[%d, %d]", claim.getCenterX(), claim.getCenterZ())))
                .append(createInfoLine("Bounds",
                        String.format("X:%d-%d Z:%d-%d", claim.getX1(), claim.getX2(), claim.getZ1(), claim.getZ2())))
                .append(createInfoLine("Trusted", claim.getTrustedPlayers().size() + " players"));

        ctx.getSource().sendFeedback(() -> info, false);
        return Command.SINGLE_SUCCESS;
    }

    private static Text createInfoLine(String label, String value) {
        return Text.literal(label + ": ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal(value + "\n")
                        .formatted(Formatting.WHITE));
    }

    private static void sendSuccess(CommandContext<ServerCommandSource> ctx, Text message) {
        ctx.getSource().sendFeedback(() -> message.copy().formatted(Formatting.GREEN), false);
    }

    private static void sendError(CommandContext<ServerCommandSource> ctx, Text message) {
        ctx.getSource().sendError(message.copy().formatted(Formatting.RED));
    }
    private static int removeClaim(
            CommandContext<ServerCommandSource> ctx,
            String claimName
    ) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (ClaimManager.removeClaim(player, claimName)) {
            ctx.getSource().sendFeedback(() ->
                            Text.literal(" ")
                                    .append(Text.literal(claimName).formatted(Formatting.GOLD))
                                    .append("§a'"),
                    false
            );
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }
    private static int removeAllClaims(
            CommandContext<ServerCommandSource> ctx,
            ServerPlayerEntity targetPlayer
    ) {
        int removed = ClaimManager.removeAllClaims(ctx.getSource().getPlayer(), targetPlayer);
        if (removed > 0) {
            ctx.getSource().sendFeedback(() ->
                            Text.literal("§aRemoved ")
                                    .append(Text.literal(String.valueOf(removed)).formatted(Formatting.GOLD))
                                    .append(" §aLand owned by ")
                                    .append(targetPlayer.getDisplayName()),
                    false
            );
        } else {
            ctx.getSource().sendFeedback(() ->
                            Text.literal("§6[IanClaims]§c No Land found for ")
                                    .append(targetPlayer.getDisplayName()),
                    false
            );
        }
        return removed;
    }
    private static int sellLand(CommandContext<ServerCommandSource> ctx, String claimName, int price) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        if (ClaimManager.putLandForSale(player, claimName, price)) {
            ctx.getSource().sendFeedback(() ->
                    Text.literal("§6[IanClaims]§a Land put up for sale!"), false);
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    private static int buyLand(CommandContext<ServerCommandSource> ctx, String claimName) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("§cOnly players can use this command"));
            return 0;
        }

        boolean success = ClaimManager.buyLand(player, claimName);
        if (success) {
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }
}