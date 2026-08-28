package com.bluup.hexwright.server.hexpatterns;

import com.bluup.hexwright.server.accessory.WornAccessories;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.reliquary.ChestCastEnv;
import com.bluup.hexwright.server.reliquary.ReliquarySealItem;
import com.bluup.hexwright.server.reliquary.ReliquaryStore;
import com.bluup.hexwright.server.talisman.WornTalismans;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ram.talia.moreiotas.api.casting.iota.ItemStackIota;

import java.util.ArrayList;
import java.util.List;

public final class HeldSealActions {

    private HeldSealActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("reliquarys_ledger"),
            new ActionRegistryEntry(HexPattern.fromAngles("weeeew", HexDir.WEST), RELIQUARYS_LEDGER));
        Registry.register(registry, Hexwright.id("reliquarys_offering"),
            new ActionRegistryEntry(HexPattern.fromAngles("weeeewqaa", HexDir.WEST), RELIQUARYS_OFFERING));
        Registry.register(registry, Hexwright.id("reliquarys_reclamation"),
            new ActionRegistryEntry(HexPattern.fromAngles("weeeewqdd", HexDir.WEST), RELIQUARYS_RECLAMATION));
    }

    private static String resolveHeldSeal(CastingEnvironment env) {
        ItemStack seal = ItemStack.EMPTY;

        if (env instanceof ChestCastEnv chestEnv) {
            ItemStack docked = chestEnv.heldSlotStack();
            if (docked.getItem() instanceof ReliquarySealItem) {
                seal = docked;
            }
        }

        if (seal.isEmpty()) {
            ServerPlayer caster = env.getCaster();
            if (caster != null) {
                for (ItemStack worn : WornAccessories.allWorn(caster)) {
                    if (worn.getItem() instanceof ReliquarySealItem) {
                        seal = worn;
                        break;
                    }
                }
            }
        }

        if (seal.isEmpty()) {
            CastingEnvironment.HeldItemInfo held =
                env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof ReliquarySealItem);
            if (held != null) {
                seal = held.stack();
            }
        }

        if (seal.isEmpty()) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_held_sealless");
        }
        String key = ReliquarySealItem.storeKey(seal);
        if (key == null || !ReliquaryStore.get(env.getWorld().getServer()).exists(key)) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_held_unbound");
        }
        return key;
    }

    private static void grantOrDrop(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            dropped.setPickUpDelay(10);
            player.level().addFreshEntity(dropped);
        }
    }

    private static void grantEviction(@Nullable ServerPlayer caster, ItemStack evicted) {
        if (evicted.isEmpty() || caster == null) {
            return;
        }
        grantOrDrop(caster, evicted);
    }

    private static final ConstMediaAction RELIQUARYS_LEDGER = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            String key = resolveHeldSeal(env);
            List<Iota> entries = new ArrayList<>();
            for (ItemStack stack : ReliquaryStore.get(env.getWorld().getServer()).inventory(key)) {
                entries.add(ItemStackIota.createFiltered(stack.isEmpty() ? ItemStack.EMPTY : stack.copy()));
            }
            return List.of(new ListIota(entries));
        }
    };

    private static final SpellAction RELIQUARYS_OFFERING = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ItemEntity offering = OperatorUtils.getItemEntity(args, 0, getArgc());
            int slot = OperatorUtils.getPositiveIntUnderInclusive(args, 1, ReliquaryStore.SLOTS - 1, getArgc());
            env.assertEntityInRange(offering);
            String key = resolveHeldSeal(env);
            ServerPlayer caster = env.getCaster();
            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    ReliquaryStore store = ReliquaryStore.get(castEnv.getWorld().getServer());
                    ItemStack evicted = store.placeAt(key, slot, offering.getItem());
                    offering.discard();
                    grantEviction(caster, evicted);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(offering.position(), 0.5, 10)), 1L);
        }
    };

    private static final ConstMediaAction RELIQUARYS_RECLAMATION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public long getMediaCost() {
            return MediaConstants.DUST_UNIT;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Iota raw = args.get(0);
            if (!(raw instanceof ItemStackIota wantedIota)) {
                throw MishapInvalidIota.ofType(raw, getArgc() - 1, "moreiotas.item_stack");
            }
            int position = OperatorUtils.getPositiveIntUnderInclusive(args, 1, ReliquaryStore.SLOTS - 1, getArgc());
            ItemStack wanted = wantedIota.getItemStack();
            String key = resolveHeldSeal(env);
            if (env.extractMedia(getMediaCost(), true) > 0) {
                throw new MishapNotEnoughMedia(getMediaCost());
            }
            ItemStack gathered = ReliquaryStore.get(env.getWorld().getServer()).withdraw(key, wanted, wanted.getCount(), position);
            if (!(env instanceof ChestCastEnv)) {
                ServerPlayer caster = env.getCaster();
                if (caster != null) {
                    grantOrDrop(caster, gathered);
                }
                return List.of();
            }
            return List.of(ItemStackIota.createFiltered(gathered));
        }
    };
}
