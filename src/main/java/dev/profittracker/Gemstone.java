package dev.profittracker;

import java.util.Locale;
import java.util.Optional;

public enum Gemstone {
    AMBER, AMETHYST, AQUAMARINE, CITRINE, JADE, JASPER, ONYX, OPAL, PERIDOT, RUBY, SAPPHIRE, TOPAZ;

    public static final int FLAWED_PER_FLAWLESS = 80 * 80;
    public String product() { return "FLAWLESS_" + name() + "_GEM"; }
    public static Optional<Gemstone> named(String name) {
        try { return Optional.of(valueOf(name.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
