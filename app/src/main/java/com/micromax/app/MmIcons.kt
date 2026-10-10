package com.micromax.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** MICRO-MAX line icon set: 24x24 grid, 1.8 stroke, round caps and joins. Generated from icons.py. */
object MmIcons {
    private fun line(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (p in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Home: ImageVector by lazy { line("Home", "M7 3.5h10A3.5 3.5 0 0 1 20.5 7v10a3.5 3.5 0 0 1-3.5 3.5H7A3.5 3.5 0 0 1 3.5 17V7A3.5 3.5 0 0 1 7 3.5z", "M3.5 10h17", "M12 10v10.5") }
    val Router: ImageVector by lazy { line("Router", "M5.5 12.5h13a2.5 2.5 0 0 1 2.5 2.5v1.5a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 16.5V15a2.5 2.5 0 0 1 2.5-2.5z", "M7 12.5V6", "M17 12.5V6", "M7.5 15.75h.01", "M11 15.75h.01") }
    val Ticket: ImageVector by lazy { line("Ticket", "M4 6.5h16V10a2 2 0 0 0 0 4v3.5H4V14a2 2 0 0 0 0-4z", "M15 7.5v1", "M15 11.5v1", "M15 15.5v1", "M7.5 10.5h3.5", "M7.5 13.5h2") }
    val Store: ImageVector by lazy { line("Store", "M4.5 4h15l1.5 5.2a2.6 2.6 0 0 1-4.6 1.4 2.6 2.6 0 0 1-4.4 0 2.6 2.6 0 0 1-4.4 0A2.6 2.6 0 0 1 3 9.2z", "M5 12.5V20h14v-7.5", "M10 20v-4.5h4V20") }
    val Apps: ImageVector by lazy { line("Apps", "M5 5h4.5v4.5H5z", "M14.5 5H19v4.5h-4.5z", "M5 14.5h4.5V19H5z", "M14.5 14.5H19V19h-4.5z") }
    val Add: ImageVector by lazy { line("Add", "M12 5v14", "M5 12h14") }
    val Gauge: ImageVector by lazy { line("Gauge", "M4.3 17a8.5 8.5 0 1 1 15.4 0", "M12 13.5l3.6-4.6", "M12 13.5h.01") }
    val Palette: ImageVector by lazy { line("Palette", "M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 2-.8 2-1.8 0-.5-.2-.9-.5-1.3-.3-.4-.5-.8-.5-1.3 0-1 .8-1.8 1.8-1.8H17a3.9 3.9 0 0 0 3.5-3.9A8.2 8.2 0 0 0 12 3.5z", "M7.5 11.5h.01", "M10 7.8h.01", "M14.5 7.8h.01") }
    val Sparkle: ImageVector by lazy { line("Sparkle", "M11 4l1.8 4.9L17.5 10.5l-4.7 1.7L11 17l-1.8-4.8L4.5 10.5l4.7-1.6z", "M18 15l.7 1.8 1.8.7-1.8.7L18 20l-.7-1.8-1.8-.7 1.8-.7z") }
    val Terminal: ImageVector by lazy { line("Terminal", "M5 4.5h14A2 2 0 0 1 21 6.5v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z", "M7.5 9.5l3 2.5-3 2.5", "M13 15h3.5") }
    val Chart: ImageVector by lazy { line("Chart", "M4 20h16", "M7.5 20v-7", "M12 20V6", "M16.5 20v-10") }
    val Network: ImageVector by lazy { line("Network", "M9.5 3.5h5v4h-5z", "M3 15.5h4v4H3z", "M10 15.5h4v4h-4z", "M17 15.5h4v4h-4z", "M12 7.5v8", "M5 15.5v-3.5h14v3.5") }
    val Wifi: ImageVector by lazy { line("Wifi", "M3.5 9.3a12.2 12.2 0 0 1 17 0", "M6.5 12.5a7.8 7.8 0 0 1 11 0", "M9.4 15.6a3.6 3.6 0 0 1 5.2 0", "M12 19h.01") }
    val Users: ImageVector by lazy { line("Users", "M9 11a3.4 3.4 0 1 0 0-6.8A3.4 3.4 0 0 0 9 11z", "M2.8 19.8a6.2 6.2 0 0 1 12.4 0", "M15.8 4.6a3.4 3.4 0 0 1 0 6.2", "M18.2 14.2a6.2 6.2 0 0 1 3 5.6") }
    val Person: ImageVector by lazy { line("Person", "M12 11.5a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2z", "M5 20a7 7 0 0 1 14 0") }
    val Sliders: ImageVector by lazy { line("Sliders", "M4 8h8", "M16 8h4", "M4 16h4", "M12 16h8", "M12 8a2 2 0 1 0 4 0 2 2 0 1 0-4 0", "M8 16a2 2 0 1 0 4 0 2 2 0 1 0-4 0") }
    val Shield: ImageVector by lazy { line("Shield", "M12 3.2l7.2 2.8v5.4c0 4.4-3 7.7-7.2 9.4-4.2-1.7-7.2-5-7.2-9.4V6z", "M8.8 12l2.3 2.3 4.2-4.4") }
    val Sync: ImageVector by lazy { line("Sync", "M19.8 11.2a8 8 0 0 0-14.3-3.6", "M4.5 4.8v3.4h3.4", "M4.2 12.8a8 8 0 0 0 14.3 3.6", "M19.5 19.2v-3.4h-3.4") }
    val Print: ImageVector by lazy { line("Print", "M7.5 9V4h9v5", "M7.5 17H5.5a2 2 0 0 1-2-2v-3.5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2V15a2 2 0 0 1-2 2h-2", "M7.5 14h9v6.5h-9z") }
    val Scan: ImageVector by lazy { line("Scan", "M4 9V6.5A2.5 2.5 0 0 1 6.5 4H9", "M15 4h2.5A2.5 2.5 0 0 1 20 6.5V9", "M20 15v2.5a2.5 2.5 0 0 1-2.5 2.5H15", "M9 20H6.5A2.5 2.5 0 0 1 4 17.5V15", "M9 9h2v2H9z", "M13 9h2v2h-2z", "M9 13h2v2H9z", "M13.5 14h1.5") }
    val Chip: ImageVector by lazy { line("Chip", "M8 8h8v8H8z", "M10.5 3.5V6", "M13.5 3.5V6", "M10.5 18v2.5", "M13.5 18v2.5", "M3.5 10.5H6", "M3.5 13.5H6", "M18 10.5h2.5", "M18 13.5h2.5") }
    val Traffic: ImageVector by lazy { line("Traffic", "M7.5 17V5.5", "M4 9l3.5-3.5L11 9", "M16.5 7v11.5", "M13 15l3.5 3.5L20 15") }
    val ChevronL: ImageVector by lazy { line("ChevronL", "M14.5 6l-6 6 6 6") }
    val ChevronR: ImageVector by lazy { line("ChevronR", "M9.5 6l6 6-6 6") }
    val Check: ImageVector by lazy { line("Check", "M5 12.5l4.5 4.5L19 7.5") }
    val Close: ImageVector by lazy { line("Close", "M6 6l12 12", "M18 6L6 18") }
    val Logout: ImageVector by lazy { line("Logout", "M10 4.5H6.5A2 2 0 0 0 4.5 6.5v11a2 2 0 0 0 2 2H10", "M14.5 8l4 4-4 4", "M18.5 12H9.5") }
    val Moon: ImageVector by lazy { line("Moon", "M19.5 14.3A8 8 0 0 1 9.7 4.5a8 8 0 1 0 9.8 9.8z") }
    val Lock: ImageVector by lazy { line("Lock", "M6.5 10.5h11a1.5 1.5 0 0 1 1.5 1.5v6.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 18.5V12a1.5 1.5 0 0 1 1.5-1.5z", "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5", "M12 14.5v2") }
    val Mail: ImageVector by lazy { line("Mail", "M5 5.5h14A1.5 1.5 0 0 1 20.5 7v10a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 17V7A1.5 1.5 0 0 1 5 5.5z", "M4 7.5l8 6 8-6") }
    val Eye: ImageVector by lazy { line("Eye", "M2.8 12S6 5.8 12 5.8 21.2 12 21.2 12 18 18.2 12 18.2 2.8 12 2.8 12z", "M12 14.6a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z") }
    val EyeOff: ImageVector by lazy { line("EyeOff", "M4 4.5l16 15", "M9.7 6.2A9.4 9.4 0 0 1 12 5.8c6 0 9.2 6.2 9.2 6.2a15.6 15.6 0 0 1-2.9 3.6", "M6.3 8.2A15.5 15.5 0 0 0 2.8 12S6 18.2 12 18.2a9 9 0 0 0 3.6-.8", "M10 10.2a2.6 2.6 0 0 0 3.6 3.6") }
    val Trash: ImageVector by lazy { line("Trash", "M4.5 7h15", "M9.5 7V4.8h5V7", "M6.5 7l.8 12.2a1.5 1.5 0 0 0 1.5 1.3h6.4a1.5 1.5 0 0 0 1.5-1.3L17.5 7", "M10 11v6", "M14 11v6") }
    val Cloud: ImageVector by lazy { line("Cloud", "M7.5 18.5a4.2 4.2 0 0 1-.6-8.4 5.6 5.6 0 0 1 10.8 1.4A3.5 3.5 0 0 1 17 18.5z") }
    val Download: ImageVector by lazy { line("Download", "M12 4v11", "M7.5 10.8L12 15.3l4.5-4.5", "M5 19.5h14") }
    val Copy: ImageVector by lazy { line("Copy", "M9 9h10a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 21H9a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 9 9z", "M15.5 9V5.5A1.5 1.5 0 0 0 14 4H5.5A1.5 1.5 0 0 0 4 5.5V14a1.5 1.5 0 0 0 1.5 1.5H7.5") }
    val Code: ImageVector by lazy { line("Code", "M8.5 7.5L4 12l4.5 4.5", "M15.5 7.5L20 12l-4.5 4.5", "M13.5 5.5l-3 13") }
    val Money: ImageVector by lazy { line("Money", "M3.5 7h17v10h-17z", "M12 14.6a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z", "M6.5 10.2h.01", "M17.5 13.8h.01") }
    val Timer: ImageVector by lazy { line("Timer", "M12 20.5a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15z", "M12 9v3.6l2.2 1.4", "M9.5 2.8h5") }
    val Link: ImageVector by lazy { line("Link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1") }
    val Tag: ImageVector by lazy { line("Tag", "M3.8 12.2V5.5a1.7 1.7 0 0 1 1.7-1.7h6.7l8 8a1.7 1.7 0 0 1 0 2.4l-6.5 6.5a1.7 1.7 0 0 1-2.4 0z", "M8 8h.01") }
    val Layers: ImageVector by lazy { line("Layers", "M12 4l8.5 4.5L12 13 3.5 8.5z", "M3.5 12.5L12 17l8.5-4.5", "M3.5 16.5L12 21l8.5-4.5") }
    val Web: ImageVector by lazy { line("Web", "M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z", "M3.5 12h17", "M12 3.5c2.4 2.4 3.4 5.2 3.4 8.5s-1 6.1-3.4 8.5c-2.4-2.4-3.4-5.2-3.4-8.5s1-6.1 3.4-8.5z") }
}
