package tv.confcast;

import org.jetbrains.annotations.NotNull;

public class UrlComparison {
    @NotNull
    private final UrlStat reference;
    @NotNull
    private final UrlStat changed;

    public UrlComparison(@NotNull UrlStat reference, @NotNull UrlStat changed) {
        this.reference = reference;
        this.changed = changed;
    }

    @NotNull
    public UrlStat getReference() {
        return reference;
    }

    @NotNull
    public UrlStat getChanged() {
        return changed;
    }
}
