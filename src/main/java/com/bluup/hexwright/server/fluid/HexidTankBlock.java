package com.bluup.hexwright.server.fluid;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.remnant.BottleData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class HexidTankBlock extends Block implements EntityBlock {

    public static final EnumProperty<TankPart> PART = EnumProperty.create("part", TankPart.class);

    public HexidTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, TankPart.SOLO));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HexidTankBlockEntity(pos, state);
    }


    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        int below = HexidTankColumn.below(level, pos);
        int above = HexidTankColumn.above(level, pos);
        if (below + 1 + above > HexidTank.MAX_HEIGHT) {
            return null;
        }
        if (HexidTankColumn.joinWouldClash(level, pos)) {
            return null;
        }
        return defaultBlockState().setValue(PART, TankPart.of(below > 0, above > 0));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction != Direction.UP && direction != Direction.DOWN) {
            return state;
        }
        return state.setValue(PART, TankPart.of(
            HexidTankColumn.isTank(level.getBlockState(pos.below())),
            HexidTankColumn.isTank(level.getBlockState(pos.above()))));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        HexidTankColumn.normalise(level, pos);
        HexidPipeNetwork.spread(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            HexidTankColumn.split(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }


    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean water = held.is(Items.WATER_BUCKET);
        boolean hexid = held.is(HexidFluids.HEXID_BUCKET);
        boolean bucket = held.is(Items.BUCKET);
        boolean bottle = held.is(HexwrightItems.HEX_ENGRAVED_BOTTLE);
        ADMediaHolder holder = water || hexid || bucket || bottle ? null : mediaIn(held);
        if (!held.isEmpty() && !water && !hexid && !bucket && !bottle && holder == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        HexidTankBlockEntity tank = HexidTankColumn.controller(level, pos);
        if (tank == null) {
            return InteractionResult.PASS;
        }
        if (water) {
            pourIn(level, pos, player, hand, held, tank, 0);
        } else if (hexid) {
            pourIn(level, pos, player, hand, held, tank, HexidFluids.MEDIA_PER_BUCKET);
        } else if (bucket) {
            drawOff(level, pos, player, hand, held, tank);
        } else if (bottle) {
            useBottle(level, pos, player, hand, held, tank);
        } else if (holder != null) {
            dissolve(level, pos, player, hand, held, holder, tank);
        } else {
            report(player, tank);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    private static ADMediaHolder mediaIn(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) {
            return null;
        }
        ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
        return holder != null && holder.canProvide() && holder.getMedia() > 0 ? holder : null;
    }

    private static void pourIn(Level level, BlockPos pos, Player player, InteractionHand hand,
                               ItemStack held, HexidTankBlockEntity tank, long media) {
        if (tank.isRemnantStore()) {
            say(player, "hexwright.hexid_tank.holds_remnants");
            return;
        }
        if (tank.capacityMb() - tank.amountMb() < HexidTank.BUCKET_MB) {
            say(player, "hexwright.hexid_tank.full");
            return;
        }
        tank.store(tank.amountMb() + HexidTank.BUCKET_MB, tank.totalMedia() + media);
        player.setItemInHand(hand,
            ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        report(player, tank);
    }

    private static void drawOff(Level level, BlockPos pos, Player player, InteractionHand hand,
                                ItemStack held, HexidTankBlockEntity tank) {
        if (tank.isRemnantStore()) {
            say(player, "hexwright.hexid_tank.holds_remnants");
            return;
        }
        boolean hexid = tank.isHexid();
        if (hexid && tank.mediaPerMb() != HexidFluids.MEDIA_PER_MB) {
            say(player, "hexwright.hexid_tank.not_water",
                String.format("%.0f", HexidFluids.SATURATION * 100.0));
            return;
        }
        if (tank.amountMb() < HexidTank.BUCKET_MB) {
            say(player, "hexwright.hexid_tank.too_shallow");
            return;
        }
        long drawn = hexid ? HexidFluids.MEDIA_PER_BUCKET : 0;
        tank.store(tank.amountMb() - HexidTank.BUCKET_MB, tank.totalMedia() - drawn);
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player,
            new ItemStack(hexid ? HexidFluids.HEXID_BUCKET : Items.WATER_BUCKET)));
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        report(player, tank);
    }

    private static void dissolve(Level level, BlockPos pos, Player player, InteractionHand hand,
                                 ItemStack held, ADMediaHolder holder, HexidTankBlockEntity tank) {
        if (tank.isRemnantStore()) {
            say(player, "hexwright.hexid_tank.holds_remnants");
            return;
        }
        if (tank.amountMb() <= 0) {
            say(player, "hexwright.hexid_tank.needs_water");
            return;
        }
        long headroom = tank.mediaHeadroom();
        if (headroom <= 0) {
            say(player, "hexwright.hexid_tank.saturated");
            return;
        }

        long perItem = Math.max(1, holder.getMedia() / Math.max(1, held.getCount()));
        long asked = Math.min(perItem, headroom);
        long offered = holder.withdrawMedia(asked, true);
        if (offered <= 0) {
            return;
        }
        if (offered > headroom) {
            say(player, "hexwright.hexid_tank.no_room", held.getHoverName());
            return;
        }

        ItemStack before = player.getAbilities().instabuild ? held.copy() : ItemStack.EMPTY;
        long taken = holder.withdrawMedia(asked, false);
        tank.store(tank.amountMb(), tank.totalMedia() + taken);
        if (!before.isEmpty()) {
            player.setItemInHand(hand, before);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
            0.6f, 0.9f + (float) tank.saturation() * 0.5f);
        report(player, tank);
    }


    private static void useBottle(Level level, BlockPos pos, Player player, InteractionHand hand,
                                  ItemStack held, HexidTankBlockEntity tank) {
        ItemStack one = held.copy();
        one.setCount(1);
        Remnant contents = BottleData.getContents(one);
        boolean changed = contents == null
            ? drawIntoBottle(level, pos, player, one, tank)
            : pourFromBottle(level, pos, player, one, contents, tank);
        if (changed) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, one));
        }
    }

    private static boolean pourFromBottle(Level level, BlockPos pos, Player player, ItemStack bottle,
                                          Remnant contents, HexidTankBlockEntity tank) {
        if (!tank.canAcceptRemnants()) {
            say(player, "hexwright.hexid_tank.not_empty");
            return false;
        }
        if (plumbedToSomethingElse(level, pos, tank, contents.type())) {
            say(player, "hexwright.hexid_tank.network_clash");
            return false;
        }
        if (!tank.canAcceptRemnants(contents.type())) {
            if (tank.remnants().kinds() > 1) {
                say(player, "hexwright.hexid_tank.holds_mixture");
            } else {
                say(player, "hexwright.hexid_tank.wrong_remnant", tank.remnants().largest().label());
            }
            return false;
        }
        double poured = tank.addRemnant(contents);
        if (poured <= 0.0) {
            say(player, "hexwright.hexid_tank.remnants_full");
            return false;
        }
        double left = contents.drams() - poured;
        BottleData.empty(bottle);
        if (left >= TankRemnants.MIN_DRAMS) {
            BottleData.pour(bottle, contents.withDrams(left));
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8f, 1.1f);
        say(player, "hexwright.hexid_tank.poured", (int) Math.round(poured), contents.type().label());
        return true;
    }

    private static boolean plumbedToSomethingElse(Level level, BlockPos pos,
                                                  HexidTankBlockEntity tank, RemnantType type) {
        Set<RemnantType> after = EnumSet.of(type);
        after.addAll(tank.remnants().types());
        for (HexidTankBlockEntity other : HexidPipeNetwork.sharing(level, pos)) {
            if (other == tank) {
                continue;
            }
            if (other.holdsFluid() || (other.isRemnantStore() && !other.remnants().types().equals(after))) {
                return true;
            }
        }
        return false;
    }

    private static boolean drawIntoBottle(Level level, BlockPos pos, Player player, ItemStack bottle,
                                          HexidTankBlockEntity tank) {
        RemnantType type = tank.remnants().largest();
        if (type == null) {
            say(player, "hexwright.hexid_tank.no_remnants");
            return false;
        }
        double taken = tank.drawRemnant(type, Math.min(BottleData.capacity(bottle),
            tank.remnants().drams(type)));
        if (taken <= 0.0) {
            say(player, "hexwright.hexid_tank.no_remnants");
            return false;
        }
        double poured = BottleData.pour(bottle, new Remnant(type, taken));
        if (poured < taken) {
            tank.addRemnant(new Remnant(type, taken - poured));
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.1f);
        say(player, "hexwright.hexid_tank.drawn", (int) Math.round(poured), type.label());
        return true;
    }


    private static void report(Player player, HexidTankBlockEntity tank) {
        long amount = tank.amountMb();
        long capacity = tank.capacityMb();
        if (tank.isRemnantStore()) {
            say(player, "hexwright.hexid_tank.status.remnants",
                count(Math.round(tank.remnants().total())),
                count(Math.round(tank.remnantCapacity())));
        } else if (amount <= 0) {
            say(player, "hexwright.hexid_tank.status.empty", count(capacity));
        } else if (!tank.isHexid()) {
            say(player, "hexwright.hexid_tank.status.water", count(amount), count(capacity));
        } else {
            say(player, "hexwright.hexid_tank.status.hexid", count(amount), count(capacity),
                String.format("%.2f", tank.mediaPerMb() / 100.0),
                count(tank.totalMedia()));
        }
    }

    private static String count(long value) {
        return String.format("%,d", value);
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(
            Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
    }
}
