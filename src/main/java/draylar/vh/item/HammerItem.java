package draylar.vh.item;

import draylar.vh.data.HammerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A hammer: breaking one block also breaks the blocks around it, in the plane perpendicular to
 * the direction the player was looking, out to {@code breakRadius} blocks away (radius 1 = 3x3).
 * <p>
 * This replaces the original mod's dependency on the (unmaintained, 1.20-era at best) "Magna"
 * library, which provided the same mechanic - see PORTING_NOTES.md for details on what's
 * simplified compared to the original implementation.
 * <p>
 * Extends {@code PickaxeItem} (rather than {@code Item} directly, as earlier versions of this
 * class did) specifically so its constructor builds the {@code minecraft:tool} data component for
 * us, referencing {@code BlockTags.MINEABLE_WITH_PICKAXE} - the same code path every vanilla
 * pickaxe uses, safe at this exact point in mod loading (block tags aren't bound yet - confirmed by
 * a real crash when this was attempted by hand instead, see PORTING_NOTES.md). Without a correct
 * {@code minecraft:tool} component, {@code Block.getDrops()} silently treats the hammer as the
 * wrong tool for every block: enchantments (Fortune...) get ignored and tier-gated blocks (e.g.
 * diamond ore) drop nothing at all - confirmed by a real report before this fix.
 */
public class HammerItem extends PickaxeItem {

    private final ToolMaterial material;
    private final int breakRadius;
    private final HammerData data;

    public HammerItem(ToolMaterial material, float attackDamage, float attackSpeed, Item.Properties settings, int breakRadius, HammerData data) {
        super(material, attackDamage, attackSpeed, settings);
        this.material = material;
        this.breakRadius = breakRadius;
        this.data = data;
    }

    public HammerData getData() {
        return data;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return material.speed();
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!(level instanceof ServerLevel serverLevel) || !(miner instanceof ServerPlayer player) || breakRadius <= 0) {
            return true;
        }

        Direction axis = axisOf(player.getLookAngle());

        for (BlockPos extra : blocksAround(pos, axis, breakRadius)) {
            breakExtra(serverLevel, extra, stack, player);
        }

        return true;
    }

    /** Snaps the player's look vector to the nearest of the 6 axis directions. */
    private static Direction axisOf(Vec3 look) {
        return Direction.getApproximateNearest(look.x, look.y, look.z);
    }

    private static Iterable<BlockPos> blocksAround(BlockPos center, Direction facing, int radius) {
        Direction.Axis axis = facing.getAxis();
        List<BlockPos> positions = new ArrayList<>();

        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) {
                    continue; // center block is already broken by vanilla
                }

                BlockPos pos = switch (axis) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                };

                positions.add(pos);
            }
        }

        return positions;
    }

    private void breakExtra(ServerLevel level, BlockPos pos, ItemStack tool, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            return; // air, or unbreakable (e.g. bedrock)
        }

        if (!isCorrectToolFor(state)) {
            return;
        }

        // NOTE: always compute drops via Block.getDrops() with the real tool stack, rather than
        // level.destroyBlock(pos, true, player) - that overload has no tool parameter, so it
        // computes drops as if broken by an empty hand: enchantments on the hammer (Fortune, Silk
        // Touch...) were silently ignored, and tool-tier-gated blocks (e.g. diamond ore) dropped
        // nothing at all. Confirmed by a real in-game report of Fortune not working / missing drops.
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
        level.removeBlock(pos, false);
        level.levelEvent(2001, pos, Block.getId(state));

        for (ItemStack drop : drops) {
            Block.popResource(level, pos, data.canSmelt() ? smelt(level, drop) : drop);
        }

        // NOTE: exact hurtAndBreak() signature in 26.2 unconfirmed - flagged in PORTING_NOTES.md.
        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    private boolean isCorrectToolFor(BlockState state) {
        return !state.is(material.incorrectBlocksForDrops());
    }

    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        // NOTE: RecipeManager access moved off Level onto the server in a past version - exact
        // 26.2 path unconfirmed (best guess) - flagged in PORTING_NOTES.md.
        return level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .map(holder -> holder.value().assemble(new SingleRecipeInput(input)))
                .filter(result -> !result.isEmpty())
                .map(result -> {
                    ItemStack copy = result.copy();
                    copy.setCount(input.getCount());
                    return copy;
                })
                .orElse(input);
    }
}
