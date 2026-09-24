package com.oskott.dogtrainerbackend.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

public final class SupportedLocale {

    public static final String ENGLISH = "en";
    public static final String BOKMAL = "nb";

    private SupportedLocale() {
    }

    public static String current() {
        return normalize(LocaleContextHolder.getLocale());
    }

    public static String resolve(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) {
            return ENGLISH;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            for (Locale.LanguageRange range : ranges) {
                if (range.getWeight() == 0) {
                    continue;
                }
                if ("*".equals(range.getRange())) {
                    return ENGLISH;
                }
                String language = Locale.forLanguageTag(range.getRange()).getLanguage();
                if (isNorwegian(language)) {
                    return BOKMAL;
                }
                if (ENGLISH.equalsIgnoreCase(language)) {
                    return ENGLISH;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return ENGLISH;
        }
        return ENGLISH;
    }

    public static boolean isBokmal() {
        return BOKMAL.equals(current());
    }

    private static String normalize(Locale locale) {
        return isNorwegian(locale.getLanguage()) ? BOKMAL : ENGLISH;
    }

    private static boolean isNorwegian(String language) {
        return BOKMAL.equalsIgnoreCase(language)
                || "no".equalsIgnoreCase(language)
                || "nn".equalsIgnoreCase(language);
    }
}
