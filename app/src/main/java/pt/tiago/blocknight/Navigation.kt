package pt.tiago.blocknight

/** Top-level screens. Each attempt has its own `attempt`: a new attempt is a new session, even on the same floor. */
sealed interface Screen {
    data object Home : Screen
    data class District(val id: Int) : Screen
    data object Free : Screen

    /** `resume` restores the saved free session; otherwise a fresh run replaces it. */
    data class FreeGame(val mode: String, val startLevel: Int, val attempt: Int, val resume: Boolean) : Screen
    data object Daily : Screen
    data class DailyGame(val ordinal: Int, val difficulty: String, val attempt: Int, val date: String) : Screen
    data class Game(val ordinal: Int, val difficulty: String, val attempt: Int) : Screen
    data object Stats : Screen
    data object Settings : Screen

    /** Matches never leave on Back: the match opens its pause instead (PLANO §3). */
    val isMatch: Boolean get() = this is Game || this is DailyGame || this is FreeGame
}

/**
 * Where the system Back goes from each screen. Null for the Home (the app closes) and for matches, which
 * handle Back themselves by pausing.
 */
fun backTarget(screen: Screen): Screen? = when (screen) {
    Screen.Home -> null
    is Screen.Game, is Screen.DailyGame, is Screen.FreeGame -> null
    is Screen.District, Screen.Free, Screen.Daily, Screen.Stats, Screen.Settings -> Screen.Home
}

/**
 * Text form of a [Screen] for `rememberSaveable`, so a rotation or a language change keeps the current
 * screen. Fields are separated by `|`; none of them can contain it.
 */
object ScreenCodec {
    fun encode(screen: Screen): String = when (screen) {
        Screen.Home -> "home"
        is Screen.District -> "district|${screen.id}"
        Screen.Free -> "free"
        is Screen.FreeGame -> "freegame|${screen.mode}|${screen.startLevel}|${screen.attempt}|${screen.resume}"
        Screen.Daily -> "daily"
        is Screen.DailyGame -> "dailygame|${screen.ordinal}|${screen.difficulty}|${screen.attempt}|${screen.date}"
        is Screen.Game -> "game|${screen.ordinal}|${screen.difficulty}|${screen.attempt}"
        Screen.Stats -> "stats"
        Screen.Settings -> "settings"
    }

    /** Unknown or damaged text falls back to the Home instead of crashing. */
    fun decode(text: String): Screen {
        val p = text.split('|')
        return runCatching {
            when (p[0]) {
                "home" -> Screen.Home
                "district" -> Screen.District(p[1].toInt())
                "free" -> Screen.Free
                "freegame" -> Screen.FreeGame(p[1], p[2].toInt(), p[3].toInt(), p[4].toBooleanStrict())
                "daily" -> Screen.Daily
                "dailygame" -> Screen.DailyGame(p[1].toInt(), p[2], p[3].toInt(), p[4])
                "game" -> Screen.Game(p[1].toInt(), p[2], p[3].toInt())
                "stats" -> Screen.Stats
                "settings" -> Screen.Settings
                else -> Screen.Home
            }
        }.getOrDefault(Screen.Home)
    }
}
