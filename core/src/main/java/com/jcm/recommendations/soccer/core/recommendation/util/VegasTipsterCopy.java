package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;

/**
 * AccaBaccaGlory Info blurb voice: Las Vegas tipster that hams it up.
 * Facts stay honest (selection, confidence, numbers, optional colour notes);
 * wrapping gets theatrical and varies by layout so a list of picks doesn't
 * read like the same sentence on loop. Never invent injuries, motives, or certainty.
 */
public final class VegasTipsterCopy {

    private enum Layout {
        /** Opener → lean → numbers → colour → closer */
        CLASSIC,
        /** Opener → colour → lean → numbers → closer */
        COLOUR_FIRST,
        /** Lean + numbers up front, then theatrical closer with selection */
        BOARD_READ,
        /** Colour as the hook, selection woven into the lean */
        STREET_WISE,
        /** Short punch: opener packs selection + lean, numbers trail */
        SHORT_PUNCH
    }

    private static final String[] OPENERS = {
            "Lights are bright for %s.",
            "Don't blink — %s is the pick.",
            "Put the chips on %s.",
            "Ring the bell for %s.",
            "Roll it with %s.",
            "Step right up for %s.",
            "The neon's pointing at %s.",
            "Deal me %s.",
            "Crowd's buzzing about %s.",
            "Tape this one: %s.",
            "Mark the card — %s.",
            "The window likes %s tonight."
    };

    private static final String[] STRONG_LEANS = {
            "This one's a Strong lean from the board",
            "Strong lean — the board's nodding along",
            "We're shouting Strong on this one",
            "Strong lean stamped on the slip",
            "The house calls this a Strong lean"
    };

    private static final String[] MODERATE_LEANS = {
            "We're calling a Moderate lean from the board",
            "Moderate lean — worth a look without the fireworks",
            "Board says Moderate; keep the powder dry",
            "A measured Moderate lean tonight",
            "Moderate lean, no parade needed"
    };

    private static final String[] WEAK_LEANS = {
            "A speculative Weak lean from the board",
            "Weak lean — entertainment money only",
            "Speculative Weak lean if you're feeling spicy",
            "Weak lean on the long-shot ticket",
            "Call it Weak and treat it like popcorn"
    };

    private static final String[] PROB_PHRASES = {
            "model's ringing %.1f%%",
            "our number sits at %.1f%%",
            "tape has it around %.1f%%",
            "board math says %.1f%%",
            "we're marking %.1f%% on the sheet"
    };

    private static final String[] EXPECTED_PHRASES = {
            "we're looking at about %.1f %s",
            "projection lands near %.1f %s",
            "expect something like %.1f %s",
            "the total's hanging around %.1f %s",
            "card projects ~%.1f %s"
    };

    private static final String[] VALUE_PHRASES = {
            "still +%.1f%% of juice on the price",
            "+%.1f%% value left on the bone",
            "price still leaking +%.1f%%",
            "juice check: +%.1f%%",
            "mispriced to the tune of +%.1f%%"
    };

    private static final String[] EDGE_PHRASES = {
            "+%.1f edge in the tank",
            "+%.1f of edge riding shotgun",
            "edge meter at +%.1f",
            "+%.1f edge still breathing"
    };

    private static final String[] ODDS_PHRASES = {
            "priced at %.2f",
            "window showing %.2f",
            "ticket sits at %.2f",
            "listed %.2f on the board"
    };

    private static final String[] EV_PHRASES = {
            "EV %.3f",
            "expected value %.3f",
            "EV sitting at %.3f"
    };

    private static final String[] CLOSERS = {
            "Showtime: %s vs %s.",
            "Tonight's card: %s vs %s.",
            "Under the lights: %s vs %s.",
            "The marquee: %s vs %s.",
            "Kickoff billing: %s vs %s.",
            "Main event: %s vs %s."
    };

    private static final String[] COLOUR_BRIDGES = {
            "%s",
            "Colour on the tape: %s",
            "Here's the juice: %s",
            "Read the room — %s",
            "Side note from the floor: %s"
    };

    private VegasTipsterCopy() {}

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
        // Prefer colour-forward layouts when we actually have colour to sell.
        if (!hasColour(brief) && (layout == Layout.COLOUR_FIRST || layout == Layout.STREET_WISE)) {
            layout = Layout.CLASSIC;
        }

        String selection = brief.selection().trim();
        String opener = String.format(pick(OPENERS, hash, 1), selection);
        String lean = confidenceLine(brief, hash);
        String numbers = numbersLine(brief, hash);
        String colour = colourLine(brief, hash);
        String closer = closerLine(brief, hash);

        String text = switch (layout) {
            case CLASSIC -> joinSentences(opener, leanWithNumbers(lean, numbers), colour, closer);
            case COLOUR_FIRST -> joinSentences(opener, colour, leanWithNumbers(lean, numbers), closer);
            case BOARD_READ -> joinSentences(
                    leanWithNumbers(lean, numbers),
                    "We're sliding the chips toward " + selection + ".",
                    colour,
                    closer
            );
            case STREET_WISE -> joinSentences(
                    colour,
                    leanWithNumbers(lean + " on " + selection, numbers),
                    closer
            );
            case SHORT_PUNCH -> joinSentences(
                    opener + " " + lean + ".",
                    numbers.isBlank() ? null : capitalize(numbers.replaceFirst("^—\\s*", "")),
                    colour,
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
            String unit = brief.expectedUnit() != null ? brief.expectedUnit() : "on the board";
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
        // Keep the raw note when tests/engines rely on exact keywords;
        // only sometimes wrap it with a bridge so variety stays optional.
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
            if (!cleaned.endsWith(".") && !cleaned.endsWith("!") && !cleaned.endsWith("?")) {
                // leave as-is; callers usually punctuate
            }
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
        // Nudge variety when the same selection shows with different numbers.
        if (brief.probabilityPct() != null) {
            h = 31 * h + Double.hashCode(Math.rint(brief.probabilityPct() * 10));
        } else if (brief.expectedNumber() != null) {
            h = 31 * h + Double.hashCode(Math.rint(brief.expectedNumber() * 10));
        }
        return h;
    }
}
