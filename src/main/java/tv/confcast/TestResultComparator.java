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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
    private static Map<String, UrlStat> loadResults(
            @NotNull Path taskFile
    ) throws Exception {
        HashMap<String, List<Integer>> latencyMap = new HashMap<>();
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
        Map<String, UrlStat> result = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : latencyMap.entrySet()) {
            IntStream intStream = entry.getValue().stream()
                    .mapToInt(Integer::intValue);
            int average = (int) intStream
                    .average()
                    .orElseThrow();
            int median = (int) new Median().evaluate(
                    entry.getValue().stream()
                            .mapToDouble(Integer::doubleValue)
                            .toArray()
            );
            int min = intStream.min().orElseThrow();
            int max = intStream.max().orElseThrow();
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
    private static Map<String, UrlComparison> compareResults(
            @NotNull Map<String, UrlStat> referenceResults,
            @NotNull Map<String, UrlStat> resultsToCompare
    ) throws Exception {
        if (!referenceResults.keySet().equals(resultsToCompare.keySet())) {
            throw new IllegalArgumentException("Reference results and results to compare have different page sets");
        }
        Map<String, UrlComparison> result = new HashMap<>();
        for (String page : referenceResults.keySet()) {
            result.put(page, new UrlComparison(
                    referenceResults.get(page),
                    resultsToCompare.get(page)
            ));
        }
        return result;
    }

    private static void writeXlsx(
            @NotNull Map<String, UrlComparison> pageComparisonMap,
            @NotNull Path outputFile,
            @NotNull String sheetName
    ) throws Exception {
        byte[] xlsBytes = ExcelHelper.generateExcelWorkbook(
                sheetName,
                pageComparisonMap.entrySet().stream()
                        .map(entry -> {
                            ArrayList<Object> result = new ArrayList<>();
                            result.add(entry.getKey());
                            result.add(entry.getValue().getReference().getAverage());
                            result.add(entry.getValue().getChanged().getAverage());
                            result.add(entry.getValue().getReference().getMedian());
                            result.add(entry.getValue().getChanged().getMedian());
                            return result;
                        })
                        .collect(Collectors.toList()),
                "Page",
                "average before",
                "average after",
                "median before",
                "median after"
        );
        Files.write(outputFile, xlsBytes);
    }

    @Override
    public Integer call() throws Exception {
        Map<String, UrlComparison> pageComparisonMap = compareResults(
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
