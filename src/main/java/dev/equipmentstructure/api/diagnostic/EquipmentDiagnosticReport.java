package dev.equipmentstructure.api.diagnostic;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, stable, localizable findings. An empty report certifies only its documented scope. */
public record EquipmentDiagnosticReport(String scope, List<Issue> issues) {
    public static final int PAGE_SIZE = 10;
    public enum Severity { ERROR, WARNING, INFO }

    public record Issue(Severity severity, String code, String subject, String reference) {
        public Issue {
            Objects.requireNonNull(severity);
            Objects.requireNonNull(code);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(reference);
        }
    }

    public EquipmentDiagnosticReport {
        Objects.requireNonNull(scope);
        issues = issues.stream().distinct().sorted(Comparator.comparing(Issue::severity)
                .thenComparing(Issue::code).thenComparing(Issue::subject).thenComparing(Issue::reference)).toList();
    }

    public long count(Severity severity) { return issues.stream().filter(issue -> issue.severity() == severity).count(); }
    public int pages() { return Math.max(1, (issues.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    public int boundedPage(int requested) { return Math.max(1, Math.min(pages(), requested)); }
    public List<Issue> page(int requested) {
        int start = (boundedPage(requested) - 1) * PAGE_SIZE;
        return issues.subList(start, Math.min(start + PAGE_SIZE, issues.size()));
    }
}
