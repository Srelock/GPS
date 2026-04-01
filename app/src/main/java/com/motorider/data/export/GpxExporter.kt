package com.motorider.data.export

import com.motorider.data.entity.RouteEntity
import com.motorider.data.entity.RoutePoint
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports saved routes to GPX (GPS Exchange Format) files.
 * 
 * GPX is a widely-supported format that can be imported into:
 * - Google Earth
 * - Strava
 * - Garmin Connect
 * - Komoot
 * - Many other GPS applications
 */
object GpxExporter {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // ISO 8601 date format as required by GPX standard
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    /**
     * Export a route to GPX XML format.
     * 
     * @param route The saved route entity containing polyline data
     * @param outputStream Stream to write GPX content to (file, share intent, etc.)
     * @throws Exception if polyline JSON cannot be parsed
     */
    fun exportToGpx(route: RouteEntity, outputStream: OutputStream) {
        val points = json.decodeFromString<List<RoutePoint>>(route.polylineJson)
        
        val gpxContent = buildGpxContent(route, points)
        outputStream.write(gpxContent.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }
    
    /**
     * Build the GPX XML content string.
     */
    private fun buildGpxContent(route: RouteEntity, points: List<RoutePoint>): String {
        return buildString {
            // XML declaration and GPX root element
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="MotoRider Dashboard"
                |     xmlns="http://www.topografix.com/GPX/1/1"
                |     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                |     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""".trimMargin())
            
            // Metadata section
            appendLine("  <metadata>")
            appendLine("    <name>${escapeXml(route.name)}</name>")
            appendLine("    <desc>Recorded with MotoRider Dashboard</desc>")
            appendLine("    <time>${dateFormat.format(Date(route.startTime))}</time>")
            appendLine("  </metadata>")
            
            // Track element
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(route.name)}</name>")
            appendLine("    <type>motorcycle</type>")
            
            // Track extensions with statistics
            appendLine("    <extensions>")
            appendLine("      <motorider:stats xmlns:motorider=\"http://motorider.app/gpx/1\">")
            appendLine("        <motorider:distance>${route.totalDistanceMeters}</motorider:distance>")
            appendLine("        <motorider:maxSpeed>${route.maxSpeedKmh}</motorider:maxSpeed>")
            appendLine("        <motorider:avgSpeed>${route.avgSpeedKmh}</motorider:avgSpeed>")
            appendLine("        <motorider:duration>${route.durationMillis}</motorider:duration>")
            appendLine("      </motorider:stats>")
            appendLine("    </extensions>")
            
            // Track segment with all points
            appendLine("    <trkseg>")
            
            for (point in points) {
                append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
                
                // Add elevation if available
                point.altitude?.let { alt ->
                    append("<ele>${String.format(Locale.US, "%.1f", alt)}</ele>")
                }
                
                // Add timestamp
                append("<time>${dateFormat.format(Date(point.timestamp))}</time>")
                
                // Add speed as extension
                append("<extensions>")
                append("<speed>${String.format(Locale.US, "%.2f", point.speedKmh / 3.6)}</speed>") // m/s for GPX standard
                append("</extensions>")
                
                appendLine("</trkpt>")
            }
            
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }
    
    /**
     * Escape special XML characters to prevent parsing errors.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    /**
     * Generate a suggested filename for the GPX export.
     * 
     * @param route The route to generate filename for
     * @return Filename like "MotoRider_2024-01-15_MyRoute.gpx"
     */
    fun generateFilename(route: RouteEntity): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormatter.format(Date(route.startTime))
        val safeName = route.name
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(" ", "_")
            .take(30)
        return "MotoRider_${dateStr}_$safeName.gpx"
    }
    
    /**
     * Calculate the bounding box of a route.
     * Useful for displaying route on a map.
     */
    fun calculateBounds(points: List<RoutePoint>): RouteBounds? {
        if (points.isEmpty()) return null
        
        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE
        
        for (point in points) {
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }
        
        return RouteBounds(minLat, maxLat, minLon, maxLon)
    }
}

/**
 * Geographic bounding box for a route.
 */
data class RouteBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    val centerLatitude: Double get() = (minLatitude + maxLatitude) / 2
    val centerLongitude: Double get() = (minLongitude + maxLongitude) / 2
}
