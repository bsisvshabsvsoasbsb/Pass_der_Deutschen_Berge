package de.passderdeutschenberge.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aus- und Einlesen des Fortschritts als JSON-Datei.
 *
 * Zweck ist der Umzug zwischen App-Staenden: bei einem Signaturwechsel (Debug
 * auf Release) oder einem Geraetewechsel muss die App deinstalliert werden und
 * der lokale Speicher geht verloren.
 *
 * Jeder Eintrag traegt zwei Schluessel: die technische ID und einen fachlichen
 * Ersatzschluessel aus Regions- und Objektnamen. Die IDs enthalten die
 * Seitenzahl aus dem Quell-PDF und koennen sich bei einer neuen Fassung
 * verschieben - der Ersatzschluessel faengt das ab.
 */
object ProgressTransfer {

    const val FORMAT = "de.passderdeutschenberge.progress"
    const val VERSION = 1

    /** Ergebnis eines Imports; unaufgeloeste Eintraege werden benannt, nicht verschluckt. */
    data class ImportReport(
        val summits: Int,
        val targets: Int,
        val notes: Int,
        val unresolved: Int,
    ) {
        val total: Int get() = summits + targets + notes
    }

    /** Fehlergrund als Code - die Oberflaeche uebersetzt ihn. */
    enum class Reason { INVALID_JSON, WRONG_FORMAT, NEWER_VERSION, IO }

    class TransferException(
        val reason: Reason,
        val detail: String = "",
    ) : Exception("$reason $detail")

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        return "pass-fortschritt-$stamp.json"
    }

    // --- Export ---------------------------------------------------------

    fun export(progress: PassProgress, catalog: PassCatalog, appVersion: String): String {
        val summits = JSONArray()
        progress.summits.forEach { (id, entry) ->
            val summit = catalog.summitById[id]
            summits.put(
                JSONObject()
                    .put("id", id)
                    .put("key", summit?.let { catalog.keyOfSummit(it) } ?: JSONObject.NULL)
                    .put("name", summit?.displayName ?: JSONObject.NULL)
                    .put("done", entry.done)
                    .put("date", entry.date)
                    .put("startPoint", entry.startPoint)
                    .put("route", entry.route)
                    .put("distance", entry.distance)
                    .put("duration", entry.duration)
                    .put("elevationGain", entry.elevationGain)
                    .put("rating", entry.rating)
                    .put("notes", entry.notes),
            )
        }
        val targets = JSONArray()
        progress.targets.forEach { (id, entry) ->
            targets.put(
                JSONObject()
                    .put("id", id)
                    .put("key", catalog.keyOfTarget(id) ?: JSONObject.NULL)
                    .put("name", catalog.targetById[id]?.name ?: JSONObject.NULL)
                    .put("done", entry.done)
                    .put("date", entry.date),
            )
        }
        val notes = JSONArray()
        progress.regionNotes.forEach { (id, text) ->
            notes.put(
                JSONObject()
                    .put("id", id)
                    .put("key", catalog.regionById[id]?.slug ?: JSONObject.NULL)
                    .put("text", text),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(Date()))
            .put("appVersion", appVersion)
            .put("summits", summits)
            .put("targets", targets)
            .put("regionNotes", notes)
            .toString(2)
    }

    // --- Import ---------------------------------------------------------

    /**
     * @param replace true ersetzt den bisherigen Stand, false fuehrt zusammen
     *                (importierte Werte gewinnen bei Konflikt).
     */
    fun import(
        json: String,
        catalog: PassCatalog,
        current: PassProgress,
        replace: Boolean,
    ): Pair<PassProgress, ImportReport> {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw TransferException(Reason.INVALID_JSON) }

        val format = root.optString("format")
        if (format != FORMAT) {
            throw TransferException(Reason.WRONG_FORMAT, format)
        }
        val version = root.optInt("version", 0)
        if (version > VERSION) {
            throw TransferException(Reason.NEWER_VERSION, version.toString())
        }

        var unresolved = 0
        val summits = LinkedHashMap<String, SummitEntry>()
        root.optJSONArray("summits")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = resolveSummitId(o, catalog)
                if (id == null) {
                    unresolved++
                    continue
                }
                summits[id] = SummitEntry(
                    done = o.optBoolean("done"),
                    date = o.optString("date"),
                    startPoint = o.optString("startPoint"),
                    route = o.optString("route"),
                    distance = o.optString("distance"),
                    duration = o.optString("duration"),
                    elevationGain = o.optString("elevationGain"),
                    rating = o.optInt("rating"),
                    notes = o.optString("notes"),
                )
            }
        }
        val targets = LinkedHashMap<String, TargetEntry>()
        root.optJSONArray("targets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = resolveTargetId(o, catalog)
                if (id == null) {
                    unresolved++
                    continue
                }
                targets[id] = TargetEntry(done = o.optBoolean("done"), date = o.optString("date"))
            }
        }
        val notes = LinkedHashMap<String, String>()
        root.optJSONArray("regionNotes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = resolveRegionId(o, catalog)
                val text = o.optString("text")
                if (id == null) {
                    unresolved++
                    continue
                }
                if (text.isNotBlank()) notes[id] = text
            }
        }

        val merged = if (replace) {
            PassProgress(summits = summits, targets = targets, regionNotes = notes)
        } else {
            PassProgress(
                summits = current.summits + summits,
                targets = current.targets + targets,
                regionNotes = current.regionNotes + notes,
            )
        }
        return merged to ImportReport(summits.size, targets.size, notes.size, unresolved)
    }

    /** Erst die technische ID, dann der fachliche Ersatzschluessel. */
    private fun resolveSummitId(o: JSONObject, catalog: PassCatalog): String? {
        val id = o.optString("id").takeIf { it.isNotEmpty() }
        if (id != null && catalog.summitById.containsKey(id)) return id
        val key = o.optString("key").takeIf { it.isNotEmpty() && it != "null" }
        return key?.let { catalog.summitByKey[it]?.id }
    }

    private fun resolveTargetId(o: JSONObject, catalog: PassCatalog): String? {
        val id = o.optString("id").takeIf { it.isNotEmpty() }
        if (id != null && catalog.targetById.containsKey(id)) return id
        val key = o.optString("key").takeIf { it.isNotEmpty() && it != "null" }
        return key?.let { catalog.targetByKey[it]?.id }
    }

    private fun resolveRegionId(o: JSONObject, catalog: PassCatalog): String? {
        val id = o.optString("id").takeIf { it.isNotEmpty() }
        if (id != null && catalog.regionById.containsKey(id)) return id
        val key = o.optString("key").takeIf { it.isNotEmpty() && it != "null" }
        return key?.let { catalog.regionBySlug[it]?.id }
    }
}
