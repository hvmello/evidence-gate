package dev.hvmello.evidencegate.regression;

import java.util.List;

/** Prints the one line each suite is known by, plus the ids of the cases that broke it. */
final class SuiteReport {

    private SuiteReport() { }

    static void print(String id, String name, String value, int total, String target, List<String> failingIds) {
        System.out.println("REGRESSION " + id + " " + name + " fixtures=" + RegressionFixtures.VERSION
                + " value=" + value + " total=" + total + " target=" + target);
        if (!failingIds.isEmpty()) {
            System.out.println("REGRESSION " + id + " failing: " + String.join(", ", failingIds));
        }
        System.out.flush();
    }
}
