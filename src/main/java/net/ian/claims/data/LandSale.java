package net.ian.claims.data;
import java.lang.reflect.Type;
import com.google.gson.annotations.SerializedName;

public class LandSale {
    @SerializedName("claim")
    private final LandClaim claim;

    @SerializedName("price")
    private final int price;

    @SerializedName("seller_uuid")
    private final String sellerUUID;

    @SerializedName("seller_name")
    private final String sellerName;

    public boolean contains(int x, int z) { return claim.contains(x, z);}
    public LandSale(LandClaim claim, int price, String sellerUUID, String sellerName) {
        this.claim = claim;
        this.price = price;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;

    }
    // Getters
    public LandClaim getClaim() { return claim; }
    public int getPrice() { return price; }
    public String getSellerUUID() { return sellerUUID; }
    public String getSellerName() { return sellerName; }

}