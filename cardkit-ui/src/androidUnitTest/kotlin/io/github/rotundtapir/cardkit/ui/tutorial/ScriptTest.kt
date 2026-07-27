// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.ui.tutorial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [cardSpeechText] pins the notation-to-speech expansion 500's narration pipeline is generated
 * from (its NarrationManifestTest hashes the results, so the output here must stay byte-stable).
 * The 500-specific pieces ride in through the parameters exactly as the migrated game passes them.
 */
class CardSpeechTextTest {

    /** 500's phrase-level substitutions, passed by the game. */
    private val substitutions = listOf(
        "score 10 a trick" to "score ten a trick",
        "seven (7♠ or higher)" to "7♠ or higher",
    )

    /** 500's no-trump bid notation, passed by the game. */
    private val ntRule = Regex("\\b(10|[6-9])NT\\b") to { m: MatchResult -> "${m.groupValues[1]} no trumps" }

    private fun speak(display: String): String =
        cardSpeechText(display, substitutions, listOf(ntRule))

    @Test
    fun `card notation expands to rank of suit`() {
        assertEquals(
            "Everyone else has passed, so bid 7 of spades.",
            speak("Everyone else has passed, so bid 7♠."),
        )
        assertEquals("Cash the ace of hearts", speak("Cash the A♥"))
        assertEquals("the queen of diamonds, king of diamonds and ace of diamonds", speak("the Q♦, K♦ and A♦"))
        assertEquals("Lead the 10 of spades", speak("Lead the 10♠"))
        assertEquals("the jack of clubs is not really a club", speak("the J♣ is not really a club"))
    }

    @Test
    fun `game notation rules expand after card notation and before the shouted-word fold`() {
        // "NT" is all-caps: without the game rule running first, the fold would produce "nt".
        assertEquals(
            "You can also bid 6 no trumps up to 10 no trumps.",
            speak("You can also bid 6NT up to 10NT."),
        )
        assertEquals("10 no trumps is worth 520 points", speak("10NT is worth 520 points"))
    }

    @Test
    fun `plus-points scores are spoken as plus`() {
        assertEquals("contract made, plus 140 points", speak("contract made, +140 points"))
    }

    @Test
    fun `shouted emphasis words fold to lowercase`() {
        assertEquals(
            "the jacks outrank the ace",
            speak("the JACKS OUTRANK THE ACE"),
        )
        assertEquals("the other black Jack", speak("the other BLACK Jack"))
    }

    @Test
    fun `phrase substitutions apply first so they can match raw notation`() {
        // The parenthetical substitution matches the DISPLAY form ("seven (7♠ or higher)");
        // if card notation ran first the pattern would no longer exist to replace.
        assertEquals(
            "a bid of 7 of spades or higher",
            speak("a bid of seven (7♠ or higher)"),
        )
        assertEquals("and score ten a trick defending", speak("and score 10 a trick defending"))
    }

    @Test
    fun `text without notation is spoken as written`() {
        val plain = "Whoever wins a trick leads the next."
        assertEquals(plain, speak(plain))
    }

    @Test
    fun `defaults apply the universal rules only`() {
        assertEquals("jack of spades beats plus 10", cardSpeechText("J♠ beats +10"))
    }
}

/** The generic step cursor: the game supplies the steps, the UI reads `step` until it runs out. */
class TutorialScriptStateTest {

    @Test
    fun `step is the entry at stepIndex and null once the script is over`() {
        val steps = listOf("bid", "discard", "play")
        assertEquals("bid", TutorialScriptState(steps, 0) {}.step)
        assertEquals("play", TutorialScriptState(steps, 2) {}.step)
        assertNull(TutorialScriptState(steps, 3) {}.step)
        assertNull(TutorialScriptState(emptyList<String>(), 0) {}.step)
    }

    @Test
    fun `onAdvance is the caller's cursor mutation`() {
        var advanced = 0
        val state = TutorialScriptState(listOf("a"), 0) { advanced++ }
        state.onAdvance()
        assertEquals(1, advanced)
    }
}
