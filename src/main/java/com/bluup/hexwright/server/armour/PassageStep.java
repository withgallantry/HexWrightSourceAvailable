package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapImmuneEntity;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import com.bluup.hexwright.server.weapon.SlamWindUp;
import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static at.petrak.hexcasting.api.casting.OperatorUtils.getVec3;

public final class PassageStep {

    public static final int DEPART_TICKS = 26;

    private static final Set<UUID> DEPARTING = new HashSet<>();

    private PassageStep() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getSpecialHandlerRegistry(),
            Hexwright.id("passage_step"), FACTORY);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DEPARTING.clear());
    }

    private static final SpecialHandler.Factory<Handler> FACTORY = (pattern, env) -> {
        ItemStack cape = CapeOfPassageItem.wornSet(env.getCastingEntity());
        if (cape == null || !ArmourPowerToggle.matches(cape, pattern)) {
            return null;
        }
        return Handler.INSTANCE;
    };

    private enum Handler implements SpecialHandler {
        INSTANCE;

        @Override
        public Action act() {
            return STEP;
        }

        @Override
        public Component getName() {
            return Component.translatable("hexcasting.spell.hexwright.passage_step");
        }
    }

    private static final SpellAction STEP = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 target = getVec3(args, 0, getArgc());
            LivingEntity caster = env.getCastingEntity();
            if (caster == null) {
                throw new MishapBadCaster();
            }
            if (CapeOfPassageItem.wornSet(caster) == null) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_passage_unworn");
            }

            if (caster.getType().is(HexTags.Entities.CANNOT_TELEPORT)) {
                throw new MishapImmuneEntity(caster);
            }
            if (!HexConfig.server().canTeleportInThisDimension(env.getWorld().dimension())) {
                throw new MishapBadLocation(target, "bad_dimension");
            }
            env.assertVecInWorld(target);
            if (!env.isVecInWorld(target.subtract(0.0, 1.0, 0.0))) {
                throw new MishapBadLocation(target, "too_close_to_out");
            }

            ServerLevel world = env.getWorld();
            Vec3 from = caster.position();
            Vec3 delta = target.subtract(from);
            AABB landing = caster.getBoundingBox().move(delta);
            if (!world.noCollision(caster, landing)) {
                return new Result(new Spell() {
                    @Override
                    public void cast(CastingEnvironment castEnv) {
                        if (caster instanceof ServerPlayer player) {
                            player.displayClientMessage(
                                Component.translatable("message.hexwright.passage.obstructed"), true);
                        }
                    }
                }, 0L, List.of(), 1L);
            }

            TeleportWards.Check ward = TeleportWards.firstRefusing(world, from, target);
            if (ward != null) {
                return new Result(new Spell() {
                    @Override
                    public void cast(CastingEnvironment castEnv) {
                        if (caster instanceof ServerPlayer player) {
                            ward.notifyRefused(player);
                        }
                    }
                }, 0L, List.of(), 1L);
            }

            if (DEPARTING.contains(caster.getUUID())) {
                return new Result(new Spell() {
                    @Override
                    public void cast(CastingEnvironment castEnv) {
                    }
                }, 0L, List.of(), 1L);
            }

            float yaw = caster.getYRot();
            return new Result(new Spell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    if (!DEPARTING.add(caster.getUUID())) {
                        return;
                    }
                    HexwrightNetworking.sendPassagePortal(world, caster, caster.position(), false, yaw);
                    SlamWindUp.schedule(world.getServer(), DEPART_TICKS, () -> arrive(caster, world, target, yaw));
                }
            }, CapeOfPassageItem.MEDIA_PER_STEP,
                List.of(ParticleSpray.cloud(from.add(0.0, caster.getBbHeight() / 2.0, 0.0), 1.0, 12)),
                1L);
        }
    };

    private static void arrive(LivingEntity caster, ServerLevel world, Vec3 target, float yaw) {
        DEPARTING.remove(caster.getUUID());
        if (caster.isRemoved() || !caster.isAlive() || caster.level() != world) {
            return;
        }
        Vec3 departed = caster.position();
        Vec3 delta = target.subtract(departed);
        if (!world.noCollision(caster, caster.getBoundingBox().move(delta))) {
            return;
        }
        OpTeleport.INSTANCE.teleportRespectSticky(caster, delta, world);
        if (caster.position().distanceToSqr(departed) < 1.0e-4) {
            return;
        }
        HexwrightNetworking.sendPassagePortal(world, caster, caster.position(), true, yaw);
    }

    private abstract static class Spell implements RenderedSpell {
        @Override
        public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
            cast(castEnv);
            return image;
        }
    }
}
