package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapImmuneEntity;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.bluup.hexwright.server.wardingbox.WardingBoxCastEnv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ram.talia.moreiotas.api.casting.iota.EntityTypeIota;
import ram.talia.moreiotas.api.casting.iota.ItemTypeIota;

import java.util.List;
import java.util.function.Consumer;

public final class WardingBoxActions {

    private WardingBoxActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("wardens_attunement"),
            new ActionRegistryEntry(HexPattern.fromAngles("dqaqd", HexDir.EAST), WARDENS_ATTUNEMENT));
        Registry.register(registry, Hexwright.id("wardens_reflection"),
            new ActionRegistryEntry(HexPattern.fromAngles("wawqwawa", HexDir.SOUTH_WEST), WARDENS_REFLECTION));
        Registry.register(registry, Hexwright.id("wardens_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("wawqwawaa", HexDir.SOUTH_WEST), WARDENS_GAMBIT));
        Registry.register(registry, Hexwright.id("redirect"),
            new ActionRegistryEntry(HexPattern.fromAngles("wawqwawaed", HexDir.SOUTH_WEST), REDIRECT));
    }

    private static WardingBoxBlockEntity wardAt(Vec3 vec, CastingEnvironment env) {
        env.assertVecInRange(vec);
        if (!(env.getWorld().getBlockEntity(BlockPos.containing(vec)) instanceof WardingBoxBlockEntity box)) {
            throw new MishapBadLocation(vec, "hexwright_warding_box");
        }
        return box;
    }

    private static final SpellAction WARDENS_ATTUNEMENT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            Iota raw = args.get(1);

            WardingBoxBlockEntity box = wardAt(vec, env);
            BlockPos pos = box.getBlockPos();

            Consumer<WardingBoxBlockEntity> attune;
            if (raw instanceof EntityTypeIota typeIota) {
                EntityType<?> type = typeIota.getEntityType();
                attune = ward -> ward.attuneToEntityType(type);
            } else if (raw instanceof ItemTypeIota typeIota) {
                Item item = typeIota.getItem();
                if (item == Items.AIR) {
                    throw MishapInvalidIota.ofType(raw, 0, "hexwright.ward_target");
                }
                attune = ward -> ward.attuneToItem(item);
            } else {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.ward_target");
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    attune.accept(box);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 1L);
        }
    };

    private static final ConstMediaAction WARDENS_REFLECTION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            AABB area = wardAt(vec, env).wardedArea();
            return List.of(new ListIota(List.<Iota>of(
                new Vec3Iota(new Vec3(area.maxX, area.maxY, area.maxZ)),
                new Vec3Iota(new Vec3(area.minX, area.minY, area.minZ))
            )));
        }
    };

    private static final SpellAction WARDENS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            Iota raw = args.get(1);

            WardingBoxBlockEntity box = wardAt(vec, env);
            BlockPos pos = box.getBlockPos();

            SpellList corners = OperatorUtils.getList(args, 1, getArgc());
            if (corners.size() != 2
                || !(corners.getAt(0) instanceof Vec3Iota first)
                || !(corners.getAt(1) instanceof Vec3Iota second)) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.cuboid");
            }

            Vec3 a = first.getVec3();
            Vec3 b = second.getVec3();
            double cx = pos.getX() + 0.5;
            double cz = pos.getZ() + 0.5;

            double spread = Math.max(
                Math.max(Math.abs(a.x - cx), Math.abs(b.x - cx)),
                Math.max(Math.abs(a.z - cz), Math.abs(b.z - cz)));
            double rise = Math.max(a.y, b.y) - pos.getY();

            int span = (int) Math.ceil(2.0 * spread - 1e-6);
            if (span % 2 == 0) {
                span++;
            }
            int width = Math.max(span, WardingBoxBlockEntity.MIN_WIDTH);
            int height = Math.max((int) Math.ceil(rise - 1e-6), WardingBoxBlockEntity.MIN_HEIGHT);

            int maxWidth = box.maxWidth();
            if (width > maxWidth || height > WardingBoxBlockEntity.MAX_HEIGHT) {
                throw MishapInvalidIota.of(raw, 0, "hexwright.ward_cuboid",
                    maxWidth, WardingBoxBlockEntity.MAX_HEIGHT);
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    box.resizeVolume(width, height);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.cloud(Vec3.atCenterOf(pos), width / 2.0, 20)), 1L);
        }
    };

    private static final SpellAction REDIRECT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            if (!(env instanceof WardingBoxCastEnv wardEnv)) {
                throw new MishapBadCaster();
            }

            Entity target = OperatorUtils.getEntity(args, 0, getArgc());
            Vec3 dest = OperatorUtils.getVec3(args, 1, getArgc());

            env.assertEntityInRange(target);
            env.assertVecInWorld(dest);

            if (target.getType().is(HexTags.Entities.CANNOT_TELEPORT)) {
                throw new MishapImmuneEntity(target);
            }
            if (target.getType().is(HexTags.Entities.STICKY_TELEPORTERS)) {
                for (Entity passenger : target.getPassengers()) {
                    if (passenger.getType().is(HexTags.Entities.CANNOT_TELEPORT)) {
                        throw new MishapImmuneEntity(passenger);
                    }
                }
            }

            Vec3 from = target.position();
            Vec3 delta = dest.subtract(from);

            AABB area = wardEnv.getBox().wardedArea();
            for (Entity part : target.getRootVehicle().getSelfAndPassengers().toList()) {
                if (!area.contains(part.position())) {
                    throw new MishapBadLocation(part.position(), "hexwright_outside_ward");
                }
                Vec3 landing = part.position().add(delta);
                if (!area.contains(landing)) {
                    throw new MishapBadLocation(landing, "hexwright_outside_ward");
                }
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    OpTeleport.INSTANCE.teleportRespectSticky(target, delta, castEnv.getWorld());
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 20, List.of(
                ParticleSpray.burst(from.add(0.0, target.getBbHeight() / 2.0, 0.0), 1.0, 15),
                ParticleSpray.cloud(dest, 1.0, 15)
            ), 1L);
        }
    };
}
