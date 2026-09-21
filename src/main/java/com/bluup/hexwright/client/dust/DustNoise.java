package com.bluup.hexwright.client.dust;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

final class DustNoise {

    static final int SIZE = 64;

    private static int texture;

    private DustNoise() {
    }

    static int texture() {
        if (texture != 0) {
            return texture;
        }
        texture = GL11.glGenTextures();
        ByteBuffer data = MemoryUtil.memAlloc(SIZE * SIZE * SIZE);
        try {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        data.put((byte) value(x, y, z));
                    }
                }
            }
            data.flip();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, SIZE, SIZE, SIZE, 0,
                GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, data);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        } finally {
            MemoryUtil.memFree(data);
        }
        return texture;
    }

    static int value(int x, int y, int z) {
        int h = x * 0x27D4EB2D + y * 0x165667B1 + z * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h >>> 24;
    }
}
