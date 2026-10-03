package com.mara.jordan.app.model;

import com.google.re2j.PatternSyntaxException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StatusTextFilterTest {

    @Test
    public void noTextLetsEveryStatusThrough() {
        for (StatusTextFilter filter : new StatusTextFilter[]{StatusTextFilter.NONE,
                StatusTextFilter.keyword(""), StatusTextFilter.keyword(null),
                StatusTextFilter.regex(""), StatusTextFilter.regex(null)}) {
            assertTrue(filter.matches("epoch 3 done"));
            assertTrue(filter.matches(""));
            assertTrue(filter.matches(null));
        }
    }

    @Test
    public void keywordIsFoundAnywhereIgnoringCase() {
        StatusTextFilter filter = StatusTextFilter.keyword("Loss");

        assertTrue(filter.matches("held-out loss = 0.6648 (step 3)"));
        assertTrue(filter.matches("LOSS"));
        assertFalse(filter.matches("accuracy = 0.91"));
        assertFalse(filter.matches(null));
    }

    @Test
    public void keywordIsTakenLiterally() {
        StatusTextFilter filter = StatusTextFilter.keyword("loss=0.");

        assertTrue(filter.matches("loss=0.42"));
        assertFalse(filter.matches("loss=0742"));
    }

    @Test
    public void regexIsFoundAnywhereIgnoringCase() {
        StatusTextFilter filter = StatusTextFilter.regex("loss = 0\\.[0-5]");

        assertTrue(filter.matches("held-out LOSS = 0.4012 (step 7)"));
        assertFalse(filter.matches("held-out loss = 0.6648 (step 3)"));
    }

    @Test
    public void regexAnchorsAreHonoured() {
        StatusTextFilter filter = StatusTextFilter.regex("^epoch \\d+$");

        assertTrue(filter.matches("epoch 12"));
        assertFalse(filter.matches("end of epoch 12"));
        assertFalse(filter.matches("epoch 12 done"));
    }

    @Test
    public void filterRemembersHowItWasTyped() {
        StatusTextFilter regex = StatusTextFilter.of("^epoch", true);
        StatusTextFilter keyword = StatusTextFilter.of("^epoch", false);

        assertEquals("^epoch", regex.getText());
        assertTrue(regex.isRegex());
        assertEquals("^epoch", keyword.getText());
        assertFalse(keyword.isRegex());
        assertTrue(keyword.matches("a line holding ^epoch"));
        assertFalse(keyword.matches("epoch 1"));
    }

    @Test
    public void invalidRegexIsRefusedWithItsReason() {
        assertRefused("(epoch", "missing closing )");
        assertRefused("loss[", "missing closing ]");
        assertRefused("*epoch", "missing argument to repetition operator");
    }

    /**
     * RE2 refuses what it cannot match in linear time ; the reason says so rather than failing
     * silently.
     */
    @Test
    public void backreferencesAndLookaroundsAreRefused() {
        assertRefused("(epoch) \\1", "invalid escape sequence");
        assertRefused("epoch(?= done)", "invalid or unsupported Perl syntax");
    }

    @Test
    public void invalidRegexIsAcceptedAsKeyword() {
        StatusTextFilter filter = StatusTextFilter.of("(epoch", false);

        assertTrue(filter.matches("(epoch 3)"));
    }

    /**
     * A pattern java.util.regex backtracks on for ever, even on the JDK 9+ that memoizes the
     * exponential cases such as {@code (a+)+$} : every way of cutting the line in 8 pieces is tried
     * before giving up — over 20 s for 60 letters on JDK 21, and ICU, which runs it on Android, does
     * no better. RE2 runs it in a time linear in the length of the line.
     */
    @Test(timeout = 5000)
    public void catastrophicPatternStaysLinear() {
        StatusTextFilter filter = StatusTextFilter.regex("(.*a){8}b");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            line.append('a');
        }

        assertFalse(filter.matches(line.toString()));
    }

    private static void assertRefused(String pattern, String reason) {
        try {
            StatusTextFilter.regex(pattern);
            fail("accepted : " + pattern);
        } catch (PatternSyntaxException e) {
            assertTrue(e.getDescription(), e.getDescription().contains(reason));
        }
    }
}
