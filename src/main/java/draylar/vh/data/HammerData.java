package draylar.vh.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import draylar.vh.VanillaHammers;
import draylar.vh.item.HammerItem;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads {@code static_data/vanilla-hammers/hammers/*.json} from every loaded mod (this one and
 * any other mod that ships hammer definitions in the same convention, e.g. Adabranium) and
 * registers the corresponding hammer items.
 * <p>
 * This replaces the original mod's dependency on the (unmaintained) "StaticData" library, which
 * did the same cross-mod scan-and-load at mod-init time, before resources/tags are available.
 */
public final class HammerData {

    private static final String STATIC_DATA_PATH = "static_data/vanilla-hammers/hammers";

    /** Every hammer, regardless of material, shares this tag for anvil repair (simplified vs. upstream - see PORTING_NOTES.md). */
    public static final TagKey<Item> REPAIRABLE = TagKey.create(Registries.ITEM, VanillaHammers.id("repairable"));

    public final String id;
    public final int miningLevel;
    public final int durability;
    public final float blockBreakSpeed;
    public final float attackDamage;
    public final float attackSpeed;
    public final int enchantability;
    public final boolean isFireImmune;
    public final boolean smelts;
    public final int breakRadius;
    public final boolean isExtra;
    public final int burnTime;
    public final boolean hasExtraKnockback;

    /** Every hammer registered so far, in registration order - used to populate the creative tab. */
    public static final List<Item> ALL = new ArrayList<>();

    /** {@code (item, burnTime)} pairs collected during registration, consumed once by FuelValueEvents.BUILD in VanillaHammers. */
    public record FuelEntry(Item item, int burnTime) {}
    public static final List<FuelEntry> FUEL_ENTRIES = new ArrayList<>();

    private HammerData(JsonObject json) {
        this.id = getString(json, "id", "");
        this.miningLevel = getInt(json, "miningLevel", 0);
        this.durability = getInt(json, "durability", 500);
        this.blockBreakSpeed = getFloat(json, "blockBreakSpeed", 1.0f);
        this.attackDamage = getFloat(json, "attackDamage", 4.0f);
        this.attackSpeed = getFloat(json, "attackSpeed", -2.4f);
        this.enchantability = getInt(json, "enchantability", 15);
        this.isFireImmune = getBoolean(json, "isFireImmune", false);
        this.smelts = getBoolean(json, "smelts", false);
        this.breakRadius = getInt(json, "breakRadius", 1);
        this.isExtra = getBoolean(json, "isExtra", false);
        this.burnTime = getInt(json, "burnTime", 0);
        this.hasExtraKnockback = getBoolean(json, "hasExtraKnockback", false);
    }

    /** Scans every loaded mod's jar/dev-source-set for hammer definitions and registers them all. */
    public static void loadAndRegisterAll() {
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                Path hammersDir = root.resolve(STATIC_DATA_PATH);

                if (!Files.isDirectory(hammersDir)) {
                    continue;
                }

                try (Stream<Path> files = Files.list(hammersDir)) {
                    files.filter(p -> p.toString().endsWith(".json")).forEach(HammerData::loadAndRegister);
                } catch (IOException e) {
                    VanillaHammers.LOGGER.warn("Failed to list hammer data in {} from mod {}", hammersDir, mod.getMetadata().getId(), e);
                }
            }
        }
    }

    private static void loadAndRegister(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            new HammerData(element.getAsJsonObject()).register();
        } catch (Exception e) {
            VanillaHammers.LOGGER.error("Failed to load hammer data from {}", file, e);
        }
    }

    private void register() {
        if (id.isEmpty() || (isExtra && !VanillaHammers.CONFIG.enableExtraMaterials)) {
            return;
        }

        String path = id + "_hammer";
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, path.contains(":") ? Identifier.parse(path) : VanillaHammers.id(path));

        ToolMaterial material = new ToolMaterial(
                incorrectBlocksTag(miningLevel),
                durability * VanillaHammers.CONFIG.durabilityModifier,
                blockBreakSpeed * (float) VanillaHammers.CONFIG.breakSpeedMultiplier,
                attackDamage,
                enchantability,
                REPAIRABLE
        );

        Item.Properties settings = new Item.Properties()
                .setId(key)
                .stacksTo(1)
                .durability(durability * VanillaHammers.CONFIG.durabilityModifier)
                .enchantable(enchantability)
                .repairable(REPAIRABLE)
                .component(DataComponents.TOOL, buildToolComponent(blockBreakSpeed * (float) VanillaHammers.CONFIG.breakSpeedMultiplier))
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(VanillaHammers.id("hammer_attack_damage_" + id), attackDamage, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(VanillaHammers.id("hammer_attack_speed_" + id), attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build());
        if (isFireImmune) {
            settings = settings.fireResistant();
        }

        HammerItem hammer = new HammerItem(material, settings, breakRadius == 0 ? 1 : breakRadius, this);
        Registry.register(BuiltInRegistries.ITEM, key, hammer);
        ALL.add(hammer);

        if (burnTime > 0) {
            FUEL_ENTRIES.add(new FuelEntry(hammer, burnTime));
        }
    }

    /**
     * Builds the {@code minecraft:tool} data component by hand, since {@code HammerItem} extends
     * {@code Item} directly rather than {@code PickaxeItem}/{@code DiggerItem} (which would build
     * this automatically). Without it, {@code Tool.isCorrectForDrops()} - consulted internally by
     * {@code Block.getDrops()} - defaults to "not correct for drops" for every block, which is
     * silent (no exception, no log warning) and was the real cause of a real bug: enchantments
     * (Fortune...) being ignored and tier-gated blocks (e.g. diamond ore) dropping nothing at all
     * when broken by the hammer's area-of-effect. Covers the same 4 tags vanilla's own tools use
     * (pickaxe/shovel/axe/hoe) so drops behave correctly regardless of which kind of block the
     * hammer happens to hit. {@code Registry#getOrCreateTag} is used specifically because this runs
     * at mod-init time, before tags are loaded - it returns a live reference that gets filled in
     * once tags are ready, rather than requiring them to already exist (see the class javadoc on
     * why this whole scan-and-register step already has to run this early).
     */
    private static Tool buildToolComponent(float speed) {
        List<Tool.Rule> rules = List.of(
                new Tool.Rule(BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.MINEABLE_WITH_PICKAXE), Optional.of(speed), Optional.of(true)),
                new Tool.Rule(BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.MINEABLE_WITH_SHOVEL), Optional.of(speed), Optional.of(true)),
                new Tool.Rule(BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.MINEABLE_WITH_AXE), Optional.of(speed), Optional.of(true)),
                new Tool.Rule(BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.MINEABLE_WITH_HOE), Optional.of(speed), Optional.of(true))
        );
        return new Tool(rules, 1.0f, 1, false);
    }

    public boolean canSmelt() {
        return smelts;
    }

    public boolean hasExtraKnockback() {
        return hasExtraKnockback;
    }

    /**
     * Best-effort mapping from the original mod's 0-4 "mining level" to a vanilla "incorrect for X
     * tool" block tag - unconfirmed against a real 26.2 build, see PORTING_NOTES.md.
     */
    private static TagKey<Block> incorrectBlocksTag(int miningLevel) {
        return switch (miningLevel) {
            case 0 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 1 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static float getFloat(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
}
