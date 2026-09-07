package de.passderdeutschenberge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pass_progress")

/**
 * Persistenz des Sammelfortschritts.
 *
 * Der gesamte Fortschritt passt in ein JSON-Dokument von wenigen hundert
 * Kilobyte; eine relationale Ablage waere hier Aufwand ohne Nutzen, da immer
 * der komplette Stand gelesen und in-memory ausgewertet wird.
 */
class ProgressStore(private val context: Context) {

    private val progressKey = stringPreferencesKey("progress_json")
    private val languageKey = stringPreferencesKey("language_tag")

    val progress: Flow<PassProgress> = context.dataStore.data.map { prefs ->
        prefs[progressKey]?.let(::decode) ?: PassProgress()
    }

    /** Leerer Wert = Systemsprache. */
    val languageTag: Flow<String> = context.dataStore.data.map { it[languageKey].orEmpty() }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit { it[languageKey] = tag }
    }

    suspend fun updateSummit(id: String, transform: (SummitEntry) -> SummitEntry) {
        mutate { current ->
            val updated = transform(current.summit(id))
            current.copy(
                summits = if (updated.isEmpty) current.summits - id
                else current.summits + (id to updated),
            )
        }
    }

    suspend fun updateTarget(id: String, transform: (TargetEntry) -> TargetEntry) {
        mutate { current ->
            val updated = transform(current.target(id))
            current.copy(
                targets = if (updated.isEmpty) current.targets - id
                else current.targets + (id to updated),
            )
        }
    }

    suspend fun setRegionNote(id: String, note: String) {
        mutate { current ->
            current.copy(
                regionNotes = if (note.isBlank()) current.regionNotes - id
                else current.regionNotes + (id to note),
            )
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.remove(progressKey) }
    }

    private suspend fun mutate(transform: (PassProgress) -> PassProgress) {
        context.dataStore.edit { prefs ->
            val current = prefs[progressKey]?.let(::decode) ?: PassProgress()
            prefs[progressKey] = encode(transform(current))
        }
    }

    private fun encode(progress: PassProgress): String {
        val summits = JSONObject()
        progress.summits.forEach { (id, entry) ->
            summits.put(
                id,
                JSONObject()
                    .put("d", entry.done)
                    .put("dt", entry.date)
                    .put("sp", entry.startPoint)
                    .put("rt", entry.route)
                    .put("di", entry.distance)
                    .put("tm", entry.duration)
                    .put("eg", entry.elevationGain)
                    .put("ra", entry.rating)
                    .put("no", entry.notes),
            )
        }
        val targets = JSONObject()
        progress.targets.forEach { (id, entry) ->
            targets.put(id, JSONObject().put("d", entry.done).put("dt", entry.date))
        }
        val notes = JSONObject()
        progress.regionNotes.forEach { (id, note) -> notes.put(id, note) }
        return JSONObject()
            .put("v", 1)
            .put("summits", summits)
            .put("targets", targets)
            .put("notes", notes)
            .toString()
    }

    private fun decode(raw: String): PassProgress = runCatching {
        val root = JSONObject(raw)
        val summits = root.optJSONObject("summits")
        val targets = root.optJSONObject("targets")
        val notes = root.optJSONObject("notes")
        PassProgress(
            summits = summits?.keys()?.asSequence()?.associateWith { key ->
                val o = summits.getJSONObject(key)
                SummitEntry(
                    done = o.optBoolean("d"),
                    date = o.optString("dt"),
                    startPoint = o.optString("sp"),
                    route = o.optString("rt"),
                    distance = o.optString("di"),
                    duration = o.optString("tm"),
                    elevationGain = o.optString("eg"),
                    rating = o.optInt("ra"),
                    notes = o.optString("no"),
                )
            }?.toMap() ?: emptyMap(),
            targets = targets?.keys()?.asSequence()?.associateWith { key ->
                val o = targets.getJSONObject(key)
                TargetEntry(done = o.optBoolean("d"), date = o.optString("dt"))
            }?.toMap() ?: emptyMap(),
            regionNotes = notes?.keys()?.asSequence()?.associateWith { notes.optString(it) }
                ?.toMap() ?: emptyMap(),
        )
    }.getOrElse { PassProgress() }
}
