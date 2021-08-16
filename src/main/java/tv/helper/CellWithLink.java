package tv.helper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record CellWithLink(
        @NotNull String url,
        @Nullable Object value
) {
}
