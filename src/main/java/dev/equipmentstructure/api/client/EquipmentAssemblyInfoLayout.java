package dev.equipmentstructure.api.client;

import java.util.List;

/** Shared vertical metrics for side information panels. */
public final class EquipmentAssemblyInfoLayout {
    private static final java.util.regex.Pattern NUMERIC_VALUE =
            java.util.regex.Pattern.compile("[-+0-9.,/% ×x:()]+", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);
    public static final int MIN_HEIGHT = 64;
    /** Content is not capped; the player's GUI scale controls visibility. */
    public static final int MAX_HEIGHT = Integer.MAX_VALUE;
    public static final int LINE_HEIGHT = 14;
    /** Compact rhythm for the fixed five-row equipment attribute panel. */
    public static final int STAT_LINE_HEIGHT = 9;
    public static final int STAT_FIRST_LINE_OFFSET = 18;
    private static final int TOP_AND_BOTTOM_PADDING = 8;

    private EquipmentAssemblyInfoLayout() {
    }

    /** Fit ordinary numeric values before wrapping; keep long prose at readable full size. */
    public static float statValueScale(String text, int renderedWidth, int availableWidth) {
        if (availableWidth <= 0 || renderedWidth < 0) throw new IllegalArgumentException("Invalid text width");
        if (renderedWidth <= availableWidth || !NUMERIC_VALUE.matcher(text).matches()) return 1.0F;
        return Math.max(0.75F, (float) availableWidth / renderedWidth);
    }

    /** Returns a height that grows only when an author adds wrapped information lines. */
    public static int heightForLines(int lineCount) {
        if (lineCount < 0) throw new IllegalArgumentException("Line count cannot be negative");
        return Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT,
                TOP_AND_BOTTOM_PADDING + lineCount * LINE_HEIGHT));
    }

    /** Height for the compact, fixed-row equipment attribute panel. */
    public static int heightForStatLines(int lineCount) {
        if (lineCount < 0) throw new IllegalArgumentException("Line count cannot be negative");
        int lastLineBottom = STAT_FIRST_LINE_OFFSET + Math.max(0, lineCount - 1) * STAT_LINE_HEIGHT + 9;
        return Math.max(MIN_HEIGHT, lastLineBottom + TOP_AND_BOTTOM_PADDING - 9);
    }

    /** Compatibility overload; side panels are intentionally not clipped. */
    public static int heightForStatLines(int lineCount, int maxHeight) {
        if (maxHeight <= 0) throw new IllegalArgumentException("Maximum height must be positive");
        return heightForStatLines(lineCount);
    }

    /**
     * Computes the height of a row-based information panel. Each row may
     * consume more than one line when an author supplies longer translated
     * labels or values. The old maximum-height argument is retained for
     * source compatibility but no longer limits the result.
     */
    public static int heightForStatRows(List<Integer> rowLineCounts, int maxHeight) {
        if (rowLineCounts == null || rowLineCounts.isEmpty()) {
            throw new IllegalArgumentException("At least one information row is required");
        }
        if (maxHeight <= 0) throw new IllegalArgumentException("Maximum height must be positive");
        int lines = 0;
        for (Integer count : rowLineCounts) {
            if (count == null || count < 1) {
                throw new IllegalArgumentException("Information row line count must be positive");
            }
            lines += count;
        }
        // Keep the title and a full bottom inset clear of the nine-slice border.
        int requested = STAT_FIRST_LINE_OFFSET + lines * STAT_LINE_HEIGHT + 9;
        return Math.max(MIN_HEIGHT, requested);
    }

    /** Default compact height used before content-specific measurement. */
    public static int heightForComponentInfo() {
        return 58;
    }

    /**
     * Computes a panel height from wrapped content. The panel is allowed to
     * extend below the nominal GUI height so the vanilla GUI scale remains the
     * only visibility control.
     */
    public static int heightForComponentInfo(int lineCount, int maxHeight) {
        if (lineCount < 0) throw new IllegalArgumentException("Line count cannot be negative");
        if (maxHeight <= 0) throw new IllegalArgumentException("Maximum height must be positive");
        return heightForComponentInfo(lineCount);
    }

    /** Computes a height with the full text area inside the panel border. */
    public static int heightForComponentInfo(int lineCount) {
        if (lineCount < 0) throw new IllegalArgumentException("Line count cannot be negative");
        // Nine-pixel text with a nine-pixel top inset and an eight-pixel
        // bottom inset. The minimum keeps an empty frame balanced while the
        // formula guarantees the final line never touches the nine-slice
        // border.
        return Math.max(42, 27 + Math.max(1, lineCount) * 9);
    }
}
