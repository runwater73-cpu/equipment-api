package dev.equipmentstructure.api.client;

/** Opaque panels hide the board lattice; selection lightens the component's own color. */
record GridPartColors(int fill, int outline, int marker) {
    static GridPartColors of(int rgb, boolean selected, boolean dragging) {
        return new GridPartColors(mix(0x182129, rgb, dragging ? .28 : selected ? .62 : .43),
                mix(rgb, 0xFFFFFF, selected ? .65 : .25), 0xFF000000 | rgb);
    }
    private static int mix(int a, int b, double amount) {
        int result = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= (int) Math.round(((a >> shift) & 255) * (1 - amount) + ((b >> shift) & 255) * amount) << shift;
        return result;
    }
}
