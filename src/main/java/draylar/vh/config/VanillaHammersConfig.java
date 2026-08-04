package draylar.vh.config;

/**
 * Simplified replacement for the original OmegaConfig-based config (OmegaConfig is unmaintained
 * and was not ported). No config screen / persistence yet - just the same default values as
 * upstream. See PORTING_NOTES.md.
 */
public class VanillaHammersConfig {

    public boolean enableExtraMaterials = true;
    public int durabilityModifier = 5;
    public double breakSpeedMultiplier = 1.0;
}
