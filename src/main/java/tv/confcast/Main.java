package tv.confcast;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) throws Throwable {
        int exitCode = new CommandLine(new TestResultComparator()).execute(args);
        System.exit(exitCode);
    }
}
