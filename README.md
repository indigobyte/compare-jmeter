# JMeter result comparison

## Usage 

    java -jar compare-jmeter.jar <reference.csv> <test.csv> <comparison.json> <comparison.xlsx>

| Parameter | Description |
---|---
|`<reference.csv>`|File containing reference measurement results in CSV format.|
|`<test.csv>`|File containing measurement results that must be compared against the reference ones.|
|`<comparison.json>`|File to write comparison results to in JSON format.|
|`<comparison.xlsx>`|File to write comparison results to in XSLX format.|

## Building

Run `mvn clean package`

Result is contained in `target/compare-jmeter.jar`.
