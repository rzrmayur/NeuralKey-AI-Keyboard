package com.supakeyboard.neuralkey.ime.keyboard

object KeyboardLayouts {

    val qwertyRows: List<List<KeyDefinition>> = listOf(
        listOf(
            KeyDefinition("q", secondaryText = "1"),
            KeyDefinition("w", secondaryText = "2"),
            KeyDefinition("e", secondaryText = "3"),
            KeyDefinition("r", secondaryText = "4"),
            KeyDefinition("t", secondaryText = "5"),
            KeyDefinition("y", secondaryText = "6"),
            KeyDefinition("u", secondaryText = "7"),
            KeyDefinition("i", secondaryText = "8"),
            KeyDefinition("o", secondaryText = "9"),
            KeyDefinition("p", secondaryText = "0")
        ),
        listOf(
            KeyDefinition("", type = KeyType.SPACER, weight = 0.5f),
            KeyDefinition("a"),
            KeyDefinition("s"),
            KeyDefinition("d"),
            KeyDefinition("f"),
            KeyDefinition("g"),
            KeyDefinition("h"),
            KeyDefinition("j"),
            KeyDefinition("k"),
            KeyDefinition("l"),
            KeyDefinition("", type = KeyType.SPACER, weight = 0.5f)
        ),
        listOf(
            KeyDefinition("⇧", type = KeyType.SHIFT, weight = 1.5f, isFunctional = true),
            KeyDefinition("z"),
            KeyDefinition("x"),
            KeyDefinition("c"),
            KeyDefinition("v"),
            KeyDefinition("b"),
            KeyDefinition("n"),
            KeyDefinition("m"),
            KeyDefinition("⌫", type = KeyType.BACKSPACE, weight = 1.5f, isFunctional = true)
        ),
        listOf(
            KeyDefinition("?123", type = KeyType.SYMBOLS_TOGGLE, weight = 1.4f, isFunctional = true),
            KeyDefinition("🌐", type = KeyType.SWITCH_IME, weight = 1.0f, isFunctional = true),
            KeyDefinition("space", type = KeyType.SPACE, weight = 4.8f),
            KeyDefinition(".", type = KeyType.PERIOD, weight = 1.0f),
            KeyDefinition("↵", type = KeyType.ENTER, weight = 1.8f, isFunctional = true)
        )
    )

    val symbolsRowsPage1: List<List<KeyDefinition>> = listOf(
        listOf(
            KeyDefinition("1"),
            KeyDefinition("2"),
            KeyDefinition("3"),
            KeyDefinition("4"),
            KeyDefinition("5"),
            KeyDefinition("6"),
            KeyDefinition("7"),
            KeyDefinition("8"),
            KeyDefinition("9"),
            KeyDefinition("0")
        ),
        listOf(
            KeyDefinition("", type = KeyType.SPACER, weight = 0.5f),
            KeyDefinition("@"),
            KeyDefinition("#"),
            KeyDefinition("$"),
            KeyDefinition("%"),
            KeyDefinition("&"),
            KeyDefinition("-"),
            KeyDefinition("+"),
            KeyDefinition("("),
            KeyDefinition(")"),
            KeyDefinition("", type = KeyType.SPACER, weight = 0.5f)
        ),
        listOf(
            KeyDefinition("=\\<", type = KeyType.SYMBOLS_PAGE_TOGGLE, weight = 1.5f, isFunctional = true),
            KeyDefinition("*"),
            KeyDefinition("\""),
            KeyDefinition("'"),
            KeyDefinition(":"),
            KeyDefinition(";"),
            KeyDefinition("!"),
            KeyDefinition("?"),
            KeyDefinition("⌫", type = KeyType.BACKSPACE, weight = 1.5f, isFunctional = true)
        ),
        listOf(
            KeyDefinition("ABC", type = KeyType.SYMBOLS_TOGGLE, weight = 1.4f, isFunctional = true),
            KeyDefinition(",", type = KeyType.COMMA, weight = 1.0f),
            KeyDefinition("space", type = KeyType.SPACE, weight = 4.8f),
            KeyDefinition("/", weight = 1.0f),
            KeyDefinition("↵", type = KeyType.ENTER, weight = 1.8f, isFunctional = true)
        )
    )

    val symbolsRowsPage2: List<List<KeyDefinition>> = listOf(
        listOf(
            KeyDefinition("~"),
            KeyDefinition("`"),
            KeyDefinition("|"),
            KeyDefinition("•"),
            KeyDefinition("√"),
            KeyDefinition("π"),
            KeyDefinition("÷"),
            KeyDefinition("×"),
            KeyDefinition("¶"),
            KeyDefinition("∆")
        ),
        listOf(
            KeyDefinition("", type = KeyType.SPACER, weight = 0.5f),
            KeyDefinition("£"),
            KeyDefinition("¥"),
            KeyDefinition("€"),
            KeyDefinition("¢"),
            KeyDefinition("^"),
            KeyDefinition("°"),
            KeyDefinition("="),
            KeyDefinition("{"),
            KeyDefinition("}"),
            KeyDefinition("", type = KeyType.SPACER, weight = 0.5f)
        ),
        listOf(
            KeyDefinition("?123", type = KeyType.SYMBOLS_PAGE_TOGGLE, weight = 1.5f, isFunctional = true),
            KeyDefinition("\\"),
            KeyDefinition("["),
            KeyDefinition("]"),
            KeyDefinition("<"),
            KeyDefinition(">"),
            KeyDefinition("«"),
            KeyDefinition("»"),
            KeyDefinition("⌫", type = KeyType.BACKSPACE, weight = 1.5f, isFunctional = true)
        ),
        listOf(
            KeyDefinition("ABC", type = KeyType.SYMBOLS_TOGGLE, weight = 1.4f, isFunctional = true),
            KeyDefinition("_", weight = 1.0f),
            KeyDefinition("space", type = KeyType.SPACE, weight = 4.8f),
            KeyDefinition("…", weight = 1.0f),
            KeyDefinition("↵", type = KeyType.ENTER, weight = 1.8f, isFunctional = true)
        )
    )

    val numericKeypad: List<List<KeyDefinition>> = listOf(
        listOf(KeyDefinition("1"), KeyDefinition("2"), KeyDefinition("3"), KeyDefinition("⌫", type = KeyType.BACKSPACE, isFunctional = true)),
        listOf(KeyDefinition("4"), KeyDefinition("5"), KeyDefinition("6"), KeyDefinition("-")),
        listOf(KeyDefinition("7"), KeyDefinition("8"), KeyDefinition("9"), KeyDefinition(".")),
        listOf(
            KeyDefinition("ABC", type = KeyType.SYMBOLS_TOGGLE, isFunctional = true),
            KeyDefinition("0"),
            KeyDefinition(",", type = KeyType.COMMA),
            KeyDefinition("↵", type = KeyType.ENTER, isFunctional = true)
        )
    )

    val phoneKeypad: List<List<KeyDefinition>> = listOf(
        listOf(KeyDefinition("1"), KeyDefinition("2"), KeyDefinition("3")),
        listOf(KeyDefinition("4"), KeyDefinition("5"), KeyDefinition("6")),
        listOf(KeyDefinition("7"), KeyDefinition("8"), KeyDefinition("9")),
        listOf(KeyDefinition("*"), KeyDefinition("0"), KeyDefinition("#")),
        listOf(
            KeyDefinition("ABC", type = KeyType.SYMBOLS_TOGGLE, weight = 1.0f, isFunctional = true),
            KeyDefinition("+", weight = 1.0f),
            KeyDefinition("⌫", type = KeyType.BACKSPACE, weight = 1.0f, isFunctional = true)
        )
    )
}
