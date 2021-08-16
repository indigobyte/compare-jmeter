package tv.confcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tv.helper.CellWithLink;
import tv.helper.ExcelHelper;
import tv.helper.HeaderCellParams;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "compare-jmeter",
        mixinStandardHelpOptions = true,
        version = "compare-jmeter 1.0",
        description = "Compare results obtained as stated in task 3318"
)
public class TestResultComparator implements Callable<Integer> {
    private static final Logger log = LogManager.getLogger(TestResultComparator.class);
    @Parameters(index = "0", description = "Reference JMeter test results")
    private String referenceFile;
    @Parameters(index = "1", description = "JMeter test results to compare")
    private String fileToCompare;
    @Parameters(index = "2", description = "File to write comparison to in JSON format")
    private String comparisonResultsJson;
    @Parameters(index = "3", description = "File to write comparison to in XLS format")
    private String comparisonResultsXls;

    @NotNull
    private static LinkedHashMap<String, UrlStat> loadResults(
            @NotNull Path taskFile
    ) throws Exception {
        LinkedHashMap<String, List<UrlLatency>> latencyMap = new LinkedHashMap<>();
        try (Reader in = new FileReader(taskFile.toFile())) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(in);
            for (CSVRecord record : records) {
                String label = record.get("label");
                String url = record.get("URL");
                int latency = Integer.parseInt(record.get("Latency"));
                String idleTime = record.get("IdleTime");
                String connectTime = record.get("Connect");
                String bytes = record.get("bytes");
                if (label.contains("warm-up")) {
                    continue;
                }
                latencyMap.computeIfAbsent(label, k -> new ArrayList<>())
                        .add(new UrlLatency(url, latency));
            }
        }
        LinkedHashMap<String, UrlStat> result = new LinkedHashMap<>();
        Percentile percentileComputer = new Percentile();
        for (Map.Entry<String, List<UrlLatency>> entry : latencyMap.entrySet()) {
            int average = (int) entry.getValue().stream()
                    .mapToInt(UrlLatency::latency)
                    .average()
                    .orElseThrow();
            final UrlLatency[] values = entry.getValue().stream()
                    .sorted()
                    .toArray(UrlLatency[]::new);
            percentileComputer.setData(Arrays
                    .stream(values)
                    .mapToDouble(UrlLatency::latency)
                    .toArray()
            );
            UrlLatency min = values[0];
            UrlLatency max = values[values.length - 1];
            Map<Integer, UrlLatency> percentiles = new HashMap<>();
            for (int percentile : UrlStat.PERCENTILES) {
                final int computedPercentile = (int) percentileComputer.evaluate(percentile);
                final int foundPos = Arrays.binarySearch(values, new UrlLatency("", computedPercentile));
                percentiles.put(percentile, values[Math.abs(foundPos)]);
            }

            result.put(entry.getKey(), new UrlStat(
                    min,
                    max,
                    average,
                    percentiles
            ));
        }
        return result;
    }

    @NotNull
    private static LinkedHashMap<String, UrlComparison> compareResults(
            @NotNull Map<String, UrlStat> referenceResults,
            @NotNull Map<String, UrlStat> resultsToCompare
    ) throws Exception {
        if (!referenceResults.keySet().equals(resultsToCompare.keySet())) {
            throw new IllegalArgumentException("Reference results and results to compare have different page sets");
        }
        LinkedHashMap<String, UrlComparison> result = new LinkedHashMap<>();
        for (String page : referenceResults.keySet()) {
            result.put(page, new UrlComparison(
                    referenceResults.get(page),
                    resultsToCompare.get(page)
            ));
        }
        return result;
    }

    private static void addColumns(
            @NotNull List<Object> row,
            int oldValue,
            int newValue
    ) {
        row.add(oldValue);
        row.add(newValue);
        row.add(newValue - oldValue);
        row.add((newValue - oldValue) * 100 / oldValue);
    }

    private static void addColumns(
            @NotNull List<Object> row,
           @NotNull UrlLatency oldValue,
           @NotNull UrlLatency newValue
    ) {
        addColumns(row, oldValue.latency(), newValue.latency());
        row.set(row.size() - 4, new CellWithLink(oldValue.url(), oldValue.latency()));
        row.set(row.size() - 3, new CellWithLink(newValue.url(), newValue.latency()));
    }

    private static void writeXlsx(
            @NotNull LinkedHashMap<String, UrlComparison> pageComparisonMap,
            @NotNull Path outputFile,
            @NotNull String sheetName
    ) throws Exception {
        Collection<ArrayList<Object>> rows = new ArrayList<>();
        for (var entry : pageComparisonMap.entrySet()) {
            ArrayList<Object> row = new ArrayList<>();
            row.add(entry.getKey());
            addColumns(
                    row,
                    entry.getValue().getReference().getAverage(),
                    entry.getValue().getChanged().getAverage()
            );
            for (var percentile : UrlStat.PERCENTILES) {
                addColumns(
                        row,
                        entry.getValue().getReference().getPercentiles().get(percentile),
                        entry.getValue().getChanged().getPercentiles().get(percentile)
                );
            }
            rows.add(row);
        }
        List<String> columnHeaders = new ArrayList<>();
        columnHeaders.add("Page");

        List<HeaderCellParams> firstHeaderRow = new ArrayList<>();
        List<HeaderCellParams> secondHeaderRow = new ArrayList<>();
        firstHeaderRow.add(new HeaderCellParams("Page", 1, 2));
        secondHeaderRow.add(null);

        List<String> comparisonHeaderTitles = new ArrayList<>();
        comparisonHeaderTitles.add("Average");

        for (int percentile : UrlStat.PERCENTILES) {
            comparisonHeaderTitles.add(percentile + "%-percentile");
        }

        for (var title : comparisonHeaderTitles) {
            firstHeaderRow.add(new HeaderCellParams(title, 4, 1));
            for (int i = 0; i < 3; ++i) {
                firstHeaderRow.add(null);
            }
            secondHeaderRow.add(new HeaderCellParams("Before, ms"));
            secondHeaderRow.add(new HeaderCellParams("After, ms"));
            secondHeaderRow.add(new HeaderCellParams("Change, ms"));
            secondHeaderRow.add(new HeaderCellParams("Change, %"));
        }

        List<List<HeaderCellParams>> headerRows = List.of(firstHeaderRow, secondHeaderRow);

        byte[] xlsBytes = ExcelHelper.generateExcelWorkbook(
                sheetName,
                rows,
                headerRows
        );
        Files.write(outputFile, xlsBytes);
    }

    @Override
    public Integer call() throws Exception {
        LinkedHashMap<String, UrlComparison> pageComparisonMap = compareResults(
                loadResults(Paths.get(referenceFile)),
                loadResults(Paths.get(fileToCompare))
        );
        new ObjectMapper()
                .writer()
                .writeValue(new File(comparisonResultsJson), pageComparisonMap);
        writeXlsx(
                pageComparisonMap,
                Paths.get(comparisonResultsXls),
                FilenameUtils.getBaseName(referenceFile) + " vs " +
                        FilenameUtils.getBaseName(fileToCompare)
        );
        return 0;
    }
}
