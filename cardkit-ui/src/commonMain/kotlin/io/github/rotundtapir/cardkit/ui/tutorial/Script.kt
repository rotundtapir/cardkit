// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.tutorial

/**
 * The live tutorial state handed to a game screen: the game's scripted [steps], the index of the
 * next human decision, and how to advance it once that decision is taken. [S] is the game's own
 * step type (its bid/discard/play script entries) — all script content stays game-side.
 */
class TutorialScriptState<S>(
    private val steps: List<S>,
    val stepIndex: Int,
    val onAdvance: () -> Unit,
) {
    /** The pending human decision, or null once the scripted hand is over. */
    val step: S? get() = steps.getOrNull(stepIndex)
}

/** One narrated tutorial line: [id] is the stable stem of its audio asset (files/narration/<id>.mp3). */
data class NarrationLine(val id: String, val text: String)

/** Card notation as the table writes it: "J♠", "10♥", "A♦"… */
private val CARD_NOTATION = Regex("(10|[2-9AKQJ])([♠♥♦♣])")

/** "+140" score deltas, spoken as "plus 140". */
private val PLUS_POINTS = Regex("\\+(\\d+)")

/**
 * An all-caps EMPHASIS word ("JACKS OUTRANK THE ACE", "BLACK"). Synthesizers read these as
 * acronyms or letter-spell them ("B-lack"); spoken text folds them to lowercase. Runs after the
 * notation rules, so game notation (e.g. 500's "NT") has already been expanded by then.
 */
private val SHOUTED_WORD = Regex("\\b[A-Z]{2,}\\b")

/**
 * Expands card-table notation for a speech synthesizer: "J♠" → "jack of spades", "+140" →
 * "plus 140", and shouted EMPHASIS words folded to lowercase. Everything else is spoken as
 * written.
 *
 * [substitutions] are the game's phrase-level fixes applied first, where synthesizers stumble on
 * the written form (e.g. "score 10 a trick" → "score ten a trick"). RULE: a substitution may only
 * change how the SAME words are rendered (numerals, notation, dropped duplication) — never insert
 * words the screen doesn't show, or the voice audibly diverges for anyone reading along; phrasing
 * problems are fixed in the display text instead.
 *
 * [notationRules] are the game's own notation expansions (e.g. 500's "10NT" → "10 no trumps"),
 * applied after the card notation and BEFORE the shouted-word fold — so an all-caps notation
 * token is expanded rather than lowercased into nonsense.
 */
fun cardSpeechText(
    display: String,
    substitutions: List<Pair<String, String>> = emptyList(),
    notationRules: List<Pair<Regex, (MatchResult) -> String>> = emptyList(),
): String {
    val substituted = substitutions.fold(display) { text, (from, to) -> text.replace(from, to) }
        .replace(CARD_NOTATION) { m ->
            val rank = when (val r = m.groupValues[1]) {
                "A" -> "ace"; "K" -> "king"; "Q" -> "queen"; "J" -> "jack"
                else -> r
            }
            val suit = when (m.groupValues[2]) {
                "♠" -> "spades"; "♥" -> "hearts"; "♦" -> "diamonds"; else -> "clubs"
            }
            "$rank of $suit"
        }
    return notationRules.fold(substituted) { text, (regex, expand) -> text.replace(regex, expand) }
        .replace(PLUS_POINTS) { m -> "plus ${m.groupValues[1]}" }
        .replace(SHOUTED_WORD) { m -> m.value.lowercase() }
}
