package draylar.vh;

import draylar.vh.config.VanillaHammersConfig;
import draylar.vh.data.HammerData;
import draylar.vh.item.HammerItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaHammers implements ModInitializer {

    public static final String MOD_ID = "vanilla-hammers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final VanillaHammersConfig CONFIG = new VanillaHammersConfig();

    public static final ResourceKey<CreativeModeTab> GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("group"));

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, GROUP, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.vanilla-hammers.group"))
                .icon(() -> new ItemStack(BuiltInRegistries.ITEM.get(id("diamond_hammer")).map(Holder.Reference::value).orElse(Items.STICK)))
                .build());

        // Scans this mod and every other loaded mod (e.g. Adabranium) for hammer definitions and
        // registers them all - see HammerData.
        HammerData.loadAndRegisterAll();

        CreativeModeTabEvents.modifyOutputEvent(GROUP).register(entries -> HammerData.ALL.forEach(entries::accept));

        // FuelRegistry was replaced by an event-based builder in 26.1 (fuel-registering hammers,
        // e.g. the wooden one, are collected during registration - see HammerData.FUEL_ENTRIES).
        FuelValueEvents.BUILD.register((builder, context) ->
                HammerData.FUEL_ENTRIES.forEach(entry -> builder.add(entry.item(), entry.burnTime())));

        registerCallbacks();
    }

    private void registerCallbacks() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack heldStack = player.getMainHandItem();

            if (heldStack.getItem() instanceof HammerItem hammerItem) {
                if (hammerItem.getData().canSmelt()) {
                    // NOTE: exact method name in 26.2 unconfirmed (best guess) - flagged in PORTING_NOTES.md.
                    entity.igniteForSeconds(4);
                }

                if (hammerItem.getData().hasExtraKnockback()) {
                    // Simplified vs. upstream (which boosted the vanilla knockback enchantment
                    // calculation via a mixin) - just gives the target an extra push directly.
                    entity.push(player.getLookAngle().x * 0.6, 0.15, player.getLookAngle().z * 0.6);
                }
            }

            return InteractionResult.PASS;
        });
    }

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }
}
