package dev.profittracker;

/** Counts of physical forms. All accounting uses exact coal equivalents, never item slots. */
public record CoalStock(long coal, long enchanted, long enchantedBlocks) {
    public static final CoalStock ZERO = new CoalStock(0, 0, 0);

    public long units() { return coal + 160L * enchanted + 25_600L * enchantedBlocks; }
    public CoalStock plus(CoalStock other) {
        return new CoalStock(coal + other.coal, enchanted + other.enchanted,
                enchantedBlocks + other.enchantedBlocks);
    }
    public CoalStock minus(CoalStock other) {
        return new CoalStock(coal - other.coal, enchanted - other.enchanted,
                enchantedBlocks - other.enchantedBlocks);
    }

    /** Attribute only the newly acquired share of compacted items, even across a baseline. */
    public CoalStock earnedShare(long units) {
        long blocks = Math.min(units, Math.max(0, enchantedBlocks) * 25_600L);
        long enchantedUnits = Math.min(units - blocks, Math.max(0, enchanted) * 160L);
        // Here all three fields hold coal-equivalent units, including fractional item shares.
        return new CoalStock(units - blocks - enchantedUnits, enchantedUnits, blocks);
    }

    public double valueAsShares(double coalPrice, double enchantedPrice, double blockPrice) {
        return coal * coalPrice + enchanted * (enchantedPrice / 160.0)
                + enchantedBlocks * (blockPrice / 25_600.0);
    }
}
