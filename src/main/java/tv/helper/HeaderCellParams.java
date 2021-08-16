package tv.helper;

import org.jetbrains.annotations.NotNull;

public record HeaderCellParams(
        @NotNull String text,
        int colSpan,
        int rowSpan
) {
    public HeaderCellParams(@NotNull String text) {
        this(text, 1, 1);
    }
}
