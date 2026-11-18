package de.fembiond.app.domain

/**
 * Startbegriffe mit Synonymen und Umgangssprache.
 * Grundlage: DRSP, Menopause Rating Scale, DSM-5-Paniksymptome.
 */
data class LexEntry(
    val label: String,
    val category: Category,
    val synonyms: List<String> = emptyList(),
    val valence: Valence? = null,
    /** Als Startvorschlag sichtbar (manche Einträge dienen nur der Erkennung). */
    val suggested: Boolean = true,
)

object Lexicon {

    private fun e(label: String, cat: Category, vararg syn: String, valence: Valence? = null) =
        LexEntry(label, cat, syn.toList(), valence)

    val entries: List<LexEntry> = listOf(
        // Stimmung & Geist
        e("gereizt", Category.MIND, "reizbar", "genervt", "aggressiv", "zickig", "dünnhäutig", "ungeduldig", "wütend", "wut", "sauer"),
        e("traurig", Category.MIND, "niedergeschlagen", "deprimiert", "down", "bedrückt", "weinerlich", "weinen", "geweint", "heulen", "geheult", "melancholisch", "nah am wasser"),
        e("ängstlich", Category.MIND, "angst", "ängste", "besorgt", "sorgen", "verängstigt", "furcht"),
        e("angespannt", Category.MIND, "anspannung", "verspannt innerlich", "unter strom", "nervös", "nervosität", "gestresst", "gestresst gefühlt"),
        e("innere Unruhe", Category.MIND, "unruhe", "unruhig", "rastlos", "kribbelig", "aufgewühlt", "getrieben", "zappelig", "hibbelig"),
        e("überfordert", Category.MIND, "überforderung", "alles zu viel", "zu viel", "überwältigt"),
        e("Stimmungsschwankungen", Category.MIND, "stimmungsschwankung", "launisch", "launen", "himmelhoch jauchzend", "auf und ab"),
        e("ausgeglichen", Category.MIND, "entspannt", "ruhig", "gelassen", "zufrieden", "ausgewogen"),
        e("fröhlich", Category.MIND, "gut gelaunt", "gute laune", "glücklich", "happy", "froh", "heiter"),
        e("konzentrationsschwach", Category.MIND, "unkonzentriert", "konzentrationsprobleme", "konzentrationsschwäche", "brain fog", "nebel im kopf", "benebelt", "zerstreut"),
        e("vergesslich", Category.MIND, "vergesslichkeit", "gedächtnisprobleme", "vergessen"),
        e("Wortfindungsstörungen", Category.MIND, "wörter fehlen", "wort fällt nicht ein", "wortfindung"),
        e("Wutausbrüche", Category.MIND, "wutanfall", "ausgerastet", "explodiert", "rage"),
        e("antriebslos", Category.MIND, "lustlos", "keine lust", "unmotiviert", "antriebslosigkeit"),
        e("sensibel", Category.MIND, "empfindlich", "verletzlich"),
        e("Rückzug", Category.MIND, "sozialer rückzug", "zurückgezogen", "will niemanden sehen", "will meine ruhe", "keine lust auf menschen"),
        e("Libido gering", Category.MIND, "keine lust auf sex", "wenig libido", "libido niedrig"),
        e("Libido hoch", Category.MIND, "viel lust auf sex", "libido stark"),

        // Körper
        e("Krämpfe", Category.BODY, "krampf", "unterleibskrämpfe", "bauchkrämpfe", "regelschmerzen", "periodenschmerzen", "unterleibsschmerzen", "menstruationsschmerzen"),
        e("Kopfschmerzen", Category.BODY, "kopfweh", "kopfschmerz", "schädelweh", "brummschädel"),
        e("Migräne", Category.BODY, "migräneanfall"),
        e("Brustspannen", Category.BODY, "brustschmerzen", "spannende brüste", "brüste spannen", "brust tut weh", "empfindliche brust"),
        e("Blähungen", Category.BODY, "blähbauch", "aufgebläht", "gebläht", "dicker bauch", "aufgedunsen"),
        e("Rückenschmerzen", Category.BODY, "rückenweh", "rücken tut weh", "kreuzschmerzen"),
        e("Gelenkschmerzen", Category.BODY, "gelenke tun weh", "gliederschmerzen", "muskelschmerzen"),
        e("Herzklopfen", Category.BODY, "herzstolpern", "herzpochen"),
        e("Übelkeit", Category.BODY, "übel", "schlecht im magen", "brechreiz", "mir ist schlecht"),
        e("Hautunreinheiten", Category.BODY, "pickel", "unreine haut", "akne"),
        e("Heißhunger", Category.BODY, "fressattacke", "appetit auf süßes", "hunger auf süßes", "heisshunger"),
        e("Verdauungsprobleme", Category.BODY, "durchfall", "verstopfung", "bauchschmerzen", "bauchweh"),
        e("Wassereinlagerungen", Category.BODY, "wasser eingelagert", "geschwollene beine", "geschwollen"),
        e("Schwindel", Category.BODY, "schwindelig", "schwummrig", "benommen"),

        // Perimenopause
        e("Hitzewallung", Category.PERIMENOPAUSE, "hitzewallungen", "wallungen", "hitzewelle", "hitzeschub", "fliegende hitze"),
        e("Nachtschweiß", Category.PERIMENOPAUSE, "nachts geschwitzt", "nassgeschwitzt", "schweißgebadet", "nachtschweiss"),
        e("Herzrasen", Category.PERIMENOPAUSE, "herzjagen", "puls hoch"),
        e("Scheidentrockenheit", Category.PERIMENOPAUSE, "trockene scheide", "vaginale trockenheit"),
        e("Harndrang", Category.PERIMENOPAUSE, "ständig aufs klo", "blasenschwäche", "häufiges wasserlassen"),
        e("trockene Haut / Juckreiz", Category.PERIMENOPAUSE, "trockene haut", "juckreiz", "haut juckt"),
        e("Haarausfall", Category.PERIMENOPAUSE, "haare fallen aus", "dünnes haar"),
        e("Gewichtszunahme", Category.PERIMENOPAUSE, "zugenommen", "bauchfett"),
        e("Tinnitus", Category.PERIMENOPAUSE, "ohrensausen", "ohrgeräusche", "pfeifen im ohr"),
        e("Kribbeln / Missempfindungen", Category.PERIMENOPAUSE, "missempfindungen", "stromschlag gefühl", "elektrisches gefühl"),
        e("trockene Augen", Category.PERIMENOPAUSE, "augen brennen", "augen trocken"),

        // Schlaf & Energie
        e("müde", Category.SLEEP_ENERGY, "müdigkeit", "schläfrig", "kaputt", "platt"),
        e("erschöpft", Category.SLEEP_ENERGY, "erschöpfung", "ausgelaugt", "fertig", "total fertig", "k.o.", "ko"),
        e("energiegeladen", Category.SLEEP_ENERGY, "voller energie", "fit", "tatendrang", "power"),
        e("Einschlafprobleme", Category.SLEEP_ENERGY, "nicht einschlafen", "schlecht eingeschlafen", "einschlafstörung"),
        e("Frühes Erwachen", Category.SLEEP_ENERGY, "früh aufgewacht", "um drei aufgewacht", "um 3 aufgewacht", "zu früh wach", "morgens früh wach"),
        e("Durchschlafprobleme", Category.SLEEP_ENERGY, "oft aufgewacht", "nachts aufgewacht", "aufgewacht", "durchschlafstörung", "wach gelegen"),
        e("schlecht geschlafen", Category.SLEEP_ENERGY, "kaum geschlafen", "wenig geschlafen", "nicht gut geschlafen", "unruhig geschlafen", "miserabel geschlafen", "schlaflos", "schlaflosigkeit"),
        e("gut geschlafen", Category.SLEEP_ENERGY, "super geschlafen", "durchgeschlafen", "ausgeschlafen", "erholsam geschlafen"),

        // Blutung
        e("Schmierblutung", Category.BLEEDING, "schmieren", "spotting", "schmierblutungen"),
        e("Blutung leicht", Category.BLEEDING, "leichte blutung", "periode leicht", "leichte periode"),
        e("Blutung mittel", Category.BLEEDING, "mittlere blutung", "normale blutung"),
        e("Blutung stark", Category.BLEEDING, "starke blutung", "periode stark", "starke periode", "starke regel"),
        e("Blutung sehr stark", Category.BLEEDING, "sehr starke blutung", "extrem starke blutung", "durchgeblutet"),
        e("Zwischenblutung", Category.BLEEDING, "zwischenblutungen"),

        // Lebensumstände
        e("Arbeitsstress", Category.LIFE, "stress auf der arbeit", "stressiger arbeitstag", "viel arbeit", "deadline", "überstunden", "stress im job", "arbeit stressig", valence = Valence.BURDEN),
        e("Arbeitstag", Category.LIFE, "gearbeitet", "auf der arbeit", "im büro", valence = Valence.NEUTRAL),
        e("letzter Arbeitstag", Category.LIFE, "letzten arbeitstag", "letzter tag auf der arbeit", valence = Valence.NEUTRAL),
        e("Urlaub", Category.LIFE, "frei", "freier tag", "ferien", valence = Valence.POSITIVE),
        e("Streit", Category.LIFE, "gestritten", "auseinandersetzung", "konflikt", "zoff", valence = Valence.BURDEN),
        e("Familie", Category.LIFE, "eltern", "kinder", "schwester", "bruder", "mutter", "vater", "schwiegereltern"),
        e("Beziehung", Category.LIFE, "partner", "freund", "zweisamkeit", "date"),
        e("Treffen mit Freunden", Category.LIFE, "freunde getroffen", "freundin getroffen", "mit freunden", "verabredung", valence = Valence.POSITIVE),
        e("Feier", Category.LIFE, "party", "geburtstag", "geburtstagsfeier", "hochzeit", "familienfeier", valence = Valence.POSITIVE),
        e("Essen gehen", Category.LIFE, "essen gegangen", "restaurant", "essen gehen", "auswärts gegessen"),
        e("Konzert", Category.LIFE, "konzert", "festival", "auftritt"),
        e("Kino / Theater", Category.LIFE, "kino", "theater", "oper", "musical"),
        e("Reise", Category.LIFE, "unterwegs", "verreist", "flug", "zugfahrt", "dienstreise"),
        e("Infekt", Category.LIFE, "erkältung", "erkältet", "krank", "grippe", "fieber", "corona", valence = Valence.BURDEN),
        e("Sport", Category.LIFE, "joggen", "laufen", "yoga", "fitness", "training", "trainiert", "schwimmen", "radfahren", "spaziergang", "spazieren", valence = Valence.POSITIVE),
        e("Alkohol", Category.LIFE, "wein", "bier", "sekt", "getrunken", "cocktail"),
        e("viel Koffein", Category.LIFE, "viel kaffee", "kaffee", "koffein", "energy drink"),
        e("Arzttermin", Category.LIFE, "beim arzt", "arzt", "frauenarzt", "gynäkologin", "gynäkologe", "termin beim arzt"),
        e("Termin", Category.LIFE, "termine", "wichtiger termin", "prüfung", "präsentation", "vorstellungsgespräch"),
        e("Belastung", Category.LIFE, "stress", "stressig", "belastend", "ärger", "sorgen gemacht", valence = Valence.BURDEN),
        e("schönes Erlebnis", Category.LIFE, "schöner tag", "toller tag", "schön gewesen", valence = Valence.POSITIVE),

        // Medikation / Nebenwirkungen
        e("Schläfrigkeit", Category.MEDICATION_EFFECT, "benommen nach tablette", "schläfrig nach einnahme"),
        e("Stimmungsveränderung", Category.MEDICATION_EFFECT, "stimmung verändert"),

        // Unruhe: körperliche Anzeichen
        e("Herzklopfen", Category.PANIC_SYMPTOM),
        e("Herzrasen", Category.PANIC_SYMPTOM),
        e("Magen / Bauchgrummeln", Category.PANIC_SYMPTOM),
        e("Kloß im Hals", Category.PANIC_SYMPTOM),
        e("Gedankenkreisen", Category.PANIC_SYMPTOM),
        e("Muskelanspannung", Category.PANIC_SYMPTOM),
        e("Zittern", Category.PANIC_SYMPTOM, "gezittert", "zitterig"),
        e("Schwitzen", Category.PANIC_SYMPTOM),
        e("Schwindel", Category.PANIC_SYMPTOM),
        e("Atemnot", Category.PANIC_SYMPTOM, "kurzatmig", "keine luft", "luftnot"),
        e("Engegefühl in der Brust", Category.PANIC_SYMPTOM, "brustenge", "druck auf der brust"),
        e("Hitze oder Kälte", Category.PANIC_SYMPTOM, "hitze", "kälteschauer", "frösteln"),
        e("Kribbeln", Category.PANIC_SYMPTOM, "taubheit", "ameisenlaufen"),
        e("Übelkeit", Category.PANIC_SYMPTOM),
        e("Angst vor Kontrollverlust", Category.PANIC_SYMPTOM, "todesangst", "angst verrückt zu werden"),
        e("Unwirklichkeitsgefühl", Category.PANIC_SYMPTOM, "derealisation", "neben mir gestanden", "wie im film"),
        // Unruhe: Auslöser
        e("Anstehendes Ereignis", Category.PANIC_TRIGGER),
        e("Menschenmenge", Category.PANIC_TRIGGER),
        e("Stress", Category.PANIC_TRIGGER),
        e("Gedanken an später", Category.PANIC_TRIGGER),
        e("nachts aus dem Schlaf", Category.PANIC_TRIGGER),
        e("Autofahren", Category.PANIC_TRIGGER),
        e("Enge Räume", Category.PANIC_TRIGGER),
        e("ohne erkennbaren Grund", Category.PANIC_TRIGGER),
        // Unruhe: was geholfen hat
        e("Atemübung", Category.PANIC_HELP),
        e("Bewegung", Category.PANIC_HELP),
        e("Frische Luft", Category.PANIC_HELP),
        e("Gespräch", Category.PANIC_HELP),
        e("Ablenkung", Category.PANIC_HELP),
        e("Rückzug / Pause", Category.PANIC_HELP),
        e("Medikament", Category.PANIC_HELP),
        e("Kälte (Wasser, Eis)", Category.PANIC_HELP),
        // Ereignisarten
        e("Geburtstagsfeier", Category.EVENT_TYPE),
        e("Essen gehen", Category.EVENT_TYPE),
        e("Konzert", Category.EVENT_TYPE),
        e("Party / Feier", Category.EVENT_TYPE),
        e("Familienfeier", Category.EVENT_TYPE),
        e("Treffen mit Freunden", Category.EVENT_TYPE),
        e("Kino / Theater", Category.EVENT_TYPE),
        e("Arzttermin", Category.EVENT_TYPE),
        e("Termin bei der Arbeit", Category.EVENT_TYPE),
        e("Reise", Category.EVENT_TYPE),
        e("Einkaufen / Menschenmenge", Category.EVENT_TYPE),
    )

    /** Startbegriffe für eine Kategorie. */
    fun seeds(category: Category): List<LexEntry> = entries.filter { it.category == category && it.suggested }
}
