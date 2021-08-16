package tv.confcast;

import org.jetbrains.annotations.NotNull;

public record UrlLatency(
        @NotNull String url,
        int latency
) implements Comparable<UrlLatency> {

    @Override
    public int compareTo(@NotNull UrlLatency o) {
        return Integer.compare(latency, o.latency);
    }
}
