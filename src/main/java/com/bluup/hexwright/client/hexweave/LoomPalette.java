package com.bluup.hexwright.client.hexweave;

public record LoomPalette(int eye, int tail, int trigger, int selected, int selectedFade,
                          int grid, int rim, int label, int number, int insert) {

    public static final LoomPalette DEFAULT = new LoomPalette(0x6FE3D6, 0xC99BFF, 0xF2D98C, 0xFFD27A,
        0xFFF4C9, 0x8FA8D8, 0x9C7BD8, 0xB9A8D8, 0xE9DDFF, 0x64C8FF);

    public static LoomPalette of(int eye, int tail, int trigger, int selected, int selectedFade,
                                 int grid, int rim, int label, int number, int insert) {
        return new LoomPalette(eye & 0xFFFFFF, tail & 0xFFFFFF, trigger & 0xFFFFFF, selected & 0xFFFFFF,
            selectedFade & 0xFFFFFF, grid & 0xFFFFFF, rim & 0xFFFFFF, label & 0xFFFFFF, number & 0xFFFFFF,
            insert & 0xFFFFFF);
    }
}
