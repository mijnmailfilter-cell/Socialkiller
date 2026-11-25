package nl.vliegendaapje.socialkiller

/** De bijdehande teksten. {m} = tijd, {app} = app-naam, {n} = hoeveelste pop-up. */
object Messages {

    data class Popup(val level: Int, val title: String, val body: String, val stay: String, val leave: String)

    private val level1 = listOf(
        "Kwartiertje {app}. Gezellig. Nog iets nuttigs gezien?",
        "{m}. Je duim is inmiddels fitter dan de rest van je lijf.",
        "Ben je nog niet klaar? Het internet is morgen ook nog open, hoor.",
        "Vind je het nog niet genoeg geweest? Er komt echt geen einde aan die feed.",
        "{m} scrollen. Ik zeg niks. Oké, ik zeg dít.",
        "Breaking news: er bestaan ook dingen buiten dit scherm.",
        "Nog eentje dan? Dat zei je {m} geleden ook.",
        "Het algoritme bedankt je hartelijk voor je {m}.",
        "Even checken: zoek je iets, of ben je gewoon verdwaald?",
        "Weet je wat ook leuk is? Precies. Bijna alles.",
        "Fun fact: niemand heeft ooit op zijn sterfbed gezegd 'had ik maar meer gescrold'.",
        "{m} in {app}. Heb je al gevonden waar je naar zocht? Nee? Dacht ik al.",
    )

    private val level2 = listOf(
        "{m}. Dat is een hele aflevering van een serie. Maar dan zonder verhaal.",
        "Je kijkt nu {m} naar andermans leven. Hoe gaat het eigenlijk met het jouwe?",
        "Ik ga niet zeggen dat je verslaafd bent. Maar {m}, hè.",
        "In {m} had je kunnen koken, wandelen of een dutje doen. Je koos dit.",
        "De aandeelhouders van {app} zijn je zeer dankbaar. Ze kopen er een jacht van.",
        "Je bent {m} verder en precies even wijs.",
        "Dit is pop-up nummer {n}. Je verzamelt ze nu gewoon, hè?",
        "Je duim heeft een vakbond opgericht. Ze willen pauze.",
        "Wist je dat je ook gewoon naar buiten kunt kijken? Daar is ook beeld. In HD.",
        "{m}. Als je zo doorgaat, moet ik je ouders bellen.",
    )

    private val level3 = listOf(
        "{m}. Die tijd krijg je nooit meer terug. Nooit.",
        "Je telefoon wordt warm. Van plaatsvervangende schaamte.",
        "Zelfs het algoritme vindt dat je nu even pauze moet nemen. Hét algoritme.",
        "{m}. Als dit een baan was, had je er inmiddels salaris voor gekregen.",
        "Leg. De. Telefoon. Neer. Dit is geen suggestie meer.",
        "Ik heb je {n} keer gewaarschuwd. Ik ben niet boos. Wel teleurgesteld.",
        "Ergens zit iemand die net zo lang naar jóúw posts kijkt. Vind je dat normaal? Nou dan.",
        "{m}. Er zijn mensen die in die tijd een halve marathon lopen. Jij hebt gescrold.",
        "Gefeliciteerd! Je hebt het einde van het internet bijna bereikt. Grapje, dat bestaat niet. Stop nou maar.",
        "Dit gesprek wordt ongemakkelijk. Voor ons allebei. Wegwezen hier.",
    )

    private val stay1 = listOf("Ja ja, ik weet het", "Nog even dan", "Laat me", "Bemoei je er niet mee")
    private val leave1 = listOf("Oké, ik stop", "Je hebt gelijk", "Breng me weg")
    private val stay2 = listOf("Nog héél even", "Ik heb het onder controle", "Dit is research")
    private val leave2 = listOf("Oké, ik stop ermee", "Haal me hier weg", "Fair enough")
    private val stay3 = listOf("Ik weet wat ik doe", "Ik ben volwassen", "Nog 1 filmpje (echt)")
    private val leave3 = listOf("Breng me weg hier", "Oké oké, ik stop", "Red me")

    fun levelFor(activeMs: Long): Int {
        val min = activeMs / 60000
        return when {
            min >= 90 -> 3
            min >= 45 -> 2
            else -> 1
        }
    }

    fun duration(ms: Long): String {
        val total = (ms / 60000).toInt()
        val h = total / 60
        val m = total % 60
        return when {
            h == 0 -> "$m minuten"
            m == 0 -> if (h == 1) "1 uur" else "$h uur"
            else -> "$h uur en $m minuten"
        }
    }

    fun shortDuration(ms: Long): String {
        val total = (ms / 60000).toInt()
        val h = total / 60
        val m = total % 60
        return if (h == 0) "${m}m" else "${h}u ${m}m"
    }

    fun build(level: Int, activeMs: Long, app: String, n: Int): Popup {
        val pool = when (level) { 3 -> level3; 2 -> level2; else -> level1 }
        fun fill(s: String) = s.replace("{m}", duration(activeMs)).replace("{app}", app).replace("{n}", n.toString())
        val title = when (level) {
            3 -> "Oké. Interventie."
            2 -> "Al ${duration(activeMs)}…"
            else -> "${duration(activeMs)} $app"
        }
        val (stay, leave) = when (level) {
            3 -> stay3.random() to leave3.random()
            2 -> stay2.random() to leave2.random()
            else -> stay1.random() to leave1.random()
        }
        return Popup(level, title, fill(pool.random()), stay, leave)
    }
}
