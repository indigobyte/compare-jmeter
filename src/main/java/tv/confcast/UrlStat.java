package tv.confcast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeSet;

public class UrlStat {
    public static final ImmutableSortedSet<Integer> PERCENTILES = ImmutableSortedSet.of(
            50,
            75,
            90,
            95,
            96,
            97,
            98,
            99,
            100
    );
    @NotNull
    private final UrlLatency min;
    @NotNull
    private final UrlLatency max;
    private final int average;
    private final ImmutableSortedMap<Integer, UrlLatency> percentiles;

    public UrlStat(
            @NotNull UrlLatency min,
            @NotNull UrlLatency max,
            int average,
            @NotNull Map<Integer, UrlLatency> percentiles
    ) {
        if (!percentiles.keySet().equals(PERCENTILES)) {
            try {
                throw new IllegalArgumentException("Percentiles do not match! Expected " +
                        new ObjectMapper().writeValueAsString(PERCENTILES) + ", " +
                        "got: " + new ObjectMapper().writeValueAsString(new TreeSet<>(percentiles.keySet()))
                );
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        this.min = min;
        this.max = max;
        this.average = average;
        this.percentiles = ImmutableSortedMap.copyOf(percentiles);
    }

    @NotNull
    public UrlLatency getMin() {
        return min;
    }

    @NotNull
    public UrlLatency getMax() {
        return max;
    }

    public int getAverage() {
        return average;
    }

    @NotNull
    public ImmutableSortedMap<Integer, UrlLatency> getPercentiles() {
        return percentiles;
    }
}
