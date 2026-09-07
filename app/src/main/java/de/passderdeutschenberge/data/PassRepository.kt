package de.passderdeutschenberge.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Laedt Passdaten und Kartengeometrie aus den App-Assets.
 *
 * Bewusst mit org.json (Framework-API) statt einer Serialisierungsbibliothek:
 * die Daten werden genau einmal beim Start gelesen, ein Codegenerator wuerde
 * hier nur Buildzeit kosten.
 */
class PassRepository(private val context: Context) {

    suspend fun loadCatalog(): PassCatalog = withContext(Dispatchers.IO) {
        val root = JSONObject(readAsset("pass_data.json"))
        PassCatalog(
            macroRegions = root.getJSONArray("macroRegions").map { it.toMacroRegion() },
            regions = root.getJSONArray("regions").map { it.toRegion() },
            summits = root.getJSONArray("summits").map { it.toSummit() },
        )
    }

    suspend fun loadGermany(): GermanyOutline = withContext(Dispatchers.IO) {
        val root = JSONObject(readAsset("germany.json"))
        GermanyOutline(
            attribution = root.optString("attribution"),
            states = root.getJSONArray("states").map { state ->
                FederalState(
                    code = state.optString("code"),
                    name = state.optString("name"),
                    rings = state.getJSONArray("rings").mapArrays { ring ->
                        DoubleArray(ring.length()) { ring.getDouble(it) }
                    },
                )
            },
        )
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

// --- JSON-Hilfen -------------------------------------------------------------

private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
    List(length()) { transform(getJSONObject(it)) }

private inline fun <T> JSONArray.mapArrays(transform: (JSONArray) -> T): List<T> =
    List(length()) { transform(getJSONArray(it)) }

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)

private fun JSONObject.optStringList(key: String): List<String> =
    optJSONArray(key)?.toStringList() ?: emptyList()

private fun JSONObject.toPassText(): PassText {
    val text = optJSONObject("text") ?: return PassText.Raw("")
    return when (text.optString("kind")) {
        "summit" -> PassText.Summit(nearby = text.optStringList("nearby"))
        "region" -> PassText.Region(
            focus = text.optStringList("focus"),
            top = text.optStringList("top"),
            peaks = text.optStringList("peaks"),
        )
        "collection" -> PassText.Collection(top = text.optStringList("top"))
        else -> PassText.Raw(text.optString("de"))
    }
}

private fun JSONObject.toSummitRef() = SummitRef(
    id = optStringOrNull("id"),
    name = optString("name"),
    page = optInt("page"),
)

private fun JSONObject.toMacroRegion(): MacroRegion {
    val slug = optString("slug")
    return MacroRegion(
        id = optString("id"),
        slug = slug,
        name = optString("name"),
        displayName = optString("displayName"),
        page = optInt("page"),
        characterResName = "char_" + slug.replace('-', '_'),
        keySummits = optJSONArray("keySummits")?.map { it.toSummitRef() } ?: emptyList(),
        regionIds = optStringList("regionIds"),
        lat = optDoubleOrNull("lat"),
        lon = optDoubleOrNull("lon"),
        bbox = optJSONArray("bbox")?.let { arr -> List(arr.length()) { arr.getDouble(it) } },
        virtual = optBoolean("virtual"),
    )
}

private fun JSONObject.toRegion() = Region(
    id = optString("id"),
    slug = optString("slug"),
    name = optString("name"),
    displayName = optString("displayName"),
    macroRegionId = optStringOrNull("macroRegionId"),
    page = optInt("page"),
    pages = optJSONArray("pages")?.let { arr -> List(arr.length()) { arr.getInt(it) } } ?: emptyList(),
    lat = optDoubleOrNull("lat"),
    lon = optDoubleOrNull("lon"),
    summitRefs = optJSONArray("summitRefs")?.map { it.toSummitRef() } ?: emptyList(),
    targets = optJSONArray("targets")?.map { target ->
        Target(
            id = target.optString("id"),
            name = target.optString("name"),
            type = TargetType.from(target.optStringOrNull("type")),
            category = TargetCategory.from(target.optStringOrNull("category")),
            sourcePage = target.optIntOrNull("sourcePage"),
        )
    } ?: emptyList(),
    text = toPassText(),
)

private fun JSONObject.toSummit() = Summit(
    id = optString("id"),
    slug = optString("slug"),
    name = optString("name"),
    displayName = optString("displayName"),
    elevationM = optDoubleOrNull("elevationM"),
    page = optInt("page"),
    regionId = optStringOrNull("regionId"),
    macroRegionId = optStringOrNull("macroRegionId"),
    lat = optDoubleOrNull("lat"),
    lon = optDoubleOrNull("lon"),
    text = toPassText(),
)
