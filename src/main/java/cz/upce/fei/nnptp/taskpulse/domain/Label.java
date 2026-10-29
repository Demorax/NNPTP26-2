package cz.upce.fei.nnptp.taskpulse.domain;

import cz.upce.fei.nnptp.taskpulse.domain.exception.ValidationException;

import java.util.Locale;

public final class Label {
    private Label() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Label must not be blank");
        }
        if (raw.contains(";")) {
            throw new ValidationException("Label must not contain ';', got: '" + raw + "'");
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }
}
