package it.franc.casttokodi.sites

import it.franc.casttokodi.enc

// Addon Google: pagina iniziale, ricerche dalla barra in alto e salti liberi dai risultati.

const val GOOGLE = "https://www.google.com"

fun googleSearch(query: String) = "$GOOGLE/search?q=${enc(query)}"

// Dai risultati di Google si va su altri siti per forza. Non serve il tocco (hasGesture): Google apre
// alcuni risultati da JS e il WebView non lo segna come gesto; su Google non ci sono salti pubblicitari
fun isGoogle(domain: String): Boolean = domain.startsWith("google.")

// Pagina dei cookie che Google mostra in UE al primo avvio (consent.google.com, .it...): "Accetta" o "Rifiuta"
// la chiudono e tornano alla ricerca. Non va ricordata come ultima pagina, o all'avvio dopo si riaprirebbe lì
fun isGoogleConsent(host: String?): Boolean = host.orEmpty().lowercase().startsWith("consent.google.")
