package pt.tiago.blocknight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.progress.Backup
import pt.tiago.blocknight.core.progress.DailyResult
import pt.tiago.blocknight.core.progress.FloorResult
import pt.tiago.blocknight.core.progress.FreeReducer
import pt.tiago.blocknight.core.progress.FreeResult
import pt.tiago.blocknight.core.progress.Profile
import pt.tiago.blocknight.core.progress.ProgressReducer
import pt.tiago.blocknight.data.CampaignSession
import pt.tiago.blocknight.data.FreeSession
import pt.tiago.blocknight.data.ProfileLoad
import pt.tiago.blocknight.game.Feedback
import pt.tiago.blocknight.ui.daily.DailyScreen
import pt.tiago.blocknight.ui.district.DistrictScreen
import pt.tiago.blocknight.ui.free.FreeScreen
import pt.tiago.blocknight.ui.game.CampaignGame
import pt.tiago.blocknight.ui.game.FreeGame
import pt.tiago.blocknight.ui.home.HomeScreen
import pt.tiago.blocknight.ui.home.nextFloor
import pt.tiago.blocknight.ui.setup.FloorSetupSheet
import pt.tiago.blocknight.ui.settings.SettingsScreen
import pt.tiago.blocknight.ui.stats.StatsScreen
import pt.tiago.blocknight.ui.theme.BlockNightColors
import pt.tiago.blocknight.ui.theme.BlockNightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlockNightTheme {
                AppRoot(app = application as App)
            }
        }
    }
}

/** Keeps the current screen across rotation, language or font-size changes (they recreate the activity). */
private val ScreenSaver = Saver<Screen, String>(save = { ScreenCodec.encode(it) }, restore = { ScreenCodec.decode(it) })

@Composable
fun AppRoot(app: App) {
    val container = app.container
    val context = LocalContext.current
    val exportDone = stringResource(R.string.settings_export_done)
    val importFailed = stringResource(R.string.settings_import_failed)
    val importDone = stringResource(R.string.settings_import_done)
    val saveFailedText = stringResource(R.string.result_save_failed)
    val exportFailed = stringResource(R.string.settings_export_failed)
    val feedback = remember { Feedback(context) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    var load by remember { mutableStateOf<ProfileLoad?>(null) }
    var profile by remember { mutableStateOf(Profile()) }
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    var setupOrdinal by rememberSaveable { mutableStateOf<Int?>(null) }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    var campaignSave by remember { mutableStateOf<CampaignSession?>(null) }
    var settingsStatus by remember { mutableStateOf<String?>(null) }
    var importCandidate by remember { mutableStateOf<Profile?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            settingsStatus = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(profile).toByteArray(Charsets.UTF_8)) }
                exportDone
            }.getOrElse { exportFailed }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val parsed = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { readAtMost(it, Backup.MAX_BYTES + 1) } ?: error("empty file")
                Backup.parse(bytes.toString(Charsets.UTF_8)).getOrThrow()
            }
            parsed.onSuccess { importCandidate = it; settingsStatus = null }
                .onFailure { settingsStatus = importFailed }
        }
    }
    var freeSave by remember { mutableStateOf<FreeSession?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { container.profiles.load() }
        load = loaded
        if (loaded is ProfileLoad.Ok) profile = loaded.profile
        campaignSave = withContext(Dispatchers.IO) { container.campaign.read() }
        freeSave = withContext(Dispatchers.IO) { container.free.read() }
    }
    val loaded = load
    // Nothing is written before the profile is read, and an unreadable or future profile is never
    // overwritten silently (PLANO §10.1).
    val writable = loaded is ProfileLoad.Ok || loaded is ProfileLoad.Missing
    feedback.soundOn = profile.settings.sound
    feedback.vibrationOn = profile.settings.vibration
    val difficulty = profile.lastDifficulty

    fun persist(next: Profile): Boolean {
        if (!writable) return false
        return try {
            container.profiles.save(next)
            profile = next
            true
        } catch (e: Exception) {
            false
        }
    }

    fun commitFloor(result: FloorResult): Boolean {
        val saved = persist(ProgressReducer.apply(profile, result))
        if (saved) {
            container.campaign.clear()
            campaignSave = null
        }
        return saved
    }

    fun commitFree(result: FreeResult): Boolean {
        val saved = persist(FreeReducer.apply(profile, result))
        if (saved) {
            container.free.clear()
            freeSave = null
        }
        return saved
    }

    fun commitDaily(result: DailyResult): Boolean {
        val saved = persist(FreeReducer.applyDaily(profile, result))
        if (saved) container.daily.clear()
        return saved
    }

    /** The chosen difficulty is kept in the profile; if it cannot be written it still applies in memory. */
    fun setDifficulty(difficulty: String) {
        val next = profile.copy(lastDifficulty = difficulty)
        if (!persist(next)) profile = next
    }

    // Sub-screens go back to the Home; matches handle Back themselves (pause), the Home closes the app.
    BackHandler(enabled = backTarget(screen) != null) { backTarget(screen)?.let { screen = it } }

    if (loaded == null) {
        // Until the profile is read, show only the background: no flash of a first-run Home, no early taps.
        Box(modifier = Modifier.fillMaxSize().background(BlockNightColors.Background))
        return
    }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            content = container.content,
            profile = profile,
            difficulty = difficulty,
            campaignSave = campaignSave?.takeIf { !it.daily && container.content.isAvailable(it.ordinal) },
            freeSave = freeSave,
            totalFloors = container.content.playableCount,
            today = LocalDate.now(),
            profileUnreadable = loaded is ProfileLoad.Unreadable || loaded is ProfileLoad.FutureVersion,
            onStart = { setupOrdinal = it },
            onContinue = { save ->
                attempt++
                screen = Screen.Game(save.ordinal, save.difficulty, attempt)
            },
            onDistrict = { screen = Screen.District(Districts.districtOf(nextFloor(profile, difficulty, container.content.playableCount)).id) },
            onFree = { screen = Screen.Free },
            onDaily = { screen = Screen.Daily },
            onSettings = { screen = Screen.Settings },
            onStats = { screen = Screen.Stats },
        )
        is Screen.District -> DistrictScreen(
            content = container.content,
            profile = profile,
            districtId = current.id,
            difficulty = difficulty,
            onDifficulty = { setDifficulty(it) },
            onDistrict = { screen = Screen.District(it) },
            onFloor = { setupOrdinal = it },
            onBack = { screen = Screen.Home },
        )
        Screen.Free -> FreeScreen(
            profile = profile,
            saved = freeSave,
            onPlay = { mode, level, resume ->
                attempt++
                screen = Screen.FreeGame(mode, level, attempt, resume)
            },
            onBack = { screen = Screen.Home },
        )
        is Screen.FreeGame -> FreeGame(
            app = app,
            feedback = feedback,
            mode = current.mode,
            startLevel = current.startLevel,
            attempt = current.attempt,
            resume = current.resume,
            settings = profile.settings,
            onFinished = { commitFree(it) },
            onExit = {
                freeSave = container.free.read()
                screen = Screen.Free
            },
            onRetry = {
                freeSave = null
                attempt++
                screen = Screen.FreeGame(current.mode, current.startLevel, attempt, resume = false)
            },
        )
        Screen.Daily -> DailyScreen(
            content = container.content,
            profile = profile,
            today = LocalDate.now(),
            difficulty = difficulty,
            onDifficulty = { setDifficulty(it) },
            onPlay = { ordinal, diff ->
                attempt++
                screen = Screen.DailyGame(ordinal, diff, attempt, LocalDate.now().toString())
            },
            onBack = { screen = Screen.Home },
        )
        is Screen.DailyGame -> CampaignGame(
            app = app,
            feedback = feedback,
            ordinal = current.ordinal,
            difficulty = current.difficulty,
            attempt = current.attempt,
            daily = true,
            date = current.date,
            settings = profile.settings,
            onDailyFinished = { commitDaily(it) },
            onExit = { screen = Screen.Daily },
            onNext = { screen = Screen.Daily },
            onRetry = {
                attempt++
                screen = Screen.DailyGame(current.ordinal, current.difficulty, attempt, current.date)
            },
        )
        is Screen.Game -> CampaignGame(
            app = app,
            feedback = feedback,
            ordinal = current.ordinal,
            difficulty = current.difficulty,
            attempt = current.attempt,
            onFinished = { commitFloor(it) },
            settings = profile.settings,
            onExit = {
                campaignSave = container.campaign.read()
                screen = Screen.District(Districts.districtOf(current.ordinal).id)
            },
            onNext = {
                campaignSave = null
                attempt++
                screen = if (current.ordinal < container.content.playableCount) {
                    Screen.Game(current.ordinal + 1, current.difficulty, attempt)
                } else {
                    Screen.District(Districts.districtOf(current.ordinal).id)
                }
            },
            onRetry = {
                campaignSave = null
                attempt++
                screen = Screen.Game(current.ordinal, current.difficulty, attempt)
            },
        )
        Screen.Stats -> StatsScreen(profile = profile, totalFloors = Districts.TOTAL_FLOORS, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(
            settings = profile.settings,
            onChange = { persist(profile.copy(settings = it)) },
            status = settingsStatus,
            importPreview = importCandidate?.let { candidate ->
                candidate.progress.values.sumOf { byFloor -> byFloor.values.count { it.completed } }
            },
            onExport = { exportLauncher.launch("blocknight-progress.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onConfirmImport = {
                val candidate = importCandidate ?: return@SettingsScreen
                importCandidate = null
                runCatching { container.profiles.backupCurrent() }
                settingsStatus = if (persist(candidate)) importDone else saveFailedText
            },
            onCancelImport = { importCandidate = null },
            onBack = { screen = Screen.Home },
        )
    }

    setupOrdinal?.let { ordinal ->
        FloorSetupSheet(
            content = container.content,
            ordinal = ordinal,
            districtName = Districts.districtOf(ordinal).name,
            difficulty = difficulty,
            record = profile.record(difficulty, Districts.floorId(ordinal)),
            onPlay = {
                setupOrdinal = null
                attempt++
                screen = Screen.Game(ordinal, difficulty, attempt)
            },
            onDismiss = { setupOrdinal = null },
        )
    }
}

/** Lê no máximo `limit` bytes (até um pouco mais, para o validador recusar ficheiros grandes). API 26. */
private fun readAtMost(stream: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() <= limit) {
        val n = stream.read(buffer)
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
