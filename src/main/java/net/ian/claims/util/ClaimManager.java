package net.ian.claims.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.ian.claims.data.LandClaim;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClaimManager {
    // Persistent storage
    private static final File CLAIMS_FILE = new File("./config/ian_claims/claims.json");
    private static final File NAME_CACHE_FILE = new File("./config/ian_claims/name_cache.json");

    // Spatial indexes
    private static final Map<UUID, List<LandClaim>> claimsByOwner = new HashMap<>();
    private static final Map<Long, Set<LandClaim>> claimsByChunk = new HashMap<>();
    private static final Map<Integer, Set<LandClaim>> claimsByX = new HashMap<>();
    private static final Map<Integer, Set<LandClaim>> claimsByZ = new HashMap<>();

    // Player data
    private static final Map<UUID, String> nameCache = new HashMap<>();
    private static final Map<String, Set<UUID>> playersInClaims = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private static MinecraftServer server;

    /* ========== INITIALIZATION ========== */
    static {
        loadAllData();
    }

    public static void setServer(MinecraftServer server) {
        ClaimManager.server = server;
    }

    /* ========== CORE PROTECTION ========== */
    public static synchronized boolean canClaimArea(int x, int z, int size) {
        int x1 = x - size/2;
        int z1 = z - size/2;
        int x2 = x1 + size - 1;
        int z2 = z1 + size - 1;

        // 1. Chunk-level fast check
        if (checkChunkOverlap(x1, z1, x2, z2)) return false;

        // 2. Precise coordinate verification
        if (checkPreciseOverlap(x1, z1, x2, z2)) return false;

        // 3. Final boundary check
        return !checkBoundaryOverlap(x1, z1, x2, z2);
    }

    private static boolean checkChunkOverlap(int x1, int z1, int x2, int z2) {
        int minChunkX = x1 >> 4;
        int maxChunkX = x2 >> 4;
        int minChunkZ = z1 >> 4;
        int maxChunkZ = z2 >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = ((long)cx << 32) | (cz & 0xFFFFFFFFL);
                if (claimsByChunk.containsKey(chunkKey)) return true;
            }
        }
        return false;
    }

    private static boolean checkPreciseOverlap(int x1, int z1, int x2, int z2) {
        // Check X axis
        for (int x = x1; x <= x2; x++) {
            Set<LandClaim> xClaims = claimsByX.get(x);
            if (xClaims != null && xClaims.stream().anyMatch(c -> z1 <= c.getZ2() && z2 >= c.getZ1())) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkBoundaryOverlap(int x1, int z1, int x2, int z2) {
        return claimsByOwner.values().stream()
                .flatMap(List::stream)
                .anyMatch(c -> x1 <= c.getX2() && x2 >= c.getX1() &&
                        z1 <= c.getZ2() && z2 >= c.getZ1());
    }

    /* ========== CLAIM MANAGEMENT ========== */
    public static synchronized boolean addClaim(LandClaim claim) {
        // Final verification with transaction lock
        synchronized (ClaimManager.class) {
            if (!canClaimArea(
                    (claim.getX1() + claim.getX2()) / 2,
                    (claim.getZ1() + claim.getZ2()) / 2,
                    claim.getSize()
            )) {
                return false;
            }

            UUID owner = UUID.fromString(claim.getOwnerUUID());
            claimsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(claim);
            addToSpatialIndexes(claim);
            saveClaims();
            return true;
        }
    }

    private static void addToSpatialIndexes(LandClaim claim) {
        // Chunk index
        int minChunkX = claim.getX1() >> 4;
        int maxChunkX = claim.getX2() >> 4;
        int minChunkZ = claim.getZ1() >> 4;
        int maxChunkZ = claim.getZ2() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = ((long)cx << 32) | (cz & 0xFFFFFFFFL);
                claimsByChunk.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(claim);
            }
        }

        // X/Z indexes
        for (int x = claim.getX1(); x <= claim.getX2(); x++) {
            claimsByX.computeIfAbsent(x, k -> new HashSet<>()).add(claim);
        }
        for (int z = claim.getZ1(); z <= claim.getZ2(); z++) {
            claimsByZ.computeIfAbsent(z, k -> new HashSet<>()).add(claim);
        }
    }

    public static synchronized boolean removeClaim(LandClaim claim) {
        UUID owner = UUID.fromString(claim.getOwnerUUID());
        if (claimsByOwner.getOrDefault(owner, Collections.emptyList()).remove(claim)) {
            removeFromSpatialIndexes(claim);
            saveClaims();
            return true;
        }
        return false;
    }

    private static void removeFromSpatialIndexes(LandClaim claim) {
        // Chunk index
        int minChunkX = claim.getX1() >> 4;
        int maxChunkX = claim.getX2() >> 4;
        int minChunkZ = claim.getZ1() >> 4;
        int maxChunkZ = claim.getZ2() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = ((long)cx << 32) | (cz & 0xFFFFFFFFL);
                Set<LandClaim> claims = claimsByChunk.get(chunkKey);
                if (claims != null) {
                    claims.remove(claim);
                    if (claims.isEmpty()) claimsByChunk.remove(chunkKey);
                }
            }
        }

        // X/Z indexes
        for (int x = claim.getX1(); x <= claim.getX2(); x++) {
            Set<LandClaim> claims = claimsByX.get(x);
            if (claims != null) {
                claims.remove(claim);
                if (claims.isEmpty()) claimsByX.remove(x);
            }
        }
        for (int z = claim.getZ1(); z <= claim.getZ2(); z++) {
            Set<LandClaim> claims = claimsByZ.get(z);
            if (claims != null) {
                claims.remove(claim);
                if (claims.isEmpty()) claimsByZ.remove(z);
            }
        }
    }

    /* ========== PLAYER PROTECTION ========== */
    public static boolean canModifyAt(ServerPlayerEntity player, int x, int z) {
        if (player.hasPermissionLevel(2)) return true; // Ops bypass

        for (LandClaim claim : getClaimsAt(x, z)) {
            if (!claim.getOwnerUUID().equals(player.getUuid().toString())) {
                player.sendMessage(Text.literal("§cThis land is claimed by " + resolveOwnerName(claim.getOwnerUUID())), false);
                return false;
            }
        }
        return true;
    }

    public static List<LandClaim> getClaimsAt(int x, int z) {
        List<LandClaim> result = new ArrayList<>();
        Set<LandClaim> xClaims = claimsByX.get(x);
        if (xClaims != null) {
            xClaims.stream()
                    .filter(c -> c.contains(x, z))
                    .forEach(result::add);
        }
        return result;
    }

    /* ========== DATA PERSISTENCE ========== */
    private static synchronized void loadAllData() {
        loadClaims();
        loadNameCache();
    }

    private static void loadClaims() {
        if (!CLAIMS_FILE.exists()) return;

        try (Reader reader = new FileReader(CLAIMS_FILE)) {
            List<LandClaim> loaded = GSON.fromJson(reader, new TypeToken<List<LandClaim>>(){}.getType());
            if (loaded != null) {
                loaded.forEach(claim -> {
                    UUID owner = UUID.fromString(claim.getOwnerUUID());
                    claimsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(claim);
                    addToSpatialIndexes(claim);
                });
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to load claims: " + e.getMessage());
        }
    }

    private static void loadNameCache() {
        if (!NAME_CACHE_FILE.exists()) return;

        try (Reader reader = new FileReader(NAME_CACHE_FILE)) {
            Map<UUID, String> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, String>>(){}.getType());
            if (loaded != null) nameCache.putAll(loaded);
        } catch (IOException e) {
            System.err.println("[Claims] Failed to load name cache: " + e.getMessage());
        }
    }

    public static synchronized void saveClaims() {
        try {
            CLAIMS_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(CLAIMS_FILE)) {
                GSON.toJson(
                        claimsByOwner.values().stream().flatMap(List::stream).collect(Collectors.toList()),
                        writer
                );
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to save claims: " + e.getMessage());
        }
    }

    private static synchronized void saveNameCache() {
        try {
            NAME_CACHE_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(NAME_CACHE_FILE)) {
                GSON.toJson(nameCache, writer);
            }
        } catch (IOException e) {
            System.err.println("[Claims] Failed to save name cache: " + e.getMessage());
        }
    }

    /* ========== PLAYER MANAGEMENT ========== */
    private static String resolveOwnerName(String ownerUUID) {
        try {
            UUID uuid = UUID.fromString(ownerUUID);

            // Check online players
            if (server != null) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    String name = player.getName().getString();
                    nameCache.put(uuid, name);
                    return name;
                }
            }

            // Check cache
            String cached = nameCache.get(uuid);
            if (cached != null) return cached;

        } catch (IllegalArgumentException e) {
            System.err.println("[Claims] Invalid UUID: " + ownerUUID);
        }
        return "Unknown Player";
    }

    public static void checkPlayerEntry(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        nameCache.put(uuid, player.getName().getString());

        getClaimsAt(player.getBlockX(), player.getBlockZ()).forEach(claim -> {
            String key = claim.getOwnerUUID() + ":" + claim.getX1() + ":" + claim.getZ1();
            if (playersInClaims.computeIfAbsent(key, k -> new HashSet<>()).add(uuid)) {
                notifyEntry(player, claim);
            }
        });
    }

    private static void notifyEntry(ServerPlayerEntity player, LandClaim claim) {
        if (player.getUuid().toString().equals(claim.getOwnerUUID())) return;

        player.sendMessage(Text.literal(String.format(
                "§6[Claims] §eEntered §b%s's§e land (%dx%d at §a[%d,%d]§e)",
                resolveOwnerName(claim.getOwnerUUID()),
                claim.getSize(),
                claim.getSize(),
                claim.getX1(),
                claim.getZ1()
        )), false);
    }

    public static void checkPlayerExit(ServerPlayerEntity player) {
        playersInClaims.values().forEach(set -> set.remove(player.getUuid()));
    }

    /* ========== UTILITIES ========== */
    public static List<LandClaim> getPlayerClaims(String playerUUID) {
        try {
            return new ArrayList<>(claimsByOwner.getOrDefault(
                    UUID.fromString(playerUUID),
                    Collections.emptyList()
            ));
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }

    public static void onServerTick() {
        if (server != null && server.getTicks() % 36000 == 0) { // 30min
            saveNameCache();
        }
    }
    public static boolean canModifyBlock(ServerPlayerEntity player, int x, int z) {
        // Bypass for operators/admins
        if (player.hasPermissionLevel(2)) {
            return true;
        }

        // Get all claims at this location
        List<LandClaim> claims = getClaimsAt(x, z);

        // If unclaimed, allow modification
        if (claims.isEmpty()) {
            return true;
        }

        // Check if player owns ANY of the claims at this location
        String playerUUID = player.getUuid().toString();
        for (LandClaim claim : claims) {
            if (claim.getOwnerUUID().equals(playerUUID)) {
                return true;
            }
        }

        // Not owner - send warning message
        LandClaim primaryClaim = claims.get(0);
        player.sendMessage(Text.literal(
                String.format("§cThis land is owned by %s",
                        resolveOwnerName(primaryClaim.getOwnerUUID()))
        ), false);

        return false;
    }
}