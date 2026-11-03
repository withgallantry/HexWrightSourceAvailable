package com.bluup.hexwright.server.cantillation;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.sideeffects.EvalSound;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.cantillation.Cantillation;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.flourish.DeliberateCasts;
import com.bluup.hexwright.server.hexpatterns.CastSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CastCantillations {

    private static final int PHRASE = 12;

    private static final double SEND_RADIUS = 24.0;

    private static final Map<ServerPlayer, Phrase> HELD = new LinkedHashMap<>();

    private CastCantillations() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CastCantillations::flush);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            CantillationState.get(server).sync(handler.player));
    }

    public static CastResult hear(CastingEnvironment env, CastResult result) {
        if (result.getSound().sound() == null || isMishap(result)) {
            return result;
        }
        if (!(env.getCastingEntity() instanceof ServerPlayer caster) || !DeliberateCasts.isDeliberate(env)) {
            return result;
        }
        if (!CantillationState.get(caster.server).wearing(caster.getUUID())) {
            return result;
        }
        HELD.computeIfAbsent(caster, key -> new Phrase()).add(noteOf(result), result.getSound());
        return CastSounds.muted(result);
    }

    private static boolean isMishap(CastResult result) {
        for (OperatorSideEffect effect : result.getSideEffects()) {
            if (effect instanceof OperatorSideEffect.DoMishap) {
                return true;
            }
        }
        return false;
    }

    private static CantillationCast.Note noteOf(CastResult result) {
        int key = result.getCast() instanceof PatternIota pattern ? Cantillation.keyOf(pattern.getPattern()) : 0;
        return new CantillationCast.Note(key, kindOf(result));
    }

    private static CantillationCast.Kind kindOf(CastResult result) {
        if (result.getResolutionType() == ResolvedPatternType.ESCAPED) {
            return CantillationCast.Kind.ESCAPED;
        }
        for (OperatorSideEffect effect : result.getSideEffects()) {
            if (effect instanceof OperatorSideEffect.AttemptSpell) {
                return CantillationCast.Kind.SPELL;
            }
        }
        return result.getCast() instanceof PatternIota ? CantillationCast.Kind.NOTE : CantillationCast.Kind.META;
    }

    private static void flush(MinecraftServer server) {
        if (HELD.isEmpty()) {
            return;
        }
        CantillationState state = CantillationState.get(server);
        for (Map.Entry<ServerPlayer, Phrase> entry : HELD.entrySet()) {
            ServerPlayer caster = entry.getKey();
            Phrase phrase = entry.getValue();
            Cantillation cantillation = CantillationItem.of(state.worn(caster.getUUID()));
            if (cantillation == null) {
                SoundEvent sound = phrase.loudest.sound();
                if (sound != null) {
                    caster.serverLevel().playSound(null, caster.getX(), caster.getY(), caster.getZ(), sound,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                continue;
            }
            HexwrightNetworking.sendCantillation(caster.serverLevel(),
                new CantillationCast(cantillation, caster.getId(), caster.position(), phrase.notes), SEND_RADIUS);
            if (HexwrightDebug.on(HexwrightDebug.CASTING)) {
                HexwrightDebug.log(HexwrightDebug.CASTING, "[cantillation] {}: {} notes of {} heard, as {}",
                    caster.getGameProfile().getName(), phrase.notes.size(), phrase.heard, cantillation.id());
            }
        }
        HELD.clear();
    }

    private static final class Phrase {
        private final List<CantillationCast.Note> notes = new ArrayList<>();
        private EvalSound loudest = HexEvalSounds.NOTHING;
        private int heard;

        void add(CantillationCast.Note note, EvalSound sound) {
            if (notes.size() < PHRASE) {
                notes.add(note);
            }
            loudest = loudest.greaterOf(sound);
            heard++;
        }
    }
}
