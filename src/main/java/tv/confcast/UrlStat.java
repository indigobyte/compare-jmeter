package tv.confcast;

public class UrlStat {
    private final int min;
    private final int max;
    private final int average;
    private final int median;

    public UrlStat(int min, int max, int average, int median) {
        this.min = min;
        this.max = max;
        this.average = average;
        this.median = median;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public int getAverage() {
        return average;
    }

    public int getMedian() {
        return median;
    }
}
