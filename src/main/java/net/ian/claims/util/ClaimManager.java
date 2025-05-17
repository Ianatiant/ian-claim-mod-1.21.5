package net.ian.claims.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.ian.claims.data.LandClaim;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;

public class ClaimManager {
    private static final File CLAIMS_FILE = new File("./config/ian_claims/lands.json");
    private static final Map<String, LandClaim> namedClaims = new ConcurrentHashMap<>();
    private static final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    public static MinecraftServer server;

    static {
        loadClaims();
    }
    public static void initialize(MinecraftServer server) {
        nameCache.clear();
        System.out.println("ClaimManager initialized with server reference");
    }

    private static synchronized void loadClaims() {
        if (!CLAIMS_FILE.exists()) return;

        try (Reader reader = new FileReader(CLAIMS_FILE)) {
            Map<String, LandClaim> loaded = GSON.fromJson(
                    reader,
                    new TypeToken<Map<String, LandClaim>>(){}.getType()
            );
            if (loaded != null) namedClaims.putAll(loaded);
        } catch (IOException e) {
            System.err.println("Failed to load claims: " + e.getMessage());
        }
    }

    public static synchronized void saveClaims() {
        try {
            CLAIMS_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(CLAIMS_FILE)) {
                GSON.toJson(namedClaims, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save claims: " + e.getMessage());
        }
    }

    public static boolean createClaim(ServerPlayerEntity player, String landName, int size) {
        int x = player.getBlockX();
        int z = player.getBlockZ();

        // Check if name exists
        if (namedClaims.containsKey(landName.toLowerCase())) {
            player.sendMessage(Text.literal("§cA land with this name already exists!"), false);
            return false;
        }

        // Check area overlap
        for (LandClaim claim : namedClaims.values()) {
            if (claim.contains(x, z)) {
                player.sendMessage(Text.literal("§cThis area is already claimed!"), false);
                return false;
            }
        }

        LandClaim claim = new LandClaim(
                player.getUuid().toString(),
                player.getName().getString(),
                landName,
                x, z,
                size
        );

        namedClaims.put(landName.toLowerCase(), claim);
        saveClaims();

        player.sendMessage(Text.literal(String.format(
                "§aSuccessfully claimed '%s' (%dx%d) at [%d, %d]",
                landName, size, size, x, z
        )), false);

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

}