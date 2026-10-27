package com.supakeyboard.neuralkey.ime.typing

import java.util.Arrays
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Offline word prediction: a frequency-ranked language model (unigrams + next-word bigrams),
 * the bundled SCOWL dictionary as a spelling backstop, QWERTY-aware typo correction
 * (edit distance 1 and 2) and an adaptive on-device Key Memory. No typed text ever leaves the device.
 *
 * Candidates are ranked by one additive log-frequency score:
 * `frequency + prefix bonus + learned bonus + context (bigram) bonus - edit cost - length penalty`.
 */
class SuggestionEngine(dictionary: List<String>, private val model: LanguageModel = LanguageModel.EMPTY) {
    private val corrections = SuggestionSeedData.corrections
    private val seedBigrams = SuggestionSeedData.defaultBigrams
    private val seedRanks: Map<String, Int> =
        SuggestionSeedData.commonWords.withIndex().associate { it.value to it.index }

    /** Every known lowercase word -> log-frequency score. */
    private val base: HashMap<String, Int>
    private val sortedWords: Array<String>
    private val sortedScores: IntArray

    init {
        val map = HashMap<String, Int>(model.scores.size + dictionary.size)
        val modelActive = model.scores.isNotEmpty()
        for (raw in dictionary) {
            val word = normalize(raw)
            if (!isPlain(word)) continue
            // With a frequency model, SCOWL possessives ("aardvark's") are noise; contractions live in the model.
            if (modelActive && word.indexOf('\'') >= 0 && word !in model.scores) continue
            map[word] = DICT_ONLY_SCORE
        }
        for ((word, score) in model.scores) map[word] = score
        for ((word, index) in seedRanks) {
            if (word in model.scores || !isPlain(word)) continue
            map[word] = maxOf(map[word] ?: 0, (SEED_TOP - index * SEED_STEP).toInt())
        }
        for (fixed in corrections.values) {
            val word = normalize(fixed)
            if (isPlain(word)) map.putIfAbsent(word, DICT_ONLY_SCORE)
        }
        val keys = map.keys.toTypedArray()
        keys.sort()
        sortedWords = keys
        sortedScores = IntArray(keys.size) { map.getValue(keys[it]) }
        base = map
    }

    // On-device Key Memory (user-typed frequency & contextual next-word bigrams)
    private val userWords = ConcurrentHashMap<String, Int>()
    private val userBigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val userCustomCasing = ConcurrentHashMap<String, String>()

    @Volatile
    var keyMemoryEnabled: Boolean = true

    @Volatile
    var nextWordPredictionEnabled: Boolean = true

    @Volatile
    var onMemoryUpdated: ((KeyMemorySnapshot) -> Unit)? = null

    fun restoreMemory(snapshot: KeyMemorySnapshot) {
        userWords.clear()
        userBigrams.clear()
        userCustomCasing.clear()
        userWords.putAll(snapshot.words)
        snapshot.bigrams.forEach { (prev, nextMap) ->
            userBigrams[prev] = ConcurrentHashMap(nextMap)
        }
        userCustomCasing.putAll(snapshot.customCasing)
    }

    fun exportMemory(): KeyMemorySnapshot = KeyMemorySnapshot(
        words = HashMap(userWords),
        bigrams = userBigrams.mapValues { HashMap(it.value) },
        customCasing = HashMap(userCustomCasing)
    )

    fun clearMemory() {
        userWords.clear()
        userBigrams.clear()
        userCustomCasing.clear()
        onMemoryUpdated?.invoke(KeyMemorySnapshot())
    }

    fun learnedWordCount(): Int = userWords.size

    /**
     * Records a word typed or selected by the user into on-device Key Memory.
     * Likely typos of common words are not memorised unless [force]d (the user undid a correction).
     */
    fun recordWord(word: String, previousWord: String? = null, force: Boolean = false) {
        if (!keyMemoryEnabled || !isWord(word) || word.length < 2) return
        val lower = normalize(word)
        // Do not memorize known typo keys unless the user explicitly forced them multiple times
        if (lower in corrections && (userWords[lower] ?: 0) == 0 && !force) return
        if (!force && looksLikeTypo(lower, word)) return

        userWords.compute(lower) { _, count -> (count ?: 0) + 1 }
        if (!base.containsKey(lower) && word.any { it.isUpperCase() }) {
            userCustomCasing[lower] = word
        }
        if (previousWord != null && isWord(previousWord) && previousWord.length >= 2) {
            val prevLower = normalize(previousWord)
            val nextMap = userBigrams.getOrPut(prevLower) { ConcurrentHashMap() }
            nextMap.compute(lower) { _, count -> (count ?: 0) + 1 }
        }
        onMemoryUpdated?.invoke(exportMemory())
    }

    /**
     * Rolls back a learned word when the user immediately presses Backspace to undo.
     */
    fun unlearnWord(word: String, previousWord: String? = null) {
        if (!keyMemoryEnabled || !isWord(word)) return
        val lower = normalize(word)
        userWords.computeIfPresent(lower) { _, count -> if (count <= 1) null else count - 1 }
        if (previousWord != null && isWord(previousWord)) {
            val prevLower = normalize(previousWord)
            userBigrams[prevLower]?.computeIfPresent(lower) { _, count -> if (count <= 1) null else count - 1 }
        }
        onMemoryUpdated?.invoke(exportMemory())
    }

    /**
     * Predicts the next 3 likely words after a space: learned bigrams first, then the
     * web-scale bigram model, then the curated seed pairs, then your most-used words.
     */
    fun nextWordSuggestions(previousWord: String): List<String> {
        if (!nextWordPredictionEnabled || !isWord(previousWord)) return emptyList()
        val prev = normalize(previousWord)

        val learned = userBigrams[prev]?.entries?.sortedByDescending { it.value }?.map { it.key }.orEmpty()
        val builtin = model.bigrams[prev].orEmpty() + seedBigrams[prev].orEmpty()
        val personal = userWords.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it != prev && it.length >= 2 }
            .take(3)

        return (learned + builtin + personal)
            .filter { it != prev }
            .distinct()
            .take(3)
            .map { display(it) }
    }

    /** Suggestions shown at the start of a field or sentence, when there is no previous word. */
    fun sentenceStartSuggestions(): List<String> =
        if (nextWordPredictionEnabled) SENTENCE_STARTERS.take(3) else emptyList()

    /**
     * Returns up to 3 suggestions: the exact typed token first (so names and custom terms stay
     * selectable), then the two best-scoring completions / corrections.
     */
    fun suggestions(typed: String, previousWord: String? = null): List<String> {
        if (typed.isBlank()) {
            return if (previousWord != null) nextWordSuggestions(previousWord) else emptyList()
        }
        if (!isWord(typed)) return emptyList()
        val lower = normalize(typed)
        val prevLower = previousWord?.takeIf { isWord(it) }?.let { normalize(it) }
        val ctx = context(prevLower)
        val typedKnown = base.containsKey(lower) || userWords.containsKey(lower)
        val typedScore = scoreOf(lower)

        val scored = HashMap<String, Double>()
        fun offer(word: String, score: Double) {
            if (word == lower) return
            val old = scored[word]
            if (old == null || score > old) scored[word] = score
        }

        corrections[lower]?.let { offer(normalize(it), EXPLICIT_SCORE) }

        // 1. Prefix completions: what the user is most likely still typing.
        var index = lowerBound(lower)
        while (index < sortedWords.size && sortedWords[index].startsWith(lower)) {
            val word = sortedWords[index++]
            offer(word, contextScore(word, ctx) + PREFIX_BONUS - LENGTH_PENALTY * (word.length - lower.length))
        }
        for (learned in userWords.keys) {
            if (learned.startsWith(lower) && !base.containsKey(learned)) {
                val extra = learned.length - lower.length
                offer(learned, contextScore(learned, ctx) + PREFIX_BONUS - LENGTH_PENALTY * extra)
            }
        }

        // 2. Typo candidates at edit distance 1 (keyboard-aware costs) ...
        if (lower.length >= 3) {
            for ((word, cost) in edits1(lower)) {
                if (lower.length == 3 && cost > COST_NEIGHBOR) continue
                if (typedKnown && scoreOf(word) < typedScore + KNOWN_TYPED_MARGIN) continue
                offer(word, contextScore(word, ctx) - cost)
            }
        }
        // 3. ... and distance 2 for longer words that are not words themselves.
        if (lower.length >= 5 && !typedKnown) {
            fuzzyScan(lower) { word, distance ->
                offer(word, contextScore(word, ctx) - COST_PER_EXTRA_EDIT * distance)
            }
        }

        val ranked = scored.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Double>> { it.value }
                    .thenBy { it.key.length }
                    .thenBy { it.key }
            )
            .map { render(typed, it.key) }
            .filter { it != typed }
            .distinct()
            .take(2)
        return (listOf(typed) + ranked).take(3)
    }

    /**
     * Returns the word that should silently replace [typed] when the user presses Space, or null.
     * Only confident fixes are applied; ambiguous ones stay as suggestions.
     */
    fun correction(typed: String): String? {
        if (!isWord(typed)) return null
        val lower = normalize(typed)
        if (typed == lower) ALWAYS_CAPITAL[lower]?.let { return it }
        if (typed.length < 3 || typed.all { !it.isLetter() || it.isUpperCase() } ||
            typed.drop(1).any { it.isUpperCase() }) return null
        // Never autocorrect a word the user has personally taught Key Memory
        if ((userWords[lower] ?: 0) > 0) return null
        corrections[lower]?.let { return matchCase(typed, it) }
        // Apostrophe-less contractions are also valid frequency-model tokens, so expand them explicitly.
        CONTRACTIONS[lower]?.let { return matchCase(typed, it) }
        if (base.containsKey(lower) || typed.length < 4 || typed.first().isUpperCase() || '\'' in lower) return null

        // Only cheap, keyboard-plausible slips of fairly common words may auto-replace.
        val candidates = edits1(lower)
            .filter { (word, cost) -> cost <= COST_EXTRA_LETTER && isCommon(word) }
            .map { (word, cost) -> word to (scoreOf(word) - cost) }
            .sortedByDescending { it.second }
        val best = candidates.firstOrNull() ?: return null
        val second = candidates.getOrNull(1)
        if (second != null && best.second - second.second < AUTOCORRECT_MARGIN) return null
        return matchCase(typed, best.first)
    }

    /**
     * Decodes a continuous Neural Trail Glide / Swipe-to-Type key path (e.g. "helo" or "hrtylo" -> "hello").
     * Uses anchor start/end characters, ordered subsequence matching, word frequency and context.
     */
    fun decodeGlidePath(rawPath: String, previousWord: String? = null, limit: Int = 3): List<String> {
        val cleaned = rawPath.filter { it.isLetter() }
        if (cleaned.length < 2) return emptyList()
        val isCapitalized = cleaned.first().isUpperCase()
        val lowerPath = collapseRepeats(cleaned.lowercase(Locale.ROOT))
        if (lowerPath.length < 2) return emptyList()

        val firstChar = lowerPath.first()
        val lastChar = lowerPath.last()
        val prevLower = previousWord?.takeIf { isWord(it) }?.let { normalize(it) }
        val ctx = context(prevLower)

        val bucket = LinkedHashSet<String>()
        var index = lowerBound(firstChar.toString())
        val end = lowerBound((firstChar + 1).toString())
        while (index < end) bucket += sortedWords[index++]
        for (learned in userWords.keys) if (learned.firstOrNull() == firstChar) bucket += learned

        class Scored(val word: String, val exactEnd: Boolean, val score: Double)

        val ranked = bucket.asSequence()
            .filter { word ->
                word.length in 2..(lowerPath.length + 3) &&
                    (word.last() == lastChar || qwertyNeighbors[lastChar]?.contains(word.last()) == true) &&
                    isGlideSubsequenceMatch(word, lowerPath)
            }
            .map { word ->
                Scored(
                    word,
                    word.last() == lastChar,
                    glideMatchScore(word, lowerPath) + GLIDE_CONTEXT_WEIGHT * contextScore(word, ctx)
                )
            }
            .sortedWith(compareByDescending<Scored> { it.exactEnd }.thenByDescending { it.score }.thenBy { it.word })
            .take(limit)
            .toList()

        return ranked.map { candidate ->
            if (isCapitalized) {
                (userCustomCasing[candidate.word] ?: model.cased[candidate.word] ?: candidate.word)
                    .replaceFirstChar { it.titlecase(Locale.ROOT) }
            } else {
                display(candidate.word)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scoring
    // ---------------------------------------------------------------------------------------------

    private class Context(val learned: Map<String, Int>?, val builtin: Map<String, Int>)

    private fun context(prevLower: String?): Context {
        if (prevLower == null) return NO_CONTEXT
        val builtinList = (model.bigrams[prevLower].orEmpty() + seedBigrams[prevLower].orEmpty()).distinct()
        val builtin = HashMap<String, Int>(builtinList.size * 2)
        builtinList.forEachIndexed { i, word -> builtin[word] = i }
        return Context(userBigrams[prevLower], builtin)
    }

    private fun scoreOf(word: String): Int =
        base[word] ?: if (userWords.containsKey(word)) USER_ONLY_SCORE else 0

    private fun contextScore(word: String, ctx: Context): Double {
        var score = scoreOf(word).toDouble()
        val used = userWords[word] ?: 0
        if (used > 0) score += LEARNED_BASE + LEARNED_STEP * min(used, 15)
        ctx.learned?.get(word)?.let { score += BIGRAM_LEARNED + BIGRAM_LEARNED_STEP * min(it, 10) }
        ctx.builtin[word]?.let { score += maxOf(BIGRAM_BUILTIN - BIGRAM_STEP * it, BIGRAM_BUILTIN_MIN) }
        return score
    }

    /** Frequent enough to be offered as a distance-2 fix. */
    private fun isFrequent(word: String): Boolean =
        word in seedRanks || (model.scores[word] ?: 0) >= FREQUENT_SCORE

    /** Common enough to be trusted for silent auto-correction. */
    private fun isCommon(word: String): Boolean =
        word in seedRanks || (model.scores[word] ?: 0) >= COMMON_SCORE

    /** A never-seen word one cheap slip away from a common word is probably a typo, not vocabulary. */
    private fun looksLikeTypo(lower: String, original: String): Boolean {
        if (base.containsKey(lower) || original.any { it.isUpperCase() } || lower.length < 3) return false
        return edits1(lower).any { (word, cost) -> cost <= COST_EXTRA_LETTER && isFrequent(word) }
    }

    private fun display(word: String): String = userCustomCasing[word] ?: model.cased[word] ?: word

    private fun render(typed: String, candidate: String): String {
        val preferred = userCustomCasing[candidate] ?: model.cased[candidate]
        return when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> candidate.uppercase(Locale.ROOT)
            typed.first().isUpperCase() -> preferred ?: candidate.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> preferred ?: candidate
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Candidate generation
    // ---------------------------------------------------------------------------------------------

    private fun lowerBound(prefix: String): Int {
        val found = Arrays.binarySearch(sortedWords, prefix)
        return if (found < 0) -found - 1 else found
    }

    /** All known words one keystroke error away from [word], with a keyboard-aware cost. */
    private fun edits1(word: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        fun add(candidate: String, cost: Int) {
            if (candidate == word || candidate.isEmpty()) return
            if (!base.containsKey(candidate) && !userWords.containsKey(candidate)) return
            val old = out[candidate]
            if (old == null || cost < old) out[candidate] = cost
        }
        for (i in word.indices) {
            add(word.removeRange(i, i + 1), COST_EXTRA_LETTER)
            if (i + 1 < word.length && word[i] != word[i + 1]) {
                add(word.take(i) + word[i + 1] + word[i] + word.drop(i + 2), COST_TRANSPOSE)
            }
            val neighbors = qwertyNeighbors[word[i]].orEmpty()
            for (letter in 'a'..'z') {
                if (letter == word[i]) continue
                add(
                    word.take(i) + letter + word.drop(i + 1),
                    if (neighbors.indexOf(letter) >= 0) COST_NEIGHBOR else COST_SUBSTITUTE
                )
            }
        }
        for (i in 0..word.length) {
            for (letter in 'a'..'z') add(word.take(i) + letter + word.drop(i), COST_MISSING_LETTER)
            add(word.take(i) + '\'' + word.drop(i), COST_APOSTROPHE)
        }
        return out
    }

    /** Distance-2 candidates among frequent words sharing the first letter. */
    private fun fuzzyScan(lower: String, onMatch: (String, Int) -> Unit) {
        val first = lower.first()
        var index = lowerBound(first.toString())
        val end = lowerBound((first + 1).toString())
        while (index < end) {
            val word = sortedWords[index]
            val score = sortedScores[index]
            index++
            if (score < FREQUENT_SCORE || abs(word.length - lower.length) > 2) continue
            val distance = editDistance(lower, word, 2)
            if (distance == 2) onMatch(word, distance)
        }
    }

    /** Optimal-string-alignment (Damerau-Levenshtein) distance, abandoned early beyond [max]. */
    private fun editDistance(a: String, b: String, max: Int): Int {
        if (abs(a.length - b.length) > max) return max + 1
        var older = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = min(value, older[j - 2] + 1)
                }
                current[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > max) return max + 1
            val recycled = older
            older = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }

    // ---------------------------------------------------------------------------------------------
    // Glide helpers
    // ---------------------------------------------------------------------------------------------

    private fun collapseRepeats(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var prev = '\u0000'
        for (c in input) {
            if (c != prev) {
                sb.append(c)
                prev = c
            }
        }
        return sb.toString()
    }

    private fun isGlideSubsequenceMatch(word: String, collapsedPath: String): Boolean {
        val collapsedWord = collapseRepeats(word)
        var pathIdx = 0
        var matched = 0
        for (wc in collapsedWord) {
            while (pathIdx < collapsedPath.length && collapsedPath[pathIdx] != wc) {
                pathIdx++
            }
            if (pathIdx < collapsedPath.length) {
                matched++
                pathIdx++
            }
        }
        // Allow up to 1 skipped interior letter on longer words while requiring full match on short words
        val minRequired = if (collapsedWord.length <= 3) collapsedWord.length else collapsedWord.length - 1
        return matched >= minRequired
    }

    private fun glideMatchScore(word: String, collapsedPath: String): Int {
        val collapsedWord = collapseRepeats(word)
        var pathIdx = 0
        var exactMatches = 0
        for (wc in collapsedWord) {
            while (pathIdx < collapsedPath.length && collapsedPath[pathIdx] != wc) {
                pathIdx++
            }
            if (pathIdx < collapsedPath.length) {
                exactMatches++
                pathIdx++
            }
        }
        val completenessBonus = if (exactMatches == collapsedWord.length) 100 else 40
        val lengthPenalty = abs(collapsedPath.length - collapsedWord.length) * 5
        return completenessBonus + (exactMatches * 10) - lengthPenalty
    }

    companion object {
        private const val DICT_ONLY_SCORE = 70
        private const val USER_ONLY_SCORE = 90
        private const val SEED_TOP = 200.0
        private const val SEED_STEP = 0.2
        private const val FREQUENT_SCORE = 130
        private const val COMMON_SCORE = 150
        private const val EXPLICIT_SCORE = 1000.0

        private const val PREFIX_BONUS = 25.0
        private const val LENGTH_PENALTY = 1.5
        private const val KNOWN_TYPED_MARGIN = 40
        private const val AUTOCORRECT_MARGIN = 35
        private const val GLIDE_CONTEXT_WEIGHT = 0.2

        private const val LEARNED_BASE = 120.0
        private const val LEARNED_STEP = 10.0
        private const val BIGRAM_LEARNED = 100.0
        private const val BIGRAM_LEARNED_STEP = 10.0
        private const val BIGRAM_BUILTIN = 60
        private const val BIGRAM_BUILTIN_MIN = 20
        private const val BIGRAM_STEP = 6

        private const val COST_APOSTROPHE = 12
        private const val COST_TRANSPOSE = 16
        private const val COST_MISSING_LETTER = 18
        private const val COST_NEIGHBOR = 20
        private const val COST_EXTRA_LETTER = 24
        private const val COST_SUBSTITUTE = 34
        private const val COST_PER_EXTRA_EDIT = 35

        private val NO_CONTEXT = Context(null, emptyMap())
        private val SENTENCE_STARTERS = listOf("I", "The", "Thanks", "Hi", "Hey", "Please")
        private val CONTRACTIONS = mapOf(
            "cant" to "can't", "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't",
            "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "werent" to "weren't",
            "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "wont" to "won't", "youre" to "you're",
            "theyre" to "they're", "thats" to "that's", "whats" to "what's", "ive" to "I've"
        )
        private val ALWAYS_CAPITAL = mapOf(
            "i" to "I", "i'm" to "I'm", "i'll" to "I'll", "i've" to "I've", "i'd" to "I'd",
            "im" to "I'm", "ive" to "I've"
        )

        fun isWord(word: String) = word.length in 1..32 && word.first().isLetter() &&
            word.all { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' || it == '’' }

        private fun isPlain(word: String) = word.isNotEmpty() && word.length <= 32 && word[0] in 'a'..'z' &&
            word.all { it in 'a'..'z' || it == '\'' }

        private fun normalize(word: String) = word.lowercase(Locale.ROOT).replace('’', '\'')

        fun matchCase(typed: String, candidate: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> candidate.uppercase(Locale.ROOT)
            typed.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> candidate
        }

        private val qwertyNeighbors: Map<Char, String> = mapOf(
            'q' to "wa", 'w' to "qeasd", 'e' to "wsdrf", 'r' to "edftg", 't' to "rfgyh",
            'y' to "tghuj", 'u' to "yhjik", 'i' to "ujkol", 'o' to "iklp", 'p' to "ol",
            'a' to "qwsxz", 's' to "qweadzx", 'd' to "wersfxc", 'f' to "ertdgcv", 'g' to "rtyfhvb",
            'h' to "tyugjbn", 'j' to "yuihknm", 'k' to "uiojlm", 'l' to "iopk",
            'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn",
            'n' to "bhjm", 'm' to "njk"
        )

        fun isQwertyNeighborSubstitution(typed: String, candidate: String): Boolean {
            if (typed.length != candidate.length) return false
            var diffIndex = -1
            for (i in typed.indices) {
                if (typed[i] != candidate[i]) {
                    if (diffIndex != -1) return false
                    diffIndex = i
                }
            }
            if (diffIndex == -1) return false
            val from = typed[diffIndex]
            val to = candidate[diffIndex]
            return qwertyNeighbors[from]?.contains(to) == true
        }
    }
}
