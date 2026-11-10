package com.jxparallel.forge;

/** Strict mode: the FXML has something JXML cannot take; {@link #getReport()} lists every finding. */
public final class FxmlConversionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient FxmlReport report;

    FxmlConversionException(FxmlReport report) {
        super(message(report));
        this.report = report;
    }

    private static String message(FxmlReport report) {
        StringBuilder out = new StringBuilder("FXML cannot be converted without loss (strict mode):");
        for (FxmlReport.Entry entry : report.entries()) {
            if (entry.level != FxmlReport.Level.NOTE) {
                out.append('\n').append(entry);
            }
        }
        return out.toString();
    }

    public FxmlReport getReport() {
        return report;
    }
}
