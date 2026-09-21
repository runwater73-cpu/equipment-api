package dev.equipmentstructure.api.diagnostic;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static dev.equipmentstructure.api.diagnostic.EquipmentDiagnosticReport.Severity.*;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentDiagnosticReportTest {
    @Test void findingsAreDeduplicatedSortedAndImmutable() {
        var error = new EquipmentDiagnosticReport.Issue(ERROR, "broken", "a", "b");
        var info = new EquipmentDiagnosticReport.Issue(INFO, "optional", "a", "");
        var input = new ArrayList<>(List.of(info, error, error));
        var report = new EquipmentDiagnosticReport("test", input);
        input.clear();
        assertEquals(List.of(error, info), report.issues());
        assertThrows(UnsupportedOperationException.class, () -> report.issues().clear());
        assertEquals(1, report.count(ERROR));
    }

    @Test void pagesAreBoundedIncludingEmptyReportsAndLargePageRequests() {
        var issues = new ArrayList<EquipmentDiagnosticReport.Issue>();
        for (int i = 0; i < 25; i++) issues.add(new EquipmentDiagnosticReport.Issue(INFO, "key", "item" + i, ""));
        var report = new EquipmentDiagnosticReport("test", issues);
        assertEquals(3, report.pages());
        assertEquals(10, report.page(-1).size());
        assertEquals(5, report.page(Integer.MAX_VALUE).size());
        assertEquals(issues.size(), report.page(1).size() + report.page(2).size() + report.page(3).size());
        var empty = new EquipmentDiagnosticReport("test", List.of());
        assertEquals(1, empty.pages());
        assertTrue(empty.page(100).isEmpty());
    }
}
