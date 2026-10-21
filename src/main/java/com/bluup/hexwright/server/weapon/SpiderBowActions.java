package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SpiderBowActions {

    private static final long SPIN_COST = MediaConstants.DUST_UNIT;

    private SpiderBowActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getSpecialHandlerRegistry(),
            Hexwright.id("spider_bow_sigil"), SIGIL_FACTORY);
    }

    private static final SpecialHandler.Factory<SpinWeb> SIGIL_FACTORY = (pattern, env) -> {
        if (!(env.getCastingEntity() instanceof ServerPlayer archer)
            || SpiderBowItem.carriedWithSigil(archer, pattern).isEmpty()) {
            return null;
        }
        return new SpinWeb(pattern);
    };

    private record SpinWeb(HexPattern pattern) implements SpecialHandler {
        @Override
        public Action act() {
            return spin(this.pattern);
        }

        @Override
        public Component getName() {
            return Component.translatable("hexcasting.spell.hexwright.spin_web");
        }
    }

    private static SpellAction spin(HexPattern pattern) {
        return new HexwrightSpellAction() {
            @Override
            public int getArgc() {
                return 0;
            }

            @Override
            public Result execute(List<? extends Iota> args, CastingEnvironment env) {
                if (!(env.getCastingEntity() instanceof ServerPlayer archer)) {
                    throw new MishapBadCaster();
                }
                ItemStack bow = SpiderBowItem.carriedWithSigil(archer, pattern);
                Iota caught = bow.isEmpty() ? null : SpiderBowItem.caught(bow, archer.serverLevel());
                if (caught == null) {
                    throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_nothing_caught");
                }
                Vec3 where = caught instanceof EntityIota entity
                    ? entity.getEntity().position()
                    : ((Vec3Iota) caught).getVec3();
                return new Result(new RenderedSpell() {
                    @Override
                    public void cast(CastingEnvironment castEnv) {
                        ItemStack ball = WebBallItem.spin(caught, archer.serverLevel());
                        if (!archer.getInventory().add(ball)) {
                            archer.drop(ball, false);
                        }
                        archer.level().playSound(null, archer.getX(), archer.getY(), archer.getZ(),
                            SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.5F);
                    }

                    @Override
                    public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                        cast(castEnv);
                        return image;
                    }
                }, SPIN_COST, List.of(ParticleSpray.burst(where, 0.6, 12)), 1L);
            }
        };
    }
}
