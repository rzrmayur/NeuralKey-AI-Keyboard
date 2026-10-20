package com.supakeyboard.neuralkey.ime.typing

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline dictionary + Adaptive On-Device Key Memory (Unigram & Contextual Bigram Engine)
 * with QWERTY spatial typo proximity ranking. No typed text is ever sent to a server.
 */
class SuggestionEngine(dictionary: List<String>) {
    private val words = (dictionary.map { it.lowercase(Locale.ROOT) } + commonWords + corrections.values).toHashSet()
    private val sortedWords = words.sorted()
    private val byFirstLetter = sortedWords.groupBy { it.firstOrNull() ?: '\u0000' }
    private val ranks = commonWords.withIndex().associate { it.value to it.index }

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
     */
    fun recordWord(word: String, previousWord: String? = null) {
        if (!keyMemoryEnabled || !isWord(word) || word.length < 2) return
        val lower = word.lowercase(Locale.ROOT)
        // Do not memorize known typo keys unless user explicitly forced them multiple times
        if (lower in corrections && (userWords[lower] ?: 0) == 0) return

        userWords.compute(lower) { _, count -> (count ?: 0) + 1 }
        if (lower !in words && word.any { it.isUpperCase() }) {
            userCustomCasing[lower] = word
        }
        if (previousWord != null && isWord(previousWord) && previousWord.length >= 2) {
            val prevLower = previousWord.lowercase(Locale.ROOT)
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
        val lower = word.lowercase(Locale.ROOT)
        userWords.computeIfPresent(lower) { _, count -> if (count <= 1) null else count - 1 }
        if (previousWord != null && isWord(previousWord)) {
            val prevLower = previousWord.lowercase(Locale.ROOT)
            userBigrams[prevLower]?.computeIfPresent(lower) { _, count -> if (count <= 1) null else count - 1 }
        }
        onMemoryUpdated?.invoke(exportMemory())
    }

    /**
     * Predicts the next 3 likely words after a space based on contextual bigrams and Key Memory.
     */
    fun nextWordSuggestions(previousWord: String): List<String> {
        if (!nextWordPredictionEnabled || !isWord(previousWord)) return emptyList()
        val prevLower = previousWord.lowercase(Locale.ROOT)

        val learnedNext = userBigrams[prevLower]
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key }
            .orEmpty()

        val builtinNext = defaultBigrams[prevLower].orEmpty()

        val topPersonal = userWords.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it != prevLower && it.length >= 2 }
            .take(3)

        return (learnedNext + builtinNext + topPersonal)
            .distinct()
            .take(3)
            .map { userCustomCasing[it] ?: it }
    }

    /**
     * Returns up to 3 intelligent suggestions combining:
     * 1. Exact typed token (always retained first so names/custom terms stay selectable)
     * 2. Explicit typo fixes + Contextual bigram boost + Personal Key Memory + QWERTY spatial edits + SCOWL prefix
     */
    fun suggestions(typed: String, previousWord: String? = null): List<String> {
        if (typed.isBlank()) {
            return if (previousWord != null) nextWordSuggestions(previousWord) else emptyList()
        }
        if (!isWord(typed)) return emptyList()
        val lower = typed.lowercase(Locale.ROOT)
        val prevLower = previousWord?.takeIf { isWord(it) }?.lowercase(Locale.ROOT)
        val explicit = corrections[lower]

        val completions = mutableListOf<String>()
        var index = sortedWords.binarySearch(lower).let { if (it < 0) -it - 1 else it }
        while (index < sortedWords.size && sortedWords[index].startsWith(lower)) {
            completions += sortedWords[index++]
        }
        // Include user-learned vocabulary prefixes not in base dictionary
        for (learned in userWords.keys) {
            if (learned.startsWith(lower)) completions += learned
        }

        val edits = if (typed.length >= 3) edits(lower) else emptySet()
        val comparator = contextualComparator(lower, prevLower)

        val candidates = (
            listOfNotNull(explicit) +
            completions.distinct().sortedWith(comparator) +
            edits.sortedWith(comparator)
        ).distinct()

        // Keep the exact typed word visible first, even if it is a name or technical term.
        return (
            listOf(typed) +
            candidates.filter { it != lower }.take(2).map { candidate ->
                val customCase = userCustomCasing[candidate]
                if (customCase != null && typed.firstOrNull()?.isLowerCase() == true) {
                    customCase
                } else {
                    matchCase(typed, candidate)
                }
            }
        ).take(3)
    }

    fun correction(typed: String): String? {
        if (!isWord(typed) || typed.length < 3 || typed.all { !it.isLetter() || it.isUpperCase() } ||
            typed.drop(1).any { it.isUpperCase() }) return null
        val lower = typed.lowercase(Locale.ROOT)
        // Never autocorrect a word the user has personally taught Key Memory
        if ((userWords[lower] ?: 0) > 0) return null
        corrections[lower]?.let { return matchCase(typed, it) }
        if (lower in words || typed.length < 4 || typed.first().isUpperCase()) return null
        // Only one-edit matches from our common-word vocabulary can auto-replace.
        // Ambiguous and rare matches remain suggestions for the user to choose.
        val frequent = edits(lower).filter { it in ranks }
        return frequent.singleOrNull()?.let { matchCase(typed, it) }
    }

    /**
     * Decodes a continuous Neural Trail Glide / Swipe-to-Type key path (e.g. "helo" or "hrtylo" -> "hello").
     * Uses anchor start/end characters, ordered subsequence matching, and Key Memory bigram/unigram ranking.
     */
    fun decodeGlidePath(rawPath: String, previousWord: String? = null, limit: Int = 3): List<String> {
        val cleaned = rawPath.filter { it.isLetter() }
        if (cleaned.length < 2) return emptyList()
        val isCapitalized = cleaned.first().isUpperCase()
        val lowerPath = collapseRepeats(cleaned.lowercase(Locale.ROOT))
        if (lowerPath.length < 2) return emptyList()

        val firstChar = lowerPath.first()
        val lastChar = lowerPath.last()
        val prevLower = previousWord?.lowercase(Locale.ROOT)?.takeIf { isWord(it) }

        val bucket = (byFirstLetter[firstChar].orEmpty() + userWords.keys.filter { it.firstOrNull() == firstChar })
            .distinct()

        val candidates = bucket.filter { word ->
            word.length in 2..(lowerPath.length + 3) &&
                (word.last() == lastChar || qwertyNeighbors[lastChar]?.contains(word.last()) == true) &&
                isGlideSubsequenceMatch(word, lowerPath)
        }

        val comparator = contextualComparator(lowerPath, prevLower)
        val ranked = candidates.sortedWith { a, b ->
            val aExactEnd = if (a.last() == lastChar) 1 else 0
            val bExactEnd = if (b.last() == lastChar) 1 else 0
            if (aExactEnd != bExactEnd) return@sortedWith bExactEnd.compareTo(aExactEnd)

            val aRatio = glideMatchScore(a, lowerPath)
            val bRatio = glideMatchScore(b, lowerPath)
            if (aRatio != bRatio) return@sortedWith bRatio.compareTo(aRatio)

            comparator.compare(a, b)
        }.take(limit)

        return ranked.map { candidate ->
            val customCase = userCustomCasing[candidate]
            when {
                isCapitalized -> candidate.replaceFirstChar { it.titlecase(Locale.ROOT) }
                customCase != null -> customCase
                else -> candidate
            }
        }
    }

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
        val lengthPenalty = kotlin.math.abs(collapsedPath.length - collapsedWord.length) * 5
        return completenessBonus + (exactMatches * 10) - lengthPenalty
    }

    private fun contextualComparator(typedLower: String, prevLower: String?): Comparator<String> {
        val learnedNextMap = if (prevLower != null) userBigrams[prevLower] else null
        val builtinNextList = if (prevLower != null) defaultBigrams[prevLower].orEmpty() else emptyList()

        return Comparator { a, b ->
            // 1. Contextual user bigram frequency following previousWord
            val aBigram = learnedNextMap?.get(a) ?: 0
            val bBigram = learnedNextMap?.get(b) ?: 0
            if (aBigram != bBigram) return@Comparator bBigram.compareTo(aBigram)

            // 2. Built-in bigram match following previousWord
            val aBuiltinIdx = builtinNextList.indexOf(a).let { if (it == -1) Int.MAX_VALUE else it }
            val bBuiltinIdx = builtinNextList.indexOf(b).let { if (it == -1) Int.MAX_VALUE else it }
            if (aBuiltinIdx != bBuiltinIdx) return@Comparator aBuiltinIdx.compareTo(bBuiltinIdx)

            // 3. Personal Key Memory unigram count
            val aUser = userWords[a] ?: 0
            val bUser = userWords[b] ?: 0
            if (aUser != bUser) return@Comparator bUser.compareTo(aUser)

            // 4. QWERTY spatial adjacency for same-length 1-char substitutions
            val aSpatial = if (isQwertyNeighborSubstitution(typedLower, a)) 1 else 0
            val bSpatial = if (isQwertyNeighborSubstitution(typedLower, b)) 1 else 0
            if (aSpatial != bSpatial) return@Comparator bSpatial.compareTo(aSpatial)

            // 5. Base common-word rank, then length, then alphabetical
            val aRank = ranks[a] ?: Int.MAX_VALUE
            val bRank = ranks[b] ?: Int.MAX_VALUE
            if (aRank != bRank) return@Comparator aRank.compareTo(bRank)

            val lenCmp = a.length.compareTo(b.length)
            if (lenCmp != 0) lenCmp else a.compareTo(b)
        }
    }

    private fun edits(word: String): Set<String> = buildSet {
        for (i in word.indices) {
            add(word.removeRange(i, i + 1))
            if (i + 1 < word.length) {
                add(word.take(i) + word[i + 1] + word[i] + word.drop(i + 2))
            }
            for (letter in 'a'..'z') add(word.take(i) + letter + word.drop(i + 1))
        }
        for (i in 0..word.length) {
            for (letter in 'a'..'z') add(word.take(i) + letter + word.drop(i))
        }
        retainAll { it in words || userWords.containsKey(it) }
    }

    companion object {
        fun isWord(word: String) = word.length in 1..32 && word.first().isLetter() &&
            word.all { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' || it == '’' }

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

        private val defaultBigrams: Map<String, List<String>> = mapOf(
            "thank" to listOf("you", "goodness", "fully"),
            "thanks" to listOf("for", "so", "again"),
            "looking" to listOf("forward", "for", "into"),
            "forward" to listOf("to", "this", "with"),
            "please" to listOf("let", "find", "check"),
            "let" to listOf("me", "us", "know"),
            "let's" to listOf("schedule", "discuss", "make"),
            "best" to listOf("regards", "wishes", "of"),
            "kind" to listOf("regards", "of", "words"),
            "warm" to listOf("regards", "wishes", "welcome"),
            "how" to listOf("are", "is", "can"),
            "are" to listOf("you", "we", "they"),
            "i" to listOf("am", "have", "will"),
            "i'm" to listOf("looking", "going", "happy"),
            "we" to listOf("are", "will", "can"),
            "can" to listOf("you", "we", "i"),
            "could" to listOf("you", "we", "be"),
            "would" to listOf("love", "like", "you"),
            "attached" to listOf("is", "file", "document"),
            "see" to listOf("you", "attached", "the"),
            "talk" to listOf("soon", "to", "later"),
            "good" to listOf("morning", "afternoon", "evening"),
            "happy" to listOf("to", "birthday", "friday"),
            "follow" to listOf("up", "the", "along"),
            "following" to listOf("up", "the", "our"),
            "schedule" to listOf("a", "the", "time"),
            "meeting" to listOf("tomorrow", "today", "with"),
            "hope" to listOf("you", "this", "everything")
        )

        private val corrections = mapOf(
            "teh" to "the", "thsi" to "this", "taht" to "that", "adn" to "and",
            "hte" to "the", "wiht" to "with", "woudl" to "would", "coudl" to "could",
            "shoudl" to "should", "dont" to "don't", "doesnt" to "doesn't",
            "didnt" to "didn't", "isnt" to "isn't", "wasnt" to "wasn't",
            "werent" to "weren't", "havent" to "haven't", "hasnt" to "hasn't",
            "couldnt" to "couldn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't",
            "recieve" to "receive", "recieved" to "received", "receieve" to "receive",
            "becuase" to "because", "beacuse" to "because", "becasue" to "because",
            "definately" to "definitely", "definetly" to "definitely", "seperate" to "separate",
            "occured" to "occurred", "untill" to "until", "tommorow" to "tomorrow",
            "tomorow" to "tomorrow", "tommorrow" to "tomorrow", "yestarday" to "yesterday",
            "togehter" to "together", "togeather" to "together", "alwasy" to "always",
            "pleaes" to "please", "plase" to "please", "thansk" to "thanks",
            "thnaks" to "thanks", "thanls" to "thanks", "sugestion" to "suggestion",
            "suggeston" to "suggestion", "mesage" to "message", "messsage" to "message",
            "keybaord" to "keyboard", "keybord" to "keyboard", "adress" to "address",
            "addres" to "address", "wierd" to "weird", "freind" to "friend",
            "buisness" to "business", "bussiness" to "business", "langauge" to "language",
            "languge" to "language", "accomodate" to "accommodate", "acheive" to "achieve",
            "agian" to "again", "agaisnt" to "against", "availabe" to "available",
            "availble" to "available", "belive" to "believe", "beleive" to "believe",
            "calender" to "calendar", "comming" to "coming", "completly" to "completely",
            "enviroment" to "environment", "goverment" to "government",
            "immediatly" to "immediately", "neccessary" to "necessary", "necesary" to "necessary",
            "probaly" to "probably", "probalby" to "probably", "realy" to "really",
            "relevent" to "relevant", "rember" to "remember", "remeber" to "remember",
            "responce" to "response", "sucess" to "success", "succesful" to "successful",
            "sucessful" to "successful", "thier" to "their", "whcih" to "which",
            "writting" to "writing", "whith" to "with", "wihch" to "which"
        )

        private val commonWords = ("""
            the be to of and a in that have it for not on with he as you do at this but his by from
            they we say her she or an will my one all would there their what so up out if about who
            get which go me when make can like time no just him know take people into year your good
            some could them see other than then now look only come its over think also back after use
            two how our work first well way even new want because any these give day most us is are
            was were been being had has did does doing done very much more many such should must may
            might shall need here where why hello hi thanks thank please welcome sorry yes okay sure
            great nice fine happy hope today tomorrow yesterday morning afternoon evening night week
            month meeting email message call phone address home office help send sent receive received
            write read reply talk speak tell ask answer question information update change check test
            build release keyboard suggestion correction correct typing text word language english
            german slovak business company project team manager support problem issue error fix working
            available ready finish complete start stop open close again next last before soon later
            together always never sometimes already still really definitely separate calendar friend
            family love life little long small big important possible understand remember response
            success successful writing environment necessary immediately probably relevant coming
            everything something nothing anything everyone someone nobody anything around through
            between without under above below same different right left number name place thing
            world country city road school university study learn student computer software app
            application internet online offline account model data settings option button screen
            download install version file document report review feedback let let's don't doesn't
            didn't isn't aren't wasn't weren't haven't hasn't couldn't wouldn't shouldn't can't
            won't i'm i'll i've you're you'll you've we're we'll we've they're they'll they've
            that's there's what's how's here's it's it's we'd you'd they'd he's she's
        """).trim().split(Regex("\\s+")).distinct()
    }
}
