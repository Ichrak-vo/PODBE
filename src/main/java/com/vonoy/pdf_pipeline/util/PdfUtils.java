package com.vonoy.pdf_pipeline.util;

import org.springframework.util.StringUtils;

public class PdfUtils {
    private PdfUtils() { }

    private static final String DEFAULT_FILE_NAME = "result.pdf";
    private static final String PDF_EXTENSION = ".pdf";
    public static String resolveFileName(String requestedName) {
        if (!StringUtils.hasText(requestedName)) {
            return DEFAULT_FILE_NAME;
        }

        String sanitized = sanitizeFileName(requestedName);

        if (!sanitized.toLowerCase().endsWith(PDF_EXTENSION)) {
            sanitized += PDF_EXTENSION;
        }

        return sanitized;
    }

    public static String sanitizeFileName(String filename) {
        if (!StringUtils.hasText(filename)) {
            return DEFAULT_FILE_NAME;
        }
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
