package com.supakeyboard.neuralkey.ime.typing

import java.util.Locale

/** Immutable, offline dictionary. No typed text is stored or sent to a provider. */
class SuggestionEngine(dictionary: List<String>) {
    private val words = (dictionary + commonWords + corrections.values).toHashSet()
    private val sortedWords = words.sorted()
    private val ranks = commonWords.withIndex().associate { it.value to it.index }

    fun suggestions(typed: String): List<String> {
        if (!isWord(typed)) return emptyList()
        val lower = typed.lowercase(Locale.ROOT)
        val explicit = corrections[lower]
        val completions = mutableListOf<String>()
        var index = sortedWords.binarySearch(lower).let { if (it < 0) -it - 1 else it }
        while (index < sortedWords.size && sortedWords[index].startsWith(lower)) {
            completions += sortedWords[index++]
        }
        val edits = if (typed.length >= 3) edits(lower) else emptySet()
        val candidates = (listOfNotNull(explicit) +
            completions.sortedWith(rankOrder) + edits.sortedWith(rankOrder)).distinct()
        // Keep the exact typed word visible, even if it is a name or technical term.
        return (listOf(typed) + candidates.filter { it != lower }.take(2).map { matchCase(typed, it) }).take(3)
    }

    fun correction(typed: String): String? {
        if (!isWord(typed) || typed.length < 3 || typed.all { !it.isLetter() || it.isUpperCase() } ||
            typed.drop(1).any { it.isUpperCase() }) return null
        val lower = typed.lowercase(Locale.ROOT)
        corrections[lower]?.let { return matchCase(typed, it) }
        if (lower in words || typed.length < 4) return null
        // Only one-edit matches from our common-word vocabulary can auto-replace.
        // Ambiguous and rare matches remain suggestions for the user to choose.
        val frequent = edits(lower).filter { it in ranks }
        return frequent.singleOrNull()?.let { matchCase(typed, it) }
    }

    private val rankOrder = compareBy<String> { ranks[it] ?: Int.MAX_VALUE }.thenBy { it.length }.thenBy { it }

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
        retainAll(words)
    }

    companion object {
        fun isWord(word: String) = word.length in 1..32 && word.first().isLetter() &&
            word.all { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' || it == '’' }

        fun matchCase(typed: String, candidate: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> candidate.uppercase(Locale.ROOT)
            typed.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> candidate
        }

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
