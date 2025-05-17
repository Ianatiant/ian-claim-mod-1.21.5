package net.ian.claims.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.ian.claims.data.LandClaim;
import net.ian.claims.util.ClaimManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.ian.claims.util.ClaimManager.transferClaim;
import static net.minecraft.server.command.CommandManager.*;

public final class ClaimCommand {
    private ClaimCommand() {} // Prevent instantiation

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Main claim command
        LiteralArgumentBuilder<ServerCommandSource> claimCommand = literal("claim")
                .then(createSizeCommand("16x16", 16))
                .then(createSizeCommand("32x32", 32))
                .then(createTrustCommand(true))
                .then(createTrustCommand(false))
                .then(literal("info")
                        .executes(ClaimCommand::infoCurrent)
                        .then(argument("claim", StringArgumentType.word())
                                .executes(ctx -> infoByName(ctx, StringArgumentType.getString(ctx, "claim")))
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
                );


        dispatcher.register(claimCommand);
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
                    .append(" to/from '")
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
                    sendError(ctx, Text.literal("No claim at your location"));
                    return 0;
                });
    }

    private static int infoByName(CommandContext<ServerCommandSource> ctx, String claimName) {
        return ClaimManager.getClaimByName(claimName)
                .map(claim -> sendClaimInfo(ctx, claim))
                .orElseGet(() -> {
                    sendError(ctx, Text.literal("No claim named '")
                            .append(Text.literal(claimName).formatted(Formatting.GOLD))
                            .append("'"));
                    return 0;
                });
    }

    private static int sendClaimInfo(CommandContext<ServerCommandSource> ctx, LandClaim claim) {
        Text info = Text.literal("\n=== Claim Info ===\n")
                .formatted(Formatting.GOLD)
                .append(createInfoLine("Name", claim.getLandName()))
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
}