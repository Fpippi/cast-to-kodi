package it.franc.casttokodi

import java.util.Locale

// Lingua dell'interfaccia: italiano o inglese. Ogni testo dell'app è scritto qui accanto all'altro, tr("Manda", "Send"),
// così vale anche nei file senza android.* (Rules.kt, sites/…) e I18nTest controlla che nessun testo resti solo in italiano.
object Lang {
    // Scelte di prefs["language"]: AUTO segue il telefono (italiano solo se il telefono è in italiano)
    const val AUTO = "auto"
    const val ITALIAN = "it"
    const val ENGLISH = "en"

    // Italiano finché l'app non sceglie (così i test sulla JVM vedono i testi italiani di sempre)
    @Volatile
    var english = false

    fun choose(choice: String?, system: Locale = Locale.getDefault()) {
        english = isEnglish(choice, system)
    }

    fun isEnglish(choice: String?, system: Locale): Boolean = when (choice) {
        ITALIAN -> false
        ENGLISH -> true
        else -> !system.language.equals(ITALIAN, ignoreCase = true)
    }

    // Per i nomi delle lingue e i numeri ("Inglese" / "English", "1,5 MB" / "1.5 MB")
    val locale: Locale get() = if (english) Locale.ENGLISH else Locale.ITALIAN
}

fun tr(italian: String, english: String): String = if (Lang.english) english else italian

// Nome di una lingua dentro una frase: minuscolo in italiano ("in inglese"), maiuscolo in inglese ("in English")
fun inSentence(name: String): String = if (Lang.english) name else name.lowercase(Locale.ITALIAN)

// Come si chiama ogni scelta nelle Impostazioni
fun languageChoiceLabel(choice: String?): String = when (choice) {
    Lang.ITALIAN -> "Italiano"
    Lang.ENGLISH -> "English"
    else -> tr("Come il telefono", "Same as the phone")
}
