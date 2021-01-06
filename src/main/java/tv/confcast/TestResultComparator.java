package tv.confcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import tv.helper.ExcelHelper;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        LinkedHashMap<String, List<Integer>> latencyMap = new LinkedHashMap<>();
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
                        .add(latency);
            }
        }
        LinkedHashMap<String, UrlStat> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : latencyMap.entrySet()) {
            Supplier<IntStream> streamProvider = () -> entry.getValue().stream()
                    .mapToInt(Integer::intValue);
            int average = (int) streamProvider.get()
                    .average()
                    .orElseThrow();
            int median = (int) new Median().evaluate(
                    entry.getValue().stream()
                            .mapToDouble(Integer::doubleValue)
                            .toArray()
            );
            int min = streamProvider.get().min().orElseThrow();
            int max = streamProvider.get().max().orElseThrow();
            result.put(entry.getKey(), new UrlStat(
                    min,
                    max,
                    average,
                    median
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

    private static void writeXlsx(
            @NotNull LinkedHashMap<String, UrlComparison> pageComparisonMap,
            @NotNull Path outputFile,
            @NotNull String sheetName
    ) throws Exception {
        byte[] xlsBytes = ExcelHelper.generateExcelWorkbook(
                sheetName,
                pageComparisonMap.entrySet().stream()
                        .map(entry -> {
                            ArrayList<Object> result = new ArrayList<>();
                            result.add(entry.getKey());
                            int oldAverage = entry.getValue().getReference().getAverage();
                            result.add(oldAverage);
                            int newAverage = entry.getValue().getChanged().getAverage();
                            result.add(newAverage);
                            result.add(newAverage - oldAverage);
                            result.add((newAverage - oldAverage) * 100 / oldAverage);
                            int oldMedian = entry.getValue().getReference().getMedian();
                            result.add(oldMedian);
                            int newMedian = entry.getValue().getChanged().getMedian();
                            result.add(newMedian);
                            result.add(newMedian - oldMedian);
                            result.add((newMedian - oldMedian) * 100 / oldMedian);
                            return result;
                        })
                        .collect(Collectors.toList()),
                "Page",
                "average before, ms",
                "average after, ms",
                "average change, ms",
                "average change, %",
                "median before, ms",
                "median after, ms",
                "median change, ms",
                "median change, %"
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
