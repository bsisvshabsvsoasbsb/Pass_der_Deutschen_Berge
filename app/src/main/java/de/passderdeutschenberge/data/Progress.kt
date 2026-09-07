package de.passderdeutschenberge.data

/** Notizen und Messwerte zu einer bestiegenen Tour. */
data class SummitEntry(
    val done: Boolean = false,
    val date: String = "",
    val startPoint: String = "",
    val route: String = "",
    val distance: String = "",
    val duration: String = "",
    val elevationGain: String = "",
    /** 0 = nicht bewertet, sonst 1..5. */
    val rating: Int = 0,
    val notes: String = "",
) {
    val isEmpty: Boolean
        get() = !done && date.isEmpty() && startPoint.isEmpty() && route.isEmpty() &&
            distance.isEmpty() && duration.isEmpty() && elevationGain.isEmpty() &&
            rating == 0 && notes.isEmpty()
}

/** Abhaken eines Ziels mit Besuchsdatum. */
data class TargetEntry(
    val done: Boolean = false,
    val date: String = "",
) {
    val isEmpty: Boolean get() = !done && date.isEmpty()
}

data class PassProgress(
    val summits: Map<String, SummitEntry> = emptyMap(),
    val targets: Map<String, TargetEntry> = emptyMap(),
    val regionNotes: Map<String, String> = emptyMap(),
) {
    fun summit(id: String): SummitEntry = summits[id] ?: SummitEntry()
    fun target(id: String): TargetEntry = targets[id] ?: TargetEntry()
    fun regionNote(id: String): String = regionNotes[id] ?: ""

    val doneSummitCount: Int get() = summits.count { it.value.done }
    val doneTargetCount: Int get() = targets.count { it.value.done }

    fun isSummitDone(id: String): Boolean = summits[id]?.done == true
    fun isTargetDone(id: String): Boolean = targets[id]?.done == true
}

/** Fortschritt eines Gebiets, aufgeteilt nach Gipfeln und Zielen. */
data class AreaProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val isComplete: Boolean get() = total > 0 && done == total
    val isStarted: Boolean get() = done > 0
    val percent: Int get() = if (total == 0) 0 else (done * 100) / total

    operator fun plus(other: AreaProgress) = AreaProgress(done + other.done, total + other.total)
}

fun PassProgress.progressOf(region: Region, summits: List<Summit>): AreaProgress {
    val total = region.targets.size + summits.size
    val done = region.targets.count { isTargetDone(it.id) } + summits.count { isSummitDone(it.id) }
    return AreaProgress(done, total)
}
