package com.bluup.hexwright.server.cantillation;

import com.bluup.hexwright.common.cantillation.Cantillation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record CantillationCast(Cantillation cantillation, int casterId, Vec3 pos, List<Note> notes) {

    public enum Kind {
        NOTE,
        SPELL,
        META,
        ESCAPED
    }

    public record Note(int key, Kind kind) {
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(cantillation);
        buf.writeVarInt(casterId);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeCollection(notes, (out, note) -> {
            out.writeByte(note.key());
            out.writeEnum(note.kind());
        });
    }

    public static CantillationCast read(FriendlyByteBuf buf) {
        Cantillation cantillation = buf.readEnum(Cantillation.class);
        int casterId = buf.readVarInt();
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        List<Note> notes = buf.readList(in -> new Note(in.readUnsignedByte(), in.readEnum(Kind.class)));
        return new CantillationCast(cantillation, casterId, pos, notes);
    }
}
