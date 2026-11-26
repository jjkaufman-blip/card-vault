package com.cardvault.scan

/** Reference lists used for filters, form suggestions and reading card text. */
object Catalog {
    /** Brand name -> words that identify it on a card. */
    val brands: List<Pair<String, List<String>>> = listOf(
        "Upper Deck" to listOf("upper deck"),
        "Pro Set" to listOf("pro set", "proset", "nfl pro set"),
        "Collector's Edge" to listOf("collector's edge", "collectors edge"),
        "Action Packed" to listOf("action packed"),
        "Press Pass" to listOf("press pass"),
        "Stadium Club" to listOf("stadium club"),
        "Topps" to listOf("topps"),
        "Fleer" to listOf("fleer"),
        "Score" to listOf("score"),
        "Panini" to listOf("panini"),
        "Pinnacle" to listOf("pinnacle"),
        "Donruss" to listOf("donruss"),
        "Bowman" to listOf("bowman"),
        "Leaf" to listOf("leaf"),
        "Playoff" to listOf("playoff"),
        "Pacific" to listOf("pacific"),
        "SkyBox" to listOf("skybox", "sky box"),
        "Classic" to listOf("classic"),
        "Philadelphia" to listOf("philadelphia gum", "philadelphia"),
        "Sage" to listOf("sage"),
        "Wild Card" to listOf("wild card"),
        "Pro Line" to listOf("pro line", "proline"),
        "Ultra" to listOf("fleer ultra"),
    )

    val brandNames: List<String> = brands.map { it.first }.distinct().sorted()

    val sets: List<String> = listOf(
        "Prizm", "Select", "Optic", "Mosaic", "Chrome", "Finest", "Ultra", "Flair", "Contenders",
        "National Treasures", "Absolute", "Certified", "Elite", "Rookies & Stars", "Heritage",
        "Spectra", "Phoenix", "Chronicles", "Immaculate", "Flawless", "Gridiron Kings", "Zenith",
        "SP Authentic", "SPx", "Exquisite", "Playbook", "Prestige", "Limited", "Gold Standard",
        "Impact", "Metal", "E-X2000", "Crown Royale", "Revolution",
    )

    /** Full team name -> nickname as printed on most cards. Includes historical teams. */
    val teams: List<Pair<String, String>> = listOf(
        "Arizona Cardinals" to "Cardinals", "Atlanta Falcons" to "Falcons",
        "Baltimore Ravens" to "Ravens", "Buffalo Bills" to "Bills",
        "Carolina Panthers" to "Panthers", "Chicago Bears" to "Bears",
        "Cincinnati Bengals" to "Bengals", "Cleveland Browns" to "Browns",
        "Dallas Cowboys" to "Cowboys", "Denver Broncos" to "Broncos",
        "Detroit Lions" to "Lions", "Green Bay Packers" to "Packers",
        "Houston Texans" to "Texans", "Indianapolis Colts" to "Colts",
        "Jacksonville Jaguars" to "Jaguars", "Kansas City Chiefs" to "Chiefs",
        "Las Vegas Raiders" to "Raiders", "Los Angeles Chargers" to "Chargers",
        "Los Angeles Rams" to "Rams", "Miami Dolphins" to "Dolphins",
        "Minnesota Vikings" to "Vikings", "New England Patriots" to "Patriots",
        "New Orleans Saints" to "Saints", "New York Giants" to "Giants",
        "New York Jets" to "Jets", "Philadelphia Eagles" to "Eagles",
        "Pittsburgh Steelers" to "Steelers", "San Francisco 49ers" to "49ers",
        "Seattle Seahawks" to "Seahawks", "Tampa Bay Buccaneers" to "Buccaneers",
        "Tennessee Titans" to "Titans", "Washington Commanders" to "Commanders",
        "Houston Oilers" to "Oilers", "Washington Redskins" to "Redskins",
        "Washington Football Team" to "Football Team",
    )

    val teamNames: List<String> = teams.map { it.first }.sorted()

    /** Position abbreviation -> words that identify it. */
    val positions: List<Pair<String, List<String>>> = listOf(
        "QB" to listOf("qb", "quarterback"),
        "RB" to listOf("rb", "running back", "halfback", "hb"),
        "FB" to listOf("fb", "fullback"),
        "WR" to listOf("wr", "wide receiver", "receiver", "flanker"),
        "TE" to listOf("te", "tight end"),
        "OL" to listOf("ol", "ot", "og", "tackle", "guard", "center", "offensive line"),
        "DL" to listOf("dl", "de", "dt", "nt", "defensive end", "defensive tackle", "nose tackle"),
        "LB" to listOf("lb", "olb", "ilb", "mlb", "linebacker"),
        "CB" to listOf("cb", "cornerback"),
        "S" to listOf("safety", "fs", "ss", "free safety", "strong safety"),
        "DB" to listOf("db", "defensive back"),
        "K" to listOf("kicker", "pk"),
        "P" to listOf("punter"),
        "KR" to listOf("kr", "pr", "kick returner", "punt returner"),
        "Coach" to listOf("coach", "head coach"),
    )

    val positionNames: List<String> = positions.map { it.first }

    val graders: List<String> = listOf("PSA", "BGS", "SGC", "CGC")
}
