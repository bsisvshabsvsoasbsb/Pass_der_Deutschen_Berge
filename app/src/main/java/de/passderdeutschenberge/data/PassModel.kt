package de.passderdeutschenberge.data

/** Symboltyp eines Eintrags - entspricht der Legende auf Seite 2 des Passes. */
enum class TargetType { SUMMIT, PASS, WATER, ROCK, NATURE, CASTLE, TRAIL;

    companion object {
        fun from(raw: String?): TargetType = entries.firstOrNull { it.name == raw } ?: NATURE
    }
}

/** Abschnitt, unter dem ein Ziel auf der Regionsseite steht. */
enum class TargetCategory { CASTLE, NATURE, TRAIL, ROCK, WATER, COLLECTION;

    companion object {
        fun from(raw: String?): TargetCategory = entries.firstOrNull { it.name == raw } ?: NATURE
    }
}

data class Target(
    val id: String,
    val name: String,
    val type: TargetType,
    val category: TargetCategory,
    /** Nur bei Sammlungszielen gesetzt: Seite im Pass, auf der das Ziel steht. */
    val sourcePage: Int? = null,
)

data class SummitRef(val id: String?, val name: String, val page: Int)

/**
 * Beschreibungstexte des Passes sind schablonenhaft erzeugt. Statt 482 fertige
 * Texte doppelt vorzuhalten, werden nur die variablen Teile gespeichert und der
 * Satz zur Laufzeit aus lokalisierten Schablonen gebaut.
 */
sealed interface PassText {
    data class Summit(val nearby: List<String>) : PassText
    data class Region(
        val focus: List<String>,
        val top: List<String>,
        val peaks: List<String>,
    ) : PassText
    data class Collection(val top: List<String>) : PassText
    /** Ausweichfall: unveraenderter deutscher Text aus dem Pass. */
    data class Raw(val de: String) : PassText
}

data class Summit(
    val id: String,
    val slug: String,
    val name: String,
    val displayName: String,
    val elevationM: Double?,
    val page: Int,
    val regionId: String?,
    val macroRegionId: String?,
    val lat: Double?,
    val lon: Double?,
    val text: PassText,
)

data class Region(
    val id: String,
    val slug: String,
    val name: String,
    val displayName: String,
    val macroRegionId: String?,
    val page: Int,
    val pages: List<Int>,
    val lat: Double?,
    val lon: Double?,
    val summitRefs: List<SummitRef>,
    val targets: List<Target>,
    val text: PassText,
)

data class MacroRegion(
    val id: String,
    val slug: String,
    val name: String,
    val displayName: String,
    val page: Int,
    /** Charaktertext; als String-Ressource char_<slug> lokalisiert. */
    val characterResName: String,
    val keySummits: List<SummitRef>,
    val regionIds: List<String>,
    val lat: Double?,
    val lon: Double?,
    /** minLat, minLon, maxLat, maxLon - null bei rein thematischen Gruppen. */
    val bbox: List<Double>?,
    /** true = thematische Sammlung ohne Ort (Sammlungen &amp; Herausforderungen). */
    val virtual: Boolean,
)

/** Vollstaendig geladener Pass mit vorbereiteten Nachschlage-Indizes. */
class PassCatalog(
    val macroRegions: List<MacroRegion>,
    val regions: List<Region>,
    val summits: List<Summit>,
) {
    val macroById: Map<String, MacroRegion> = macroRegions.associateBy { it.id }
    val regionById: Map<String, Region> = regions.associateBy { it.id }
    val summitById: Map<String, Summit> = summits.associateBy { it.id }
    val summitByPage: Map<Int, Summit> = summits.associateBy { it.page }
    val targetById: Map<String, Target> = regions.flatMap { it.targets }.associateBy { it.id }
    val regionsByMacro: Map<String, List<Region>> = regions.groupBy { it.macroRegionId ?: "" }
    val summitsByRegion: Map<String, List<Summit>> = summits.groupBy { it.regionId ?: "" }
    val regionBySlug: Map<String, Region> = regions.associateBy { it.slug }

    // Fachliche Ersatzschluessel fuer den Datenaustausch. Die technischen IDs
    // enthalten die Seitenzahl aus dem Quell-PDF und koennen sich bei einer
    // neuen Fassung verschieben; diese Schluessel bleiben stabil.
    // Gipfel-Slugs allein sind nicht eindeutig (z. B. "hochstein" zweimal),
    // deshalb zusammengesetzt mit der Region.
    val summitByKey: Map<String, Summit> = summits.mapNotNull { summit ->
        val region = regionById[summit.regionId] ?: return@mapNotNull null
        summitKey(region.slug, summit.slug) to summit
    }.toMap()

    val targetByKey: Map<String, Target> = regions.flatMap { region ->
        region.targets.map { target -> targetKey(region.slug, target.name) to target }
    }.toMap()

    fun keyOfSummit(summit: Summit): String? =
        regionById[summit.regionId]?.let { summitKey(it.slug, summit.slug) }

    fun keyOfTarget(targetId: String): String? = regions.firstNotNullOfOrNull { region ->
        region.targets.firstOrNull { it.id == targetId }?.let { targetKey(region.slug, it.name) }
    }

    /** Grossregionen mit Ort - Grundlage der Kartenansicht. */
    val locatedMacroRegions: List<MacroRegion> = macroRegions.filter { !it.virtual }
    val collectionMacroRegions: List<MacroRegion> = macroRegions.filter { it.virtual }

    val totalSummits: Int = summits.size
    val totalTargets: Int = targetById.size

    fun regionsOf(macro: MacroRegion): List<Region> =
        macro.regionIds.mapNotNull { regionById[it] }

    fun targetsOf(macro: MacroRegion): List<Target> =
        regionsOf(macro).flatMap { it.targets }

    fun summitsOf(macro: MacroRegion): List<Summit> =
        summits.filter { it.macroRegionId == macro.id }

    companion object {
        fun summitKey(regionSlug: String, summitSlug: String) = "$regionSlug/$summitSlug"
        fun targetKey(regionSlug: String, targetName: String) = "$regionSlug/$targetName"
    }
}
