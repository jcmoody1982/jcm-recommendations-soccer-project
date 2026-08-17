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
        CLASSIC,
        COLOUR_FIRST,
        BOARD_READ,
        STREET_WISE,
        SHORT_PUNCH,
        CLOSER_LEAD,
        NUMBERS_HOOK,
        ASIDE,
        WHISPER
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
            "The window likes %s tonight.",
            "Hot off the printer: %s.",
            "Somebody cash this vibe — %s.",
            "Ink's still wet on %s.",
            "Raise a glass to %s.",
            "The pit boss whispered %s.",
            "Fresh chalk on %s.",
            "All eyes on %s.",
            "They're talking %s in the lounge.",
            "Slide over — %s just hit the board.",
            "Hold the dice for %s.",
            "Cue the brass section for %s.",
            "Flashbulb moment: %s."
    };

    private static final String[] STRONG_LEANS = {
            "This one's a Strong lean from the board",
            "Strong lean — the board's nodding along",
            "We're shouting Strong on this one",
            "Strong lean stamped on the slip",
            "The house calls this a Strong lean",
            "Strong lean with the chest out",
            "Bold Strong lean — no soft pedaling",
            "We're planting a Strong flag here",
            "Strong lean walking in like it owns the room",
            "Call it Strong and mean it",
            "Strong lean, lights and all",
            "This earns the Strong stamp tonight"
    };

    private static final String[] MODERATE_LEANS = {
            "We're calling a Moderate lean from the board",
            "Moderate lean — worth a look without the fireworks",
            "Board says Moderate; keep the powder dry",
            "A measured Moderate lean tonight",
            "Moderate lean, no parade needed",
            "Moderate lean with the volume at half",
            "Steady Moderate lean from the sheet",
            "We're in Moderate territory — sensible money",
            "Moderate lean, cool hand on the rail",
            "Pencil in a Moderate lean",
            "Moderate lean without the trumpet solo",
            "Call it Moderate and keep walking"
    };

    private static final String[] WEAK_LEANS = {
            "A speculative Weak lean from the board",
            "Weak lean — entertainment money only",
            "Speculative Weak lean if you're feeling spicy",
            "Weak lean on the long-shot ticket",
            "Call it Weak and treat it like popcorn",
            "Weak lean, tiny stake energy",
            "We're whispering Weak on this one",
            "Weak lean for the curious only",
            "A dart-throw Weak lean",
            "Weak lean — don't rearrange the bankroll",
            "Parking a Weak lean on the fringe",
            "Weak lean with a wink, not a wave"
    };

    private static final String[] PROB_PHRASES = {
            "model's ringing %.1f%%",
            "our number sits at %.1f%%",
            "tape has it around %.1f%%",
            "board math says %.1f%%",
            "we're marking %.1f%% on the sheet",
            "probability clocked at %.1f%%",
            "the abacus lands on %.1f%%",
            "we're penciling %.1f%%",
            "hit rate vibes at %.1f%%",
            "sheet says roughly %.1f%%",
            "model temperature: %.1f%%",
            "we're hanging %.1f%% on the hook"
    };

    private static final String[] EXPECTED_PHRASES = {
            "we're looking at about %.1f %s",
            "projection lands near %.1f %s",
            "expect something like %.1f %s",
            "the total's hanging around %.1f %s",
            "card projects ~%.1f %s",
            "we're sniffing roughly %.1f %s",
            "forecast parks near %.1f %s",
            "number crunch points to %.1f %s",
            "ballpark: %.1f %s",
            "the line of best fit says %.1f %s",
            "we're floating ~%.1f %s",
            "expectancy humming around %.1f %s"
    };

    private static final String[] VALUE_PHRASES = {
            "still +%.1f%% of juice on the price",
            "+%.1f%% value left on the bone",
            "price still leaking +%.1f%%",
            "juice check: +%.1f%%",
            "mispriced to the tune of +%.1f%%",
            "+%.1f%% hanging off the ticket",
            "value drip at +%.1f%%",
            "the price owes you +%.1f%%",
            "+%.1f%% of daylight vs the market",
            "still +%.1f%% fat on this number",
            "overlay roughly +%.1f%%",
            "market lagging by +%.1f%%"
    };

    private static final String[] EDGE_PHRASES = {
            "+%.1f edge in the tank",
            "+%.1f of edge riding shotgun",
            "edge meter at +%.1f",
            "+%.1f edge still breathing",
            "we're packing +%.1f edge",
            "+%.1f edge tucked in the cuff",
            "edge ledger shows +%.1f",
            "+%.1f of cushion on the model"
    };

    private static final String[] ODDS_PHRASES = {
            "priced at %.2f",
            "window showing %.2f",
            "ticket sits at %.2f",
            "listed %.2f on the board",
            "market hanging %.2f",
            "you're getting %.2f",
            "the number's %.2f",
            "chalked at %.2f"
    };

    private static final String[] EV_PHRASES = {
            "EV %.3f",
            "expected value %.3f",
            "EV sitting at %.3f",
            "EV humming %.3f",
            "long-run EV %.3f",
            "value engine reads EV %.3f"
    };

    private static final String[] CLOSERS = {
            "Showtime: %s vs %s.",
            "Tonight's card: %s vs %s.",
            "Under the lights: %s vs %s.",
            "The marquee: %s vs %s.",
            "Kickoff billing: %s vs %s.",
            "Main event: %s vs %s.",
            "On the strip: %s vs %s.",
            "Centre stage: %s vs %s.",
            "The matchup: %s vs %s.",
            "Feature bout: %s vs %s.",
            "Tonight's duel: %s vs %s.",
            "Bill it as %s vs %s."
    };

    private static final String[] COLOUR_BRIDGES = {
            "%s",
            "Colour on the tape: %s",
            "Here's the juice: %s",
            "Read the room — %s",
            "Side note from the floor: %s",
            "Floor gossip: %s",
            "And the subplot — %s",
            "Between the lines: %s",
            "Bonus reel: %s",
            "Why it sings: %s",
            "The texture: %s",
            "Whisper from the rail: %s"
    };

    private static final String[] SELECTION_HOOKS = {
            "We're sliding the chips toward %s.",
            "Circle %s in red ink.",
            "The play is %s.",
            "We're riding %s.",
            "Ticket stub says %s.",
            "Park it on %s.",
            "Our horse is %s.",
            "Bank the angle on %s.",
            "We're cashing the vibe on %s.",
            "Lean the stack into %s."
    };

    private static final String[] ASIDE_OPENERS = {
            "Quick aside from the tipster desk.",
            "One for the late ticket writers.",
            "A little theatre before kickoff.",
            "From the smoke-filled lounge:",
            "Pass it down the rail —",
            "For those keeping a lively card:"
    };

    private static final String[] WHISPER_OPENERS = {
            "Lean in.",
            "Between you and me — hold this close.",
            "Soft voice, loud number.",
            "Don't make a scene.",
            "Quiet tip from the corner.",
            "Cupped hand from the rail."
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
        if (!hasColour(brief) && (layout == Layout.COLOUR_FIRST || layout == Layout.STREET_WISE)) {
            layout = pick(new Layout[]{Layout.CLASSIC, Layout.BOARD_READ, Layout.SHORT_PUNCH,
                    Layout.CLOSER_LEAD, Layout.NUMBERS_HOOK, Layout.ASIDE, Layout.WHISPER}, hash, 11);
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
            case BOARD_READ -> joinSentences(
                    leanWithNumbers(lean, numbers),
                    selectionHook,
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
                    numbersSentence,
                    colour,
                    closer
            );
            case CLOSER_LEAD -> joinSentences(
                    closer,
                    opener,
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
            case WHISPER -> joinSentences(
                    pick(WHISPER_OPENERS, hash, 14),
                    leanWithNumbers(lean + " — " + selection, numbers),
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
