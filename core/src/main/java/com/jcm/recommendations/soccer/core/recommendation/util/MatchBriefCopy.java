package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;

/**
 * AccaBaccaGlory Info blurb voice: warmer British analytical newspaper tone
 * (Racing Post / broadsheet preview). Facts stay honest (selection, confidence,
 * numbers, optional colour notes); wrapping varies by layout so a list of picks
 * does not read like the same sentence on loop.
 * <p>
 * Prefer "the analysis" and "the team"; never invent injuries, motives, certainty,
 * or reference an "API" / "the model" in user-facing copy.
 */
public final class MatchBriefCopy {

    private enum Layout {
        CLASSIC,
        COLOUR_FIRST,
        ANALYSIS_LEAD,
        FIXTURE_LEAD,
        SHORT_PUNCH,
        CLOSER_LEAD,
        NUMBERS_HOOK,
        ASIDE,
        CAUTION,
        TEAM_ANGLE,
        SELECTION_HOOK,
        BRIEF_NOTE
    }

    private static final String[] OPENERS = {
            "%s looks the right side of the ledger.",
            "%s is the selection of interest.",
            "The case for %s is tidy enough to note.",
            "%s stands out on this card.",
            "Interest settles on %s.",
            "The analysis leans toward %s.",
            "%s is the pick we are writing up.",
            "A quiet mark against %s.",
            "%s is where the sheet is pointing.",
            "Worth a proper look at %s.",
            "The preference here is %s.",
            "%s carries the stronger brief.",
            "Circle %s for further reading.",
            "The angle of the day is %s.",
            "%s has the cleaner narrative.",
            "We are with %s on the evidence.",
            "%s fits the shape of the match.",
            "Put the pencil on %s.",
            "%s is the line that holds up.",
            "The team brief supports %s.",
            "%s looks sound against the field.",
            "A measured case for %s.",
            "%s is the standout selection.",
            "The reading favours %s."
    };

    private static final String[] STRONG_LEANS = {
            "Strong grading from the analysis",
            "Strong conviction on the sheet",
            "This earns a Strong stamp",
            "Strong lean without hedging",
            "The analysis is firmly Strong",
            "Strong read — the numbers agree",
            "Strong grading, chest out but calm",
            "We are planting a Strong flag here",
            "Strong lean with little ambiguity",
            "Call it Strong and mean it",
            "Strong conviction on the evidence",
            "The brief is Strong end to end",
            "Strong mark — the analysis is clear",
            "A full Strong grading tonight",
            "Strong lean from first principles",
            "The analysis stamps this Strong"
    };

    private static final String[] MODERATE_LEANS = {
            "Moderate grading from the analysis",
            "A measured Moderate lean",
            "Moderate — interested, not forced",
            "Moderate lean with the volume half down",
            "Steady Moderate reading on the sheet",
            "Moderate territory; keep perspective",
            "A sensible Moderate lean",
            "Moderate grading — worth the note",
            "Pencil in a Moderate lean",
            "Moderate, without the fanfare",
            "The analysis settles on Moderate",
            "Moderate lean, cool and tidy",
            "Call it Moderate and move on",
            "Moderate conviction on the brief",
            "A balanced Moderate grading",
            "Moderate lean from the analysis"
    };

    private static final String[] WEAK_LEANS = {
            "A speculative Weak lean only",
            "Weak grading — exploratory rather than core",
            "Weak lean; notice it, do not build around it",
            "Weak case on the fringe of the card",
            "Call it Weak and treat it lightly",
            "Weak lean for the curious pencil",
            "A quiet Weak aside from the analysis",
            "Weak grading — thin on the wash",
            "Speculative Weak lean at best",
            "Weak mark; park it as colour, not centrepiece",
            "The analysis whispers Weak here",
            "Weak lean without rearranging the card",
            "A dart-throw Weak lean",
            "Weak conviction — keep it peripheral",
            "Weak grading from a thin brief",
            "Only a Weak lean on the evidence"
    };

    private static final String[] PROB_PHRASES = {
            "the analysis has it at %.1f%%",
            "probability sits at %.1f%%",
            "the sheet reads %.1f%%",
            "we are marking %.1f%%",
            "graded around %.1f%%",
            "the brief clocks %.1f%%",
            "probability pencilled at %.1f%%",
            "the reading lands near %.1f%%",
            "analysis figure: %.1f%%",
            "roughly %.1f%% on the sheet",
            "the number comes in at %.1f%%",
            "hung at %.1f%% by the analysis",
            "standing at %.1f%%",
            "the work puts it at %.1f%%",
            "assessment: %.1f%%",
            "%.1f%% on the current brief"
    };

    private static final String[] EXPECTED_PHRASES = {
            "projection near %.1f %s",
            "expect something like %.1f %s",
            "the total hangs around %.1f %s",
            "the analysis projects ~%.1f %s",
            "forecast parks near %.1f %s",
            "looking at about %.1f %s",
            "ballpark %.1f %s",
            "the brief floats ~%.1f %s",
            "expectancy around %.1f %s",
            "projected figure: %.1f %s",
            "the work points to %.1f %s",
            "roughly %.1f %s on the sheet",
            "analysis expectancy %.1f %s",
            "we are around %.1f %s",
            "the reading suggests %.1f %s",
            "near %.1f %s by the brief"
    };

    private static final String[] VALUE_PHRASES = {
            "+%.1f%% still left against the listed price",
            "an estimated +%.1f%% on the price",
            "+%.1f%% residual versus the market",
            "price looks soft by about +%.1f%%",
            "+%.1f%% of daylight in the price",
            "the price still offers +%.1f%%",
            "+%.1f%% hanging on the number",
            "market lag roughly +%.1f%%",
            "+%.1f%% of interest versus the board",
            "listed price trailing by +%.1f%%",
            "+%.1f%% value on the brief",
            "about +%.1f%% still in the price",
            "price edge near +%.1f%%",
            "+%.1f%% against the chalk",
            "a +%.1f%% gap versus the market",
            "+%.1f%% left to work with"
    };

    private static final String[] EDGE_PHRASES = {
            "+%.1f of edge on the brief",
            "edge marked at +%.1f",
            "+%.1f edge in the reading",
            "the analysis shows +%.1f edge",
            "+%.1f of cushion on the sheet",
            "edge ledger at +%.1f",
            "+%.1f edge still intact",
            "packing +%.1f of edge",
            "+%.1f on the edge column",
            "edge assessed at +%.1f",
            "+%.1f of analytical edge",
            "brief edge of +%.1f"
    };

    private static final String[] ODDS_PHRASES = {
            "priced at %.2f",
            "listed at %.2f",
            "the price sits at %.2f",
            "available at %.2f",
            "market number %.2f",
            "chalked at %.2f",
            "the board shows %.2f",
            "hanging at %.2f",
            "ticket at %.2f",
            "current price %.2f",
            "quoted %.2f",
            "standing at %.2f"
    };

    private static final String[] EV_PHRASES = {
            "expected value %.3f",
            "EV at %.3f",
            "long-run EV %.3f",
            "EV reading %.3f",
            "expected value sits at %.3f",
            "EV on the brief %.3f",
            "value engine EV %.3f",
            "EV marked %.3f"
    };

    private static final String[] CLOSERS = {
            "Fixture: %s vs %s.",
            "The match-up: %s vs %s.",
            "%s against %s.",
            "Billing: %s vs %s.",
            "Tonight: %s vs %s.",
            "The fixture reads %s vs %s.",
            "Centre stage: %s vs %s.",
            "On the card: %s vs %s.",
            "%s host %s.",
            "The afternoon: %s vs %s.",
            "Contest: %s vs %s.",
            "This one is %s vs %s.",
            "The brief closes on %s vs %s.",
            "Match: %s vs %s."
    };

    private static final String[] COLOUR_BRIDGES = {
            "%s",
            "Supporting note: %s",
            "Context: %s",
            "Brief aside — %s",
            "Worth adding: %s",
            "Colour on the sheet: %s",
            "And the subplot — %s",
            "Between the lines: %s",
            "Why it holds: %s",
            "Texture: %s",
            "On the team side: %s",
            "From the analysis: %s",
            "Further reading: %s",
            "Quiet note: %s"
    };

    private static final String[] SELECTION_HOOKS = {
            "The play is %s.",
            "We are with %s.",
            "Selection: %s.",
            "The pencil lands on %s.",
            "Our reading is %s.",
            "The team brief points to %s.",
            "Mark %s on the card.",
            "The analysis settles on %s.",
            "Preference: %s.",
            "Write %s into the notes.",
            "The cleaner angle is %s.",
            "Stay with %s."
    };

    private static final String[] ASIDE_OPENERS = {
            "A brief from the desk.",
            "One for the card notes.",
            "A short preview before kick-off.",
            "From the analysis sheet:",
            "For those keeping a lively card:",
            "A measured aside:",
            "Quick note from the brief:",
            "Worth filing under today's angles:"
    };

    private static final String[] CAUTION_OPENERS = {
            "Tempting on paper, thinner in the wash.",
            "Handle with a cool head.",
            "Interesting, not irresistible.",
            "A note of caution first.",
            "Keep the powder dry.",
            "Not a headline — a footnote.",
            "Approach with perspective.",
            "Quiet interest, nothing more."
    };

    private static final String[] TEAM_OPENERS = {
            "The team picture is instructive.",
            "On the team evidence:",
            "Looking at the sides involved:",
            "The team brief is tidy enough.",
            "From the team angle:",
            "The squads tell a story here.",
            "Team context supports the read.",
            "On current team form and shape:"
    };

    private MatchBriefCopy() {}

    public record Brief(
            ConfidenceLevel confidence,
            String selection,
            FixtureContext context,
            Double probabilityPct,
            Double valuePct,
            Double expectedNumber,
            String expectedUnit,
            Double edge,
            Double odds,
            Double expectedValue,
            String colourNote
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private ConfidenceLevel confidence;
            private String selection;
            private FixtureContext context;
            private Double probabilityPct;
            private Double valuePct;
            private Double expectedNumber;
            private String expectedUnit;
            private Double edge;
            private Double odds;
            private Double expectedValue;
            private String colourNote;

            public Builder confidence(ConfidenceLevel confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder selection(String selection) {
                this.selection = selection;
                return this;
            }

            public Builder context(FixtureContext context) {
                this.context = context;
                return this;
            }

            public Builder probabilityPct(Double probabilityPct) {
                this.probabilityPct = probabilityPct;
                return this;
            }

            public Builder valuePct(Double valuePct) {
                this.valuePct = valuePct;
                return this;
            }

            public Builder expected(Double number, String unit) {
                this.expectedNumber = number;
                this.expectedUnit = unit;
                return this;
            }

            public Builder edge(Double edge) {
                this.edge = edge;
                return this;
            }

            public Builder odds(Double odds) {
                this.odds = odds;
                return this;
            }

            public Builder expectedValue(Double expectedValue) {
                this.expectedValue = expectedValue;
                return this;
            }

            public Builder colourNote(String colourNote) {
                this.colourNote = colourNote;
                return this;
            }

            public Brief build() {
                return new Brief(
                        confidence,
                        selection,
                        context,
                        probabilityPct,
                        valuePct,
                        expectedNumber,
                        expectedUnit,
                        edge,
                        odds,
                        expectedValue,
                        colourNote
                );
            }
        }
    }

    public static String narrate(Brief brief) {
        if (brief == null || brief.selection() == null || brief.selection().isBlank()) {
            return "";
        }

        int hash = stableHash(brief);
        Layout layout = pick(Layout.values(), hash, 0);
        if (!hasColour(brief) && (layout == Layout.COLOUR_FIRST || layout == Layout.BRIEF_NOTE)) {
            layout = pick(new Layout[]{
                    Layout.CLASSIC, Layout.ANALYSIS_LEAD, Layout.FIXTURE_LEAD,
                    Layout.SHORT_PUNCH, Layout.CLOSER_LEAD, Layout.NUMBERS_HOOK,
                    Layout.ASIDE, Layout.CAUTION, Layout.TEAM_ANGLE, Layout.SELECTION_HOOK
            }, hash, 11);
        }

        String selection = brief.selection().trim();
        String opener = String.format(pick(OPENERS, hash, 1), selection);
        String lean = confidenceLine(brief, hash);
        String numbers = numbersLine(brief, hash);
        String colour = colourLine(brief, hash);
        String closer = closerLine(brief, hash);
        String selectionHook = String.format(pick(SELECTION_HOOKS, hash, 12), selection);
        String numbersSentence = numbers.isBlank()
                ? null
                : endSentence(capitalize(numbers.replaceFirst("^—\\s*", "")));

        String text = switch (layout) {
            case CLASSIC -> joinSentences(opener, leanWithNumbers(lean, numbers), colour, closer);
            case COLOUR_FIRST -> joinSentences(opener, colour, leanWithNumbers(lean, numbers), closer);
            case ANALYSIS_LEAD -> joinSentences(
                    leanWithNumbers(lean, numbers),
                    selectionHook,
                    colour,
                    closer
            );
            case FIXTURE_LEAD -> joinSentences(
                    closer,
                    opener,
                    leanWithNumbers(lean, numbers),
                    colour
            );
            case SHORT_PUNCH -> joinSentences(
                    opener + " " + lean + ".",
                    numbersSentence,
                    colour,
                    closer
            );
            case CLOSER_LEAD -> joinSentences(
                    closer,
                    selectionHook,
                    leanWithNumbers(lean, numbers),
                    colour
            );
            case NUMBERS_HOOK -> joinSentences(
                    numbersSentence != null
                            ? numbersSentence
                            : endSentence(lean + " on " + selection),
                    selectionHook,
                    numbersSentence != null ? endSentence(lean) : null,
                    colour,
                    closer
            );
            case ASIDE -> joinSentences(
                    pick(ASIDE_OPENERS, hash, 13),
                    opener,
                    leanWithNumbers(lean, numbers),
                    colour,
                    closer
            );
            case CAUTION -> joinSentences(
                    pick(CAUTION_OPENERS, hash, 14),
                    leanWithNumbers(lean + " — " + selection, numbers),
                    colour,
                    closer
            );
            case TEAM_ANGLE -> joinSentences(
                    pick(TEAM_OPENERS, hash, 15),
                    opener,
                    leanWithNumbers(lean, numbers),
                    colour,
                    closer
            );
            case SELECTION_HOOK -> joinSentences(
                    selectionHook,
                    leanWithNumbers(lean, numbers),
                    colour,
                    closer
            );
            case BRIEF_NOTE -> joinSentences(
                    colour,
                    leanWithNumbers(lean + " on " + selection, numbers),
                    closer
            );
        };

        return text.replaceAll("\\s+", " ").trim();
    }

    private static String leanWithNumbers(String lean, String numbers) {
        if (numbers == null || numbers.isBlank()) {
            return endSentence(lean);
        }
        return lean + " " + numbers + ".";
    }

    private static String confidenceLine(Brief brief, int hash) {
        ConfidenceLevel level = brief.confidence() != null ? brief.confidence() : ConfidenceLevel.MODERATE;
        return switch (level) {
            case STRONG -> pick(STRONG_LEANS, hash, 2);
            case WEAK -> pick(WEAK_LEANS, hash, 2);
            case MODERATE -> pick(MODERATE_LEANS, hash, 2);
        };
    }

    private static String numbersLine(Brief brief, int hash) {
        StringBuilder n = new StringBuilder();

        if (brief.probabilityPct() != null && Double.isFinite(brief.probabilityPct())) {
            n.append("— ").append(String.format(pick(PROB_PHRASES, hash, 3), brief.probabilityPct()));
        } else if (brief.expectedNumber() != null && Double.isFinite(brief.expectedNumber())) {
            String unit = brief.expectedUnit() != null ? brief.expectedUnit() : "on the sheet";
            n.append("— ").append(String.format(pick(EXPECTED_PHRASES, hash, 3), brief.expectedNumber(), unit));
        }

        if (brief.odds() != null && brief.odds() > 1.0) {
            appendClause(n, String.format(pick(ODDS_PHRASES, hash, 4), brief.odds()));
        }
        if (brief.valuePct() != null && brief.valuePct() > 0.05) {
            appendClause(n, String.format(pick(VALUE_PHRASES, hash, 5), brief.valuePct()));
        }
        if (brief.edge() != null && brief.edge() > 0.05) {
            appendClause(n, String.format(pick(EDGE_PHRASES, hash, 6), brief.edge()));
        }
        if (brief.expectedValue() != null && Double.isFinite(brief.expectedValue())) {
            appendClause(n, String.format(pick(EV_PHRASES, hash, 7), brief.expectedValue()));
        }

        return n.toString();
    }

    private static String colourLine(Brief brief, int hash) {
        if (!hasColour(brief)) {
            return null;
        }
        String note = trimSentence(brief.colourNote());
        String bridge = pick(COLOUR_BRIDGES, hash, 8);
        if ("%s".equals(bridge)) {
            return endSentence(note);
        }
        return endSentence(String.format(bridge, stripTrailingPeriod(note)));
    }

    private static String closerLine(Brief brief, int hash) {
        if (brief.context() == null
                || brief.context().getHomeTeam() == null
                || brief.context().getAwayTeam() == null) {
            return null;
        }
        return String.format(
                pick(CLOSERS, hash, 9),
                brief.context().getHomeTeam().getName(),
                brief.context().getAwayTeam().getName()
        );
    }

    private static boolean hasColour(Brief brief) {
        return brief.colourNote() != null && !brief.colourNote().isBlank();
    }

    private static void appendClause(StringBuilder n, String clause) {
        if (n.isEmpty()) {
            n.append("— ").append(clause);
        } else {
            n.append(", ").append(clause);
        }
    }

    private static String joinSentences(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String cleaned = part.trim();
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(cleaned);
        }
        return sb.toString().trim();
    }

    private static String endSentence(String text) {
        String trimmed = text.trim();
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return trimmed;
        }
        return trimmed + ".";
    }

    private static String stripTrailingPeriod(String note) {
        String trimmed = note.trim();
        if (trimmed.endsWith(".")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String trimSentence(String note) {
        return note.trim();
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static <T> T pick(T[] options, int hash, int salt) {
        int idx = Math.floorMod(hash * 31 + salt * 17, options.length);
        return options[idx];
    }

    private static int stableHash(Brief brief) {
        int h = brief.selection().hashCode();
        if (brief.context() != null && brief.context().getFixture() != null
                && brief.context().getFixture().getId() != null) {
            h = 31 * h + Long.hashCode(brief.context().getFixture().getId());
        }
        if (brief.confidence() != null) {
            h = 31 * h + brief.confidence().hashCode();
        }
        if (brief.probabilityPct() != null) {
            h = 31 * h + Double.hashCode(Math.rint(brief.probabilityPct() * 10));
        } else if (brief.expectedNumber() != null) {
            h = 31 * h + Double.hashCode(Math.rint(brief.expectedNumber() * 10));
        }
        return h;
    }
}
