package io.github.stephenjoy98.heatmap.review

import io.github.stephenjoy98.heatmap.data.Units
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import io.github.stephenjoy98.heatmap.cloud.FirebaseGate
import io.github.stephenjoy98.heatmap.data.AiEngine
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class InsightResult(val text: String, val engine: AiEngine, val note: String? = null)

/** Gemini Nano through ML Kit's Prompt API; runs entirely on the phone where supported. */
object OnDeviceAi {
    enum class Status { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

    private val model by lazy { Generation.getClient() }

    suspend fun status(): Status = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> Status.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> Status.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> Status.DOWNLOADING
            else -> Status.UNAVAILABLE
        }
    }.getOrDefault(Status.UNAVAILABLE)

    /** Emits human-readable progress; the last value is "done" or an error message. */
    fun download(): Flow<String> = model.download().map { s ->
        when (s) {
            is DownloadStatus.DownloadStarted -> "Starting download…"
            is DownloadStatus.DownloadProgress -> "Downloaded ${s.totalBytesDownloaded / (1024 * 1024)} MB…"
            is DownloadStatus.DownloadFailed -> "Download failed: ${s.e.message}"
            else -> "done"
        }
    }

    suspend fun generate(prompt: String): String {
        val response = model.generateContent(prompt)
        return response.candidates.firstOrNull()?.text?.trim().orEmpty()
    }
}

/** Writes Review insights with whichever engine the user picked, falling back to plain statistics. */
object Insights {
    suspend fun generate(context: Context, data: ReviewData, question: String?): InsightResult {
        val engine = Prefs.aiEngine.value ?: AiEngine.STATS
        val prompt = buildPrompt(data, question)
        return when (engine) {
            AiEngine.ON_DEVICE -> {
                val status = OnDeviceAi.status()
                if (status != OnDeviceAi.Status.AVAILABLE) {
                    statsFallback(data, question, "Gemini Nano isn't ready on this phone (${status.name.lowercase()}), so here are the plain statistics.")
                } else {
                    runCatching { OnDeviceAi.generate(prompt) }.fold(
                        onSuccess = { InsightResult(it.ifBlank { StatsInsights.build(data, question) }, AiEngine.ON_DEVICE) },
                        onFailure = { statsFallback(data, question, "Gemini Nano failed (${it.message}).") },
                    )
                }
            }
            AiEngine.CLOUD -> {
                if (!FirebaseGate.isConfigured(context)) {
                    statsFallback(data, question, "Cloud AI needs Firebase, which isn't set up in this build.")
                } else {
                    runCatching {
                        Firebase.ai(backend = GenerativeBackend.googleAI())
                            .generativeModel(Prefs.cloudModel.value)
                            .generateContent(prompt)
                            .text
                            .orEmpty()
                            .trim()
                    }.fold(
                        onSuccess = { InsightResult(it.ifBlank { StatsInsights.build(data, question) }, AiEngine.CLOUD) },
                        onFailure = { statsFallback(data, question, "Cloud AI failed (${it.message}).") },
                    )
                }
            }
            AiEngine.STATS -> InsightResult(StatsInsights.build(data, question), AiEngine.STATS)
        }
    }

    private fun statsFallback(data: ReviewData, question: String?, note: String) =
        InsightResult(StatsInsights.build(data, question), AiEngine.STATS, note)

    /** A compact fact sheet; small enough for Gemini Nano's context window. */
    fun facts(data: ReviewData): String = buildString {
        appendLine("Period: ${periodText(data.period)}")
        appendLine("Total distance: ${km(data.totalKm)} over ${data.trips} trips")
        for (mode in TravelMode.entries) {
            val v = data.kmByMode[mode] ?: continue
            if (v > 0.05) appendLine("- ${mode.label}: ${km(v)}")
        }
        appendLine("Areas visited (~220 m squares): ${data.areasVisited}, of which new: ${data.newAreas}")
        if (data.cities.isNotEmpty()) {
            appendLine("Cities (most time first): " + data.cities.take(12).joinToString { it.city + (it.country?.let { c -> " ($c)" } ?: "") })
        }
        if (data.countries.isNotEmpty()) appendLine("Countries: ${data.countries.joinToString()}")
        data.busiestDay?.let { appendLine("Longest travel day: ${it.first.format(DAY)} with ${km(it.second)}") }
        data.steps?.let { s ->
            appendLine("Steps: ${s.total} total, ${s.perDay} per day on average")
            s.bestDay?.let { appendLine("Best step day: ${it.format(DAY)} with ${s.bestDaySteps}") }
        }
        if (data.questions.isNotEmpty()) appendLine("Trips whose travel mode is still unconfirmed: ${data.questions.size}")
    }

    private fun buildPrompt(data: ReviewData, question: String?): String = buildString {
        appendLine("You are a friendly assistant inside a personal location-history app.")
        appendLine("Use only these facts about the user's travel. Don't invent places or numbers. Use ${Units.longUnitName}.")
        appendLine()
        append(facts(data))
        appendLine()
        if (question.isNullOrBlank()) {
            appendLine("Write 3 to 5 short, specific insights as bullet points (start each with '• '). Mention patterns, highlights and one suggestion for exploring somewhere new.")
        } else {
            appendLine("Answer the user's question in a few sentences: $question")
        }
    }

    /** A distance in kilometres, shown in the chosen units (km or miles). */
    internal fun km(v: Double) = Units.distance(v)

    internal fun periodText(p: ReviewPeriod) = when (p) {
        ReviewPeriod.WEEK -> "the last 7 days"
        ReviewPeriod.MONTH -> "the last 30 days"
        ReviewPeriod.YEAR -> "the last 12 months"
        ReviewPeriod.ALL -> "all time"
    }

    internal val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
}

/** Insights computed from the numbers alone; works on every phone, offline, with nothing sent anywhere. */
object StatsInsights {
    fun build(data: ReviewData, question: String?): String {
        val q = question?.lowercase()?.trim().orEmpty()
        if (q.isNotEmpty()) {
            answer(data, q)?.let { return it }
        }
        val lines = summary(data)
        return if (q.isNotEmpty()) {
            "Statistics mode can't answer free-form questions, but here's what the numbers say:\n" + lines.joinToString("\n")
        } else {
            lines.joinToString("\n")
        }
    }

    private fun summary(d: ReviewData): List<String> = buildList {
        if (d.points == 0L) {
            add("• Nothing recorded in ${Insights.periodText(d.period)} yet. Start recording to see insights here.")
            return@buildList
        }
        if (d.totalKm > 0) {
            val top = d.kmByMode.maxByOrNull { it.value }
            val share = top?.let { (it.value / d.totalKm * 100).roundToInt() }
            add("• You covered ${Insights.km(d.totalKm)} over ${d.trips} trips in ${Insights.periodText(d.period)}" +
                (if (top != null && share != null) ", ${share}% of it ${modeVerb(top.key)}." else "."))
        }
        val active = (d.kmByMode[TravelMode.WALK] ?: 0.0) + (d.kmByMode[TravelMode.CYCLE] ?: 0.0)
        if (active > 0) add("• ${Insights.km(active)} of that was under your own power (walking and cycling).")
        if (d.areasVisited > 0) {
            val pct = (d.newAreas * 100.0 / d.areasVisited).roundToInt()
            add("• You passed through ${d.areasVisited} map squares; ${d.newAreas} were new to you ($pct%).")
        }
        if (d.cities.isNotEmpty()) {
            val names = d.cities.take(5).joinToString { it.city }
            add("• ${d.cities.size} ${if (d.cities.size == 1) "city" else "cities"} visited: $names" +
                (if (d.cities.size > 5) " and more." else ".") + " You spent the most time in ${d.cities.first().city}.")
        }
        d.busiestDay?.let { add("• Your longest travel day was ${it.first.format(Insights.DAY)} at ${Insights.km(it.second)}.") }
        d.steps?.let { s ->
            if (s.total > 0) {
                add("• You averaged ${"%,d".format(s.perDay)} steps a day" +
                    (s.bestDay?.let { ", with a best of ${"%,d".format(s.bestDaySteps)} on ${it.format(Insights.DAY)}." } ?: "."))
            }
        }
        if (d.newAreas == 0L && d.areasVisited > 0) add("• Everything this period was familiar ground. Try a new route to light up more of the map.")
        if (d.questions.isNotEmpty()) add("• ${d.questions.size} trip(s) are waiting for you to confirm how you travelled.")
    }

    private fun answer(d: ReviewData, q: String): String? {
        val mode = TravelMode.choosable.firstOrNull { m ->
            when (m) {
                TravelMode.WALK -> "walk" in q
                TravelMode.CYCLE -> "cycl" in q || "bike" in q
                TravelMode.DRIVE -> "driv" in q || " car" in q
                TravelMode.RIDESHARE -> "share" in q || "uber" in q || "lyft" in q || "taxi" in q
                TravelMode.TRANSIT -> "transit" in q || "bus" in q || "train" in q || "metro" in q
                else -> false
            }
        }
        return when {
            mode != null -> "${mode.label}: ${Insights.km(d.kmByMode[mode] ?: 0.0)} in ${Insights.periodText(d.period)}."
            "step" in q -> d.steps?.let { "You took ${"%,d".format(it.total)} steps (${"%,d".format(it.perDay)} a day on average)." }
                ?: "Connect Health Connect on this tab to see your steps."
            "city" in q || "cities" in q || "where" in q ->
                if (d.cities.isEmpty()) "No cities identified yet for this period."
                else "You visited ${d.cities.size}: " + d.cities.joinToString { it.city } + "."
            "countr" in q -> if (d.countries.isEmpty()) "No countries identified yet." else "Countries: ${d.countries.joinToString()}."
            "new" in q || "explor" in q -> "${d.newAreas} new map squares out of ${d.areasVisited} visited."
            "far" in q || "km" in q || "mile" in q || "distance" in q || "kilomet" in q -> "You covered ${Insights.km(d.totalKm)} over ${d.trips} trips."
            else -> null
        }
    }

    private fun modeVerb(m: TravelMode) = when (m) {
        TravelMode.WALK -> "on foot"
        TravelMode.CYCLE -> "by bike"
        TravelMode.DRIVE -> "driving"
        TravelMode.RIDESHARE -> "in car shares"
        TravelMode.TRANSIT -> "on transit"
        TravelMode.UNKNOWN -> "unclassified"
    }
}
