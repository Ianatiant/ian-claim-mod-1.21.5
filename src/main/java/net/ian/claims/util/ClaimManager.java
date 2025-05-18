package net.ian.claims.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.ian.claims.data.LandClaim;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.ian.claims.data.LandSale;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ClaimManager {
    // File paths
    private static final File CLAIMS_FILE = new File("./config/ian_claims/lands.json");
    private static final File SALES_FILE = new File("./config/ian_claims/land_sales.json");

    // Data storage
    private static final Map<String, LandClaim> namedClaims = new ConcurrentHashMap<>();
    private static final Map<String, LandSale> landSales = new ConcurrentHashMap<>();
    private static final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    private static final Gson GSON = new Gson();
    public static MinecraftServer server;

    // Initialize when server starts
    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        nameCache.clear();
        loadClaims();
        server.sendMessage(Text.literal("[IanClaims] Claim Manager loaded."));
    }

    private static synchronized void loadClaims() {
        // Create config directory if needed
        File configDir = CLAIMS_FILE.getParentFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        // Load regular claims
        if (CLAIMS_FILE.exists()) {
            try (Reader reader = new FileReader(CLAIMS_FILE)) {
                Map<String, LandClaim> loaded = GSON.fromJson(
                        reader,
                        new TypeToken<Map<String, LandClaim>>(){}.getType()
                );
                if (loaded != null) {
                    namedClaims.putAll(loaded);
                }
            } catch (IOException e) {
                System.err.println("Failed to load claims: " + e.getMessage());
            }
        }

        // Load land sales
        if (SALES_FILE.exists()) {
            try (Reader reader = new FileReader(SALES_FILE)) {
                Map<String, LandSale> loaded = GSON.fromJson(
                        reader,
                        new TypeToken<Map<String, LandSale>>(){}.getType()
                );
                if (loaded != null) {
                    landSales.putAll(loaded);
                }
            } catch (IOException e) {
                System.err.println("Failed to load land sales: " + e.getMessage());
            }
        }
    }

    public static synchronized void saveClaims() {
        File configDir = CLAIMS_FILE.getParentFile();
        configDir.mkdirs();

        try {
            // Save regular claims
            try (Writer writer = new FileWriter(CLAIMS_FILE)) {
                GSON.toJson(namedClaims, writer);
            }

            // Save land sales
            try (Writer writer = new FileWriter(SALES_FILE)) {
                GSON.toJson(landSales, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save claims: " + e.getMessage());
        }
    }

    public static boolean createClaim(ServerPlayerEntity player, String landName, int size) {
        // First check if the name is available
        if (namedClaims.containsKey(landName.toLowerCase()) || landSales.containsKey(landName.toLowerCase())) {
            player.sendMessage(Text.literal("§6[IanClaims]§c A land with this name already exists!"), false);
            return false;
        }

        // Then check if the area is available
        if (!canClaimHere(player, landName, size)) {
            return false; // canClaimHere will send appropriate messages
        }

        // Show preview
        showClaimPreview(player, size);

        // Create the new claim
        LandClaim claim = new LandClaim(
                player.getUuid().toString(),
                player.getName().getString(),
                landName,
                player.getBlockX(),
                player.getBlockZ(),
                size
        );

        namedClaims.put(landName.toLowerCase(), claim);
        saveClaims();

        player.sendMessage(Text.literal("§6[IanClaims]§a Claim created!"), false);
        return true;
    }

    public static Optional<LandClaim> getClaimByName(String name) {
        return Optional.ofNullable(namedClaims.get(name.toLowerCase()));
    }

    public static Optional<LandClaim> getClaimAt(int x, int z) {
        return namedClaims.values().stream()
                .filter(claim -> claim.contains(x, z))
                .findFirst();
    }

    public static Optional<LandClaim> getClaimAt(ServerPlayerEntity player) {
        return getClaimAt(player.getBlockX(), player.getBlockZ());
    }

    // New trust management methods
    public static boolean addTrustedPlayer(ServerPlayerEntity owner, String claimName, ServerPlayerEntity target) {
        LandClaim claim = namedClaims.get(claimName.toLowerCase());
        if (claim == null || !claim.getOwnerUUID().equals(owner.getUuid().toString())) {
            return false;
        }

        if (claim.addTrustedPlayer(target.getUuid().toString())) {
            saveClaims();
            return true;
        }
        return false;
    }

    public static boolean removeTrustedPlayer(ServerPlayerEntity owner, String claimName, ServerPlayerEntity target) {
        LandClaim claim = namedClaims.get(claimName.toLowerCase());
        if (claim == null || !claim.getOwnerUUID().equals(owner.getUuid().toString())) {
            return false;
        }

        if (claim.removeTrustedPlayer(target.getUuid().toString())) {
            saveClaims();
            return true;
        }
        return false;
    }

    public static boolean isOwnerOrTrusted(ServerPlayerEntity player, LandClaim claim) {
        if (player == null || claim == null) return false;

        // Admins in creative mode bypass protection
        if (player.isCreative() || player.hasPermissionLevel(2)) {
            return true;
        }

        String playerUUID = player.getUuid().toString();
        return claim.getOwnerUUID().equals(playerUUID) ||
                claim.isTrusted(playerUUID);
    }

    public static boolean canModifyAt(ServerPlayerEntity player, int x, int z) {
        if (player == null) return true;

        // Allow operators and creative mode players to bypass
        if (player.hasPermissionLevel(2) || player.isCreative()) {
            return true;
        }

        Optional<LandClaim> claim = getClaimAt(x, z);
        return claim.isEmpty() ||
                claim.get().getOwnerUUID().equals(player.getUuid().toString()) ||
                claim.get().getTrustedPlayers().contains(player.getUuid().toString());
    }

    public static String getOwnerName(LandClaim claim) {
        // First try the stored name
        if (claim.getOwnerName() != null && !claim.getOwnerName().isEmpty()) {
            return claim.getOwnerName();
        }

        // Fallback to lookup if no name stored
        if (server == null) {
            return "Unknown Owner";
        }

        try {
            UUID ownerUUID = UUID.fromString(claim.getOwnerUUID());
            ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(ownerUUID);
            if (onlinePlayer != null) {
                return onlinePlayer.getName().getString();
            }

            Optional<GameProfile> cachedProfile = server.getUserCache().getByUuid(ownerUUID);
            if (cachedProfile.isPresent() && cachedProfile.get().getName() != null) {
                return cachedProfile.get().getName();
            }

            return "Unknown Owner";
        } catch (IllegalArgumentException e) {
            return "Invalid UUID";
        }
    }

    private static void log(String message) {
        if (server != null) {
            server.sendMessage(Text.literal("[IanClaims] " + message));
        }
    }
    public static boolean transferClaim(ServerPlayerEntity currentOwner, String claimName, ServerPlayerEntity newOwner) {
        LandClaim claim = namedClaims.get(claimName.toLowerCase());
        if (claim == null) {
            currentOwner.sendMessage(Text.literal("§6[IanClaims]§c No Land found with that name"), false);
            return false;
        }

        if (!claim.getOwnerUUID().equals(currentOwner.getUuid().toString())) {
            currentOwner.sendMessage(Text.literal("§6[IanClaims]§c You don't own this Land!"), false);
            return false;
        }

        claim.transferOwnership(
                newOwner.getUuid().toString(),
                newOwner.getName().getString()
        );

        saveClaims();

        // Notify both players
        currentOwner.sendMessage(
                Text.literal("§6[IanClaims]§a Successfully transferred Land '")
                        .append(Text.literal(claimName).formatted(Formatting.GOLD))
                        .append("§a to ")
                        .append(newOwner.getDisplayName()),
                false
        );

        newOwner.sendMessage(
                Text.literal("§6[IanClaims]§a You are now the owner of Land '")
                        .append(Text.literal(claimName).formatted(Formatting.GOLD))
                        .append("§a, transferred by ")
                        .append(currentOwner.getDisplayName()),
                false
        );

        return true;
    }
    public static List<LandClaim> getClaimsByOwner(UUID ownerUUID) {
        return namedClaims.values().stream()
                .filter(claim -> claim.getOwnerUUID().equals(ownerUUID.toString()))
                .collect(Collectors.toList());
    }

    public static void listPlayerClaims(ServerPlayerEntity admin, ServerPlayerEntity target) {
        List<LandClaim> claims = getClaimsByOwner(target.getUuid());

        Text header = Text.literal("=== Land owned by ")
                .append(target.getDisplayName())
                .append(" ===")
                .formatted(Formatting.GOLD, Formatting.BOLD);

        admin.sendMessage(header, false);

        if (claims.isEmpty()) {
            admin.sendMessage(Text.literal("§6[IanClaims] No Land found").formatted(Formatting.YELLOW), false);
            return;
        }

        claims.forEach(claim -> {
            admin.sendMessage(
                    Text.literal("- ")
                            .append(Text.literal(claim.getLandName()).formatted(Formatting.GREEN))
                            .append(" (")
                            .append(Text.literal(claim.getSize() + "x" + claim.getSize()).formatted(Formatting.AQUA))
                            .append(" at [")
                            .append(Text.literal(claim.getCenterX() + ", " + claim.getCenterZ()).formatted(Formatting.WHITE))
                            .append("]"),
                    false
            );
        });
    }
    // For admin removeall command
    public static int removeAllClaims(ServerPlayerEntity admin, ServerPlayerEntity target) {
        int count = 0;
        Iterator<Map.Entry<String, LandClaim>> iterator = namedClaims.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, LandClaim> entry = iterator.next();
            if (entry.getValue().getOwnerUUID().equals(target.getUuid().toString())) {
                iterator.remove();
                count++;
            }
        }

        if (count > 0) {
            saveClaims();
        }

        return count;
    }
    // For player remove command
    public static boolean removeClaim(ServerPlayerEntity player, String claimName) {
        LandClaim claim = namedClaims.get(claimName.toLowerCase());
        if (claim == null) {
            player.sendMessage(Text.literal("§6[IanClaims]§c No land found with that name"), false);
            return false;
        }

        // Check if player is owner OR OP Level 4+
        boolean isOwner = claim.getOwnerUUID().equals(player.getUuid().toString());
        boolean isAdmin = player.hasPermissionLevel(4); // OP Level 4 check

        if (!isOwner && !isAdmin) {
            player.sendMessage(Text.literal("§6[IanClaims]§c You don't own this land!"), false);
            return false;
        }

        // Admin override message
        if (isAdmin && !isOwner) {
            player.sendMessage(Text.literal("§eAdmin override - Removing " + claimName + " owned by " + claim.getOwnerName()), false);

            // Optional: Notify original owner
            ServerPlayerEntity owner = player.getServer().getPlayerManager().getPlayer(UUID.fromString(claim.getOwnerUUID()));
            if (owner != null) {
                owner.sendMessage(Text.literal("§cYour land " + claimName + " was removed by an§6 Admin"), false);
            }
        }

        namedClaims.remove(claimName.toLowerCase());
        saveClaims();
        player.sendMessage(Text.literal("§6[IanClaims]§a Successfully removed claim"), false);
        return true;
    }
    public static void showClaimPreview(ServerPlayerEntity player, int size) {
        BlockPos center = player.getBlockPos();
        int halfSize = size / 2;
        BlockPos corner1 = center.add(-halfSize, 0, -halfSize);
        BlockPos corner2 = center.add(halfSize, 0, halfSize);

        // Check for any conflicts
        boolean hasConflict = false;

        // Check against active claims
        for (LandClaim claim : namedClaims.values()) {
            if (checkOverlap(corner1.getX(), corner2.getX(), corner1.getZ(), corner2.getZ(),
                    claim.getX1(), claim.getX2(), claim.getZ1(), claim.getZ2())) {
                player.sendMessage(Text.literal("§6[IanClaims]§c Preview failed - Area overlaps with existing claim!"), false);
                hasConflict = true;
                break;
            }
        }

        // Check against land sales if no claim conflict
        if (!hasConflict) {
            for (LandSale sale : landSales.values()) {
                LandClaim saleClaim = sale.getClaim();
                if (checkOverlap(corner1.getX(), corner2.getX(), corner1.getZ(), corner2.getZ(),
                        saleClaim.getX1(), saleClaim.getX2(), saleClaim.getZ1(), saleClaim.getZ2())) {
                    player.sendMessage(Text.literal("§6[IanClaims]§c Preview failed - Area overlaps with land for sale!"), false);
                    hasConflict = true;
                    break;
                }
            }
        }

        // Only show preview if no conflicts
        if (!hasConflict) {
            new ClaimPreview(player.getWorld(), corner1, corner2).show(player);
        }
    }
    private static boolean canClaimHere(ServerPlayerEntity player, String name, int size) {
        // Check if name is taken in either active claims or land sales
        if (namedClaims.containsKey(name.toLowerCase()) || landSales.containsKey(name.toLowerCase())) {
            player.sendMessage(Text.literal("§6[IanClaims]§c Claim failed - name taken!"), false);
            return false;
        }

        BlockPos pos = player.getBlockPos();
        int x = pos.getX();
        int z = pos.getZ();
        int halfSize = size / 2;

        // Calculate the bounds of the new claim
        int newMinX = x - halfSize;
        int newMaxX = x + halfSize;
        int newMinZ = z - halfSize;
        int newMaxZ = z + halfSize;

        // Check against active claims
        for (LandClaim claim : namedClaims.values()) {
            if (checkOverlap(newMinX, newMaxX, newMinZ, newMaxZ,
                    claim.getX1(), claim.getX2(),
                    claim.getZ1(), claim.getZ2())) {
                player.sendMessage(Text.literal("§6[IanClaims]§c Claim failed - Land occupied by an existing claim!"), false);
                return false;
            }
        }

        // Check against land sales
        for (LandSale sale : landSales.values()) {
            LandClaim saleClaim = sale.getClaim();
            if (checkOverlap(newMinX, newMaxX, newMinZ, newMaxZ,
                    saleClaim.getX1(), saleClaim.getX2(),
                    saleClaim.getZ1(), saleClaim.getZ2())) {
                player.sendMessage(Text.literal("§6[IanClaims]§c Claim failed - This area is currently for sale!"), false);
                return false;
            }
        }

        return true;
    }

    private static boolean checkOverlap(int minX1, int maxX1, int minZ1, int maxZ1,
                                        int minX2, int maxX2, int minZ2, int maxZ2) {
        return !(maxX1 < minX2 || minX1 > maxX2 ||
                maxZ1 < minZ2 || minZ1 > maxZ2);
    }
    //Buy algorithm

    public static boolean putLandForSale(ServerPlayerEntity seller, String claimName, int price) {
        LandClaim claim = namedClaims.get(claimName.toLowerCase());

        if (claim == null) {
            seller.sendMessage(Text.literal("§6[IanClaims]§c No land found with that name"), false);
            return false;
        }

        if (!claim.getOwnerUUID().equals(seller.getUuid().toString())) {
            seller.sendMessage(Text.literal("§6[IanClaims]§c You don't own this land!"), false);
            return false;
        }

        if (price <= 0) {
            seller.sendMessage(Text.literal("§6[IanClaims]§c Price must be positive!"), false);
            return false;
        }

        // Create a copy of the claim without owner info
        LandClaim saleClaim = new LandClaim(
                "", // Empty owner UUID
                "", // Empty owner name
                claim.getLandName(),
                claim.getCenterX(),
                claim.getCenterZ(),
                claim.getSize()
        );

        // Copy trusted players if needed
        claim.getTrustedPlayers().forEach(saleClaim::addTrustedPlayer);

        landSales.put(claimName.toLowerCase(),
                new LandSale(saleClaim, price, seller.getUuid().toString(), seller.getName().getString()));
        namedClaims.remove(claimName.toLowerCase());
        saveClaims();

        seller.sendMessage(Text.literal(String.format(
                "§6[IanClaims]§a Land put up for sale for §e$%,d§a! Use §b/claim buy %s§a to purchase",
                price, claimName)), false);
        return true;
    }

    public static boolean buyLand(ServerPlayerEntity buyer, String claimName) {
        if (server == null) {
            buyer.sendMessage(Text.literal("§6[IanClaims]§c Server not initialized!"), false);
            return false;
        }

        try {
            LandSale sale = landSales.get(claimName.toLowerCase());
            if (sale == null) {
                buyer.sendMessage(Text.literal("§6[IanClaims]§c No land available for sale with that name"), false);
                return false;
            }

            Scoreboard scoreboard = server.getScoreboard();
            ScoreboardObjective bankObjective = scoreboard.getNullableObjective("Bank");

            if (bankObjective == null) {
                buyer.sendMessage(Text.literal("§6[IanClaims]§c Bank system not found!"), false);
                return false;
            }

            int balance = scoreboard.getOrCreateScore(buyer, bankObjective).getScore();

            if (balance < sale.getPrice()) {
                buyer.sendMessage(Text.literal(String.format(
                        "§6[IanClaims]§c You need §e$%,d§c more to buy this land!",
                        sale.getPrice() - balance)), false);
                return false;
            }

            // Deduct money
            scoreboard.getOrCreateScore(buyer, bankObjective).setScore(balance - sale.getPrice());

            // Pay seller
            ServerPlayerEntity seller = server.getPlayerManager().getPlayer(UUID.fromString(sale.getSellerUUID()));
            if (seller != null && !seller.equals(buyer)) {
                int sellerBalance = scoreboard.getOrCreateScore(seller, bankObjective).getScore();
                scoreboard.getOrCreateScore(seller, bankObjective)
                        .setScore(sellerBalance + sale.getPrice());
                seller.sendMessage(Text.literal(String.format(
                        "§6[IanClaims]§a You received §e$%,d§a for selling §b%s§a",
                        sale.getPrice(), claimName)), false);
            }

            // Transfer ownership
            LandClaim newClaim = sale.getClaim();
            newClaim.transferOwnership(
                    buyer.getUuid().toString(),
                    buyer.getName().getString()
            );

            namedClaims.put(claimName.toLowerCase(), newClaim);
            landSales.remove(claimName.toLowerCase());
            saveClaims();

            buyer.sendMessage(Text.literal(String.format(
                    "§6[IanClaims]§a You successfully bought §b%s§a for §e$%,d§a!",
                    claimName, sale.getPrice())), false);
            return true;
        } catch (Exception e) {
            System.err.println("Error processing land purchase: " + e.getMessage());
            e.printStackTrace();
            buyer.sendMessage(Text.literal("§6[IanClaims]§c Transaction failed!"), false);
            return false;
        }
    }



}