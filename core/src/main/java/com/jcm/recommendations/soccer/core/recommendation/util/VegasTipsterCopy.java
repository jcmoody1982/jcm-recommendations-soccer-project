package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;

/**
 * AccaBaccaGlory Info blurb voice: Las Vegas tipster that hams it up.
 * Facts stay honest (selection, confidence, numbers, optional colour notes);
 * wrapping gets theatrical. Never invent injuries, motives, or certainty.
 */
public final class VegasTipsterCopy {

    private static final String[] OPENERS = {
            "Lights are bright for %s.",
            "Don't blink — %s is the pick.",
            "Put the chips on %s.",
            "Ring the bell for %s.",
            "Roll it with %s.",
            "Step right up for %s."
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

        StringBuilder sb = new StringBuilder();
        sb.append(opener(brief)).append(' ');
        sb.append(confidenceLine(brief));

        String numbers = numbersLine(brief);
        if (!numbers.isBlank()) {
            sb.append(' ').append(numbers);
        }
        sb.append('.');

        if (brief.colourNote() != null && !brief.colourNote().isBlank()) {
            sb.append(' ').append(trimSentence(brief.colourNote()));
            if (!sb.toString().endsWith(".")) {
                sb.append('.');
            }
        }

        if (brief.context() != null
                && brief.context().getHomeTeam() != null
                && brief.context().getAwayTeam() != null) {
            sb.append(" Showtime: ")
                    .append(brief.context().getHomeTeam().getName())
                    .append(" vs ")
                    .append(brief.context().getAwayTeam().getName())
                    .append('.');
        }

        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String opener(Brief brief) {
        int idx = Math.floorMod(stableHash(brief), OPENERS.length);
        return String.format(OPENERS[idx], brief.selection().trim());
    }

    private static String confidenceLine(Brief brief) {
        ConfidenceLevel level = brief.confidence() != null ? brief.confidence() : ConfidenceLevel.MODERATE;
        if (level == ConfidenceLevel.STRONG) {
            return "This one's a Strong lean from the board";
        }
        if (level == ConfidenceLevel.WEAK) {
            return "A speculative Weak lean from the board";
        }
        return "We're calling a Moderate lean from the board";
    }

    private static String numbersLine(Brief brief) {
        StringBuilder n = new StringBuilder();

        if (brief.probabilityPct() != null && Double.isFinite(brief.probabilityPct())) {
            n.append(String.format("— model's ringing %.1f%%", brief.probabilityPct()));
        } else if (brief.expectedNumber() != null && Double.isFinite(brief.expectedNumber())) {
            String unit = brief.expectedUnit() != null ? brief.expectedUnit() : "on the board";
            n.append(String.format("— we're looking at about %.1f %s", brief.expectedNumber(), unit));
        }

        if (brief.odds() != null && brief.odds() > 1.0) {
            appendClause(n, String.format("priced at %.2f", brief.odds()));
        }
        if (brief.valuePct() != null && brief.valuePct() > 0.05) {
            appendClause(n, String.format("still +%.1f%% of juice on the price", brief.valuePct()));
        }
        if (brief.edge() != null && brief.edge() > 0.05) {
            appendClause(n, String.format("+%.1f edge in the tank", brief.edge()));
        }
        if (brief.expectedValue() != null && Double.isFinite(brief.expectedValue())) {
            appendClause(n, String.format("EV %.3f", brief.expectedValue()));
        }

        return n.toString();
    }

    private static void appendClause(StringBuilder n, String clause) {
        if (n.isEmpty()) {
            n.append("— ").append(clause);
        } else {
            n.append(", ").append(clause);
        }
    }

    private static String trimSentence(String note) {
        String trimmed = note.trim();
        if (trimmed.endsWith(".")) {
            return trimmed;
        }
        return trimmed;
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
        return h;
    }
}
