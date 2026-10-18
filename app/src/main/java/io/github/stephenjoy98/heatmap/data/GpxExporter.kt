package io.github.stephenjoy98.heatmap.data

import java.io.OutputStream
import java.time.Instant

/** Writes all points as GPX, one track segment per continuous route. */
object GpxExporter {
    fun export(store: PointStore, out: OutputStream) {
        out.bufferedWriter().use { w ->
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            w.write("<gpx version=\"1.1\" creator=\"Location Ticker\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            w.write("<trk><name>Location Ticker</name>\n")
            var prev: LocationPoint? = null
            store.forEachPoint { p ->
                val last = prev
                if (last == null || TrackRules.isBreak(last.time, last.lat, last.lon, p.time, p.lat, p.lon, p.intervalMs)) {
                    if (last != null) w.write("</trkseg>\n")
                    w.write("<trkseg>\n")
                }
                w.write("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><time>${Instant.ofEpochMilli(p.time)}</time></trkpt>\n")
                prev = p
            }
            if (prev != null) w.write("</trkseg>\n")
            w.write("</trk>\n</gpx>\n")
        }
    }
}
