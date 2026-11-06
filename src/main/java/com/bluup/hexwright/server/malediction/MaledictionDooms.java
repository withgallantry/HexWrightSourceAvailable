package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionSigil;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class MaledictionDooms {

    public static final int DOOM_TICKS = 60;
    private static final int EFFECT_SLACK_TICKS = 10;
    public static final int AFFLICTION_TICKS = 160;

    private static final TagKey<MobEffect> UNDOOMABLE = TagKey.create(Registries.MOB_EFFECT,
        Hexwright.id("undoomable"));

    private record Doom(ServerLevel level, LivingEntity target, UUID caster, MaledictionFinish finish, float damage,
                        @Nullable MobEffect status, long landAt, boolean shown) {
    }

    private static final List<Doom> DOOMS = new ArrayList<>();

    private MaledictionDooms() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MaledictionDooms::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DOOMS.clear());
    }

    public static boolean mayLandAs(MobEffect status) {
        return !status.isInstantenous() && !HexwrightEffects.isStatus(status)
            && !BuiltInRegistries.MOB_EFFECT.wrapAsHolder(status).is(UNDOOMABLE);
    }

    public static void doom(ServerLevel level, ServerPlayer caster, LivingEntity target, MaledictionFinish finish,
                            float damage, @Nullable MobEffect status) {
        if (!MaledictionStrike.mayHarm(caster, target)) {
            return;
        }
        target.addEffect(new MobEffectInstance(HexwrightEffects.DOOMED, DOOM_TICKS + EFFECT_SLACK_TICKS, 0), caster);
        DOOMS.add(new Doom(level, target, caster.getUUID(), finish, damage, status,
            level.getGameTime() + DOOM_TICKS,
            target.hasEffect(HexwrightEffects.DOOMED)));
        MaledictionFx.sigil(level, MaledictionSigil.Kind.DOOM, finish, target.position(), target.position(),
            clockSize(target), target);
        MaledictionFx.sound(level, target.position(), SoundEvents.BELL_RESONATE, 0.8F, 0.7F);
    }

    private static void tick(MinecraftServer server) {
        if (DOOMS.isEmpty()) {
            return;
        }
        List<Doom> landing = new ArrayList<>();
        for (Iterator<Doom> it = DOOMS.iterator(); it.hasNext(); ) {
            Doom doom = it.next();
            LivingEntity target = doom.target;
            long left = doom.landAt - doom.level.getGameTime();
            if (!target.isAlive() || target.level() != doom.level) {
                it.remove();
            } else if (doom.shown && !target.hasEffect(HexwrightEffects.DOOMED)) {
                it.remove();
                MaledictionFx.sigil(doom.level, MaledictionSigil.Kind.LIFTED, doom.finish, target.position(),
                    target.position(), 0.0F, target);
            } else if (left <= 0) {
                it.remove();
                landing.add(doom);
            } else if (left % 20 == 0) {
                MaledictionFx.sound(doom.level, target.position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F, 0.7F);
            }
        }
        for (Doom doom : landing) {
            land(server, doom);
        }
    }

    private static float clockSize(LivingEntity target) {
        return target.getBbWidth() + 0.6F;
    }

    private static void land(MinecraftServer server, Doom doom) {
        LivingEntity target = doom.target;
        if (DOOMS.stream().noneMatch(other -> other.target == target)) {
            target.removeEffect(HexwrightEffects.DOOMED);
        }
        ServerPlayer caster = server.getPlayerList().getPlayer(doom.caster);
        if (caster == null) {
            return;
        }
        Vec3 at = target.getBoundingBox().getCenter();
        if (doom.status == null) {
            MaledictionStrike.strike(caster, target, doom.damage, doom.finish, null, caster.position());
        } else if (MaledictionStrike.mayHarm(caster, target)) {
            target.addEffect(new MobEffectInstance(doom.status, AFFLICTION_TICKS, 0), caster);
        }
        MaledictionFx.burst(doom.level, at, doom.finish, 24, target.getBbWidth() * 0.6);
        MaledictionFx.sigil(doom.level, MaledictionSigil.Kind.TOLL, doom.finish, at, at, clockSize(target), target);
        MaledictionFx.sound(doom.level, at, SoundEvents.BELL_BLOCK, 1.0F, 0.5F);
    }
}
