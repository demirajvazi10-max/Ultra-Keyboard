package com.ultra.keyboard

/**
 * Multi-tap slova po tasteru, kao na starim Nokia telefonima.
 * Poslednji element u svakoj listi je uvek sama cifra (npr. '2'),
 * tako da uzastopni pritisci na kraju vrate broj ako korisnik nastavi da kuca.
 *
 * Raspored nije "sveti", lako se menja - samo izmeni liste ispod.
 */
object KeyMaps {

    // Srpska latinica - klasičan Nokia raspored (puna engleska abeceda po
    // tasteru), sa srpskim slovima dodatim na kraj odgovarajućeg tastera
    val LATIN: Map<Int, List<Char>> = mapOf(
        2 to listOf('a', 'b', 'c', 'č', 'ć', '@', '2'),
        3 to listOf('d', 'e', 'f', 'đ', '3'),
        4 to listOf('g', 'h', 'i', '4'),
        5 to listOf('j', 'k', 'l', '5'),
        6 to listOf('m', 'n', 'o', '6'),
        7 to listOf('p', 'q', 'r', 's', 'š', '7'),
        8 to listOf('t', 'u', 'v', '8'),
        9 to listOf('w', 'x', 'y', 'z', 'ž', '9')
    )

    // Srpska ćirilica - 30 slova raspoređenih na tasterima 2-9
    val CYRILLIC: Map<Int, List<Char>> = mapOf(
        2 to listOf('а', 'б', 'в', 'г', '2'),
        3 to listOf('д', 'ђ', 'е', 'ж', '3'),
        4 to listOf('з', 'и', 'ј', 'к', '4'),
        5 to listOf('л', 'љ', 'м', 'н', '5'),
        6 to listOf('њ', 'о', 'п', 'р', '6'),
        7 to listOf('с', 'т', 'ћ', 'у', '7'),
        8 to listOf('ф', 'х', 'ц', 'ч', '8'),
        9 to listOf('џ', 'ш', '9')
    )

    // Taster 1: interpunkcija koja se najčešće koristi
    val PUNCT_1: List<Char> = listOf('.', ',', '?', '!', '\'', '"', '1')

    // Taster * (kratak pritisak): brzi simboli; dugi pritisak otvara pun panel simbola
    val QUICK_SYMBOLS: List<Char> = listOf('-', ':', ';', '@', '/', '*')

    // Taster 0: razmak, dugi pritisak i dalje daje cifru 0
    val KEY_0: List<Char> = listOf(' ', '0')

    fun mapFor(cyrillic: Boolean): Map<Int, List<Char>> = if (cyrillic) CYRILLIC else LATIN

    // Zvezdica, mod BROJEVI: svaki taster direktno unosi svoju cifru (bez cikliranja)
    val NUMBERS: Map<Int, Char> = mapOf(
        1 to '1', 2 to '2', 3 to '3', 4 to '4', 5 to '5',
        6 to '6', 7 to '7', 8 to '8', 9 to '9', 0 to '0'
    )

    // Zvezdica, mod SIMBOLI: svaki taster direktno unosi svoj simbol (bez cikliranja)
    val SYMBOLS_MODE: Map<Int, Char> = mapOf(
        1 to '?', 2 to '\\', 3 to '^', 4 to ':', 5 to '=',
        6 to '°', 7 to '$', 8 to '|', 9 to '[', 0 to '~'
    )

    // Puni panel simbola (otvoren dugim pritiskom na *)
    val SYMBOLS_FULL: List<String> = listOf(
        ".", ",", "?", "!", ":", ";", "-", "_", "'", "\"",
        "(", ")", "[", "]", "{", "}", "@", "#", "$", "%",
        "&", "*", "+", "=", "/", "\\", "<", ">", "€", "din."
    )

    // Osnovni set emotikona (dovoljno za početak, lako proširiti)
    val EMOJI: List<Pair<String, String>> = listOf(
        "😀" to "nasmejano lice",
        "😂" to "smeh do suza",
        "😊" to "osmeh",
        "😍" to "zaljubljeno lice",
        "😘" to "poljubac",
        "😉" to "namigivanje",
        "😢" to "plač",
        "😭" to "jak plač",
        "😡" to "ljutnja",
        "😱" to "šok",
        "👍" to "palac gore",
        "👎" to "palac dole",
        "👏" to "aplauz",
        "🙏" to "molba, hvala",
        "❤️" to "srce",
        "💔" to "slomljeno srce",
        "🔥" to "vatra",
        "🎉" to "slavlje",
        "✅" to "kvačica, potvrđeno",
        "❌" to "iks, netačno",
        "☀️" to "sunce",
        "🌧️" to "kiša",
        "☕" to "kafa",
        "🐶" to "pas",
        "🐱" to "mačka",
        "⚽" to "fudbal",
        "🚗" to "auto",
        "📱" to "telefon",
        "⏰" to "budilnik",
        "🎂" to "rođendanska torta"
    )
}
