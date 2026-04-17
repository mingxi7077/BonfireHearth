package org.bonfiremc.hearth.brand;

public final class BonfireBranding {
    public static final String BRAND_NAME = "BonfireHearth Core";
    public static final String BRAND_ID = "bonfiremc:bonfirehearth-core";
    public static final String GRAY_CHANNEL = "Hearth-RC.1";
    public static final String CONFIG_FILE = "purpur.yml";

    private BonfireBranding() {
    }

    public static String runtimeBanner() {
        return BRAND_NAME + " " + GRAY_CHANNEL;
    }

    public static String startupBanner() {
        return runtimeBanner() + " | Purpur config compatibility mode (" + CONFIG_FILE + ")";
    }
}
