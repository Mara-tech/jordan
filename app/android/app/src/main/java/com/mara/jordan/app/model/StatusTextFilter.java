package com.mara.jordan.app.model;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

import java.util.Locale;

/**
 * Text a status must contain to be displayed: a keyword, or a regular expression the status
 * matches somewhere. Both ignore case. The filter dialog of the Status tab sets one (JRD-10); the
 * search of the toolbar is a keyword one, and a status is displayed when it passes both.
 *
 * <p>Regular expressions are RE2's ({@code com.google.re2j}), not {@code java.util.regex}'s. On
 * Android the latter runs natively in ICU, where a pattern such as {@code (a+)+$} backtracks for
 * minutes on a long status, on the UI thread, with nothing able to interrupt it. RE2 matches in a
 * time linear in the length of the text, whatever the pattern — the price is that backreferences
 * and lookarounds are refused, as syntax errors.
 */
public final class StatusTextFilter {

    /**
     * No text to contain : every status passes.
     */
    public static final StatusTextFilter NONE = keyword(null);

    private final String text;
    private final boolean regex;
    /**
     * Set in regex mode only.
     */
    private final Pattern pattern;
    /**
     * Set in keyword mode only.
     */
    private final String lowerCaseKeyword;

    private StatusTextFilter(String text, boolean regex, Pattern pattern, String lowerCaseKeyword) {
        this.text = text;
        this.regex = regex;
        this.pattern = pattern;
        this.lowerCaseKeyword = lowerCaseKeyword;
    }

    /**
     * @param text taken literally ; {@code null} or empty lets every status through
     */
    public static StatusTextFilter keyword(String text) {
        String nonNull = text == null ? "" : text;
        return new StatusTextFilter(nonNull, false, null, nonNull.toLowerCase(Locale.ROOT));
    }

    /**
     * @param text an RE2 pattern ; {@code null} or empty lets every status through
     * @throws PatternSyntaxException when the pattern is invalid, or uses a construct RE2 does
     *                                not support ; {@link PatternSyntaxException#getDescription()}
     *                                says which
     */
    public static StatusTextFilter regex(String text) throws PatternSyntaxException {
        String nonNull = text == null ? "" : text;
        return new StatusTextFilter(nonNull, true, Pattern.compile(nonNull, Pattern.CASE_INSENSITIVE), null);
    }

    public static StatusTextFilter of(String text, boolean regex) throws PatternSyntaxException {
        return regex ? regex(text) : keyword(text);
    }

    /**
     * @param statusText the text of a status ; {@code null} is read as empty
     */
    public boolean matches(String statusText) {
        if (text.isEmpty()) {
            return true;
        }
        String nonNull = statusText == null ? "" : statusText;
        if (regex) {
            return pattern.matcher(nonNull).find();
        }
        return nonNull.toLowerCase(Locale.ROOT).contains(lowerCaseKeyword);
    }

    public String getText() {
        return text;
    }

    public boolean isRegex() {
        return regex;
    }
}
