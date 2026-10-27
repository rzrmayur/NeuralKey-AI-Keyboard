package com.supakeyboard.neuralkey.ime.typing

/**
 * Hand-curated seed data used when the bundled language model is unavailable and as a
 * high-precision overlay (explicit typo fixes) on top of it.
 */
internal object SuggestionSeedData {
        val defaultBigrams: Map<String, List<String>> = mapOf(
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

        val corrections = mapOf(
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

        val commonWords = ("""
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
