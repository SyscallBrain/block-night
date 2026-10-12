package pt.tiago.blocknight.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import pt.tiago.blocknight.R
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.progress.ModeKeys
import pt.tiago.blocknight.core.progress.Profile
import pt.tiago.blocknight.data.CampaignSession
import pt.tiago.blocknight.data.ContentRepository
import pt.tiago.blocknight.data.FreeSession
import pt.tiago.blocknight.ui.components.PrimaryButton
import pt.tiago.blocknight.ui.components.ProgressBar
import pt.tiago.blocknight.ui.components.Skyline
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.components.difficultyName
import pt.tiago.blocknight.ui.district.objectiveText
import pt.tiago.blocknight.ui.theme.BlockNightColors

/**
 * Home (PLANO §3.1, DESIGN §5). Brand with the district tower beside it, the district card, the main button,
 * full-width rows for the daily challenge and free play, and a two-column grid for settings and statistics.
 * The skyline fills the bottom ~35 % behind the cards.
 */
@Composable
fun HomeScreen(
    content: ContentRepository,
    profile: Profile,
    difficulty: String,
    campaignSave: CampaignSession?,
    freeSave: FreeSession?,
    totalFloors: Int,
    today: LocalDate,
    onStart: (Int) -> Unit,
    onContinue: (CampaignSession) -> Unit,
    onDistrict: () -> Unit,
    onFree: () -> Unit,
    onDaily: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    profileUnreadable: Boolean = false,
) {
    val nextOrdinal = nextFloor(profile, difficulty, totalFloors)
    val district = Districts.districtOf(nextOrdinal.coerceIn(1, Districts.TOTAL_FLOORS))
    val completed = (1..totalFloors).count { ordinal -> profile.record(difficulty, Districts.floorId(ordinal)).completed }
    val firstOfDistrict = (district.id - 1) * Districts.FLOORS_PER_DISTRICT + 1
    val districtFloors = firstOfDistrict until firstOfDistrict + Districts.FLOORS_PER_DISTRICT
    val doneFlags = districtFloors.map { profile.record(difficulty, Districts.floorId(it)).completed }
    val districtDone = doneFlags.count { it }
    val stars = districtFloors.sumOf { profile.record(difficulty, Districts.floorId(it)).stars }
    val allDone = completed >= totalFloors
    val todays = profile.daily.filter { it.date == today.toString() && it.difficulty == difficulty }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BlockNightColors.Background).safeDrawingPadding()) {
        Skyline(
            seed = 42 + district.id,
            height = maxHeight * 0.35f,
            litFraction = (districtDone / Districts.FLOORS_PER_DISTRICT.toFloat() + 0.1f).coerceAtMost(1f),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Brand(modifier = Modifier.weight(1f))
                DistrictTower(
                    done = doneFlags,
                    current = if (allDone) -1 else nextOrdinal - firstOfDistrict,
                    description = stringResource(R.string.cd_tower, district.name, districtDone, Districts.FLOORS_PER_DISTRICT),
                    onClick = onDistrict,
                )
            }
            if (profileUnreadable) {
                Text(
                    text = stringResource(R.string.home_profile_unreadable),
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Pink),
                )
            }
            DistrictCard(
                eyebrow = stringResource(R.string.district_eyebrow, district.id, Districts.COUNT),
                name = district.name,
                progress = stringResource(
                    R.string.district_progress,
                    nextOrdinal - (district.id - 1) * Districts.FLOORS_PER_DISTRICT,
                    Districts.FLOORS_PER_DISTRICT,
                    stars,
                    Districts.FLOORS_PER_DISTRICT * 3,
                ),
                totals = stringResource(R.string.district_totals, completed, totalFloors) + " · " + difficultyName(difficulty),
                // The bar follows the district, like the line above it; the city total stays in the text below.
                fraction = districtDone / Districts.FLOORS_PER_DISTRICT.toFloat(),
                onClick = onDistrict,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (allDone) {
                    PrimaryButton(text = stringResource(R.string.campaign_done), onClick = onDistrict)
                } else {
                    val ordinal = campaignSave?.ordinal ?: nextOrdinal
                    val label = if (campaignSave != null) {
                        stringResource(R.string.continue_floor, ordinal)
                    } else {
                        stringResource(R.string.climb_floor, ordinal)
                    }
                    // A saved match resumes directly, with its own difficulty (never a fresh start over it).
                    PrimaryButton(text = label, onClick = { if (campaignSave != null) onContinue(campaignSave) else onStart(ordinal) })
                    Text(
                        // What the floor asks for, so the button and its caption never say the same thing twice.
                        text = if (campaignSave != null) {
                            stringResource(
                                R.string.saved_match,
                                objectiveText(content, campaignSave.ordinal, campaignSave.difficulty) + " · " + difficultyName(campaignSave.difficulty),
                            )
                        } else {
                            objectiveText(content, nextOrdinal, difficulty) + " · " + difficultyName(difficulty)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 12.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            MenuRow(
                title = stringResource(R.string.home_daily),
                caption = when {
                    todays.any { it.won } -> stringResource(R.string.home_daily_won)
                    todays.isNotEmpty() -> stringResource(R.string.home_daily_lost)
                    else -> stringResource(R.string.daily_caption)
                },
                highlight = todays.none { it.won },
                onClick = onDaily,
            )
            MenuRow(
                title = stringResource(R.string.home_free),
                caption = freeSave?.let { stringResource(R.string.home_free_resume, modeName(it.mode)) }
                    ?: stringResource(R.string.free_caption),
                highlight = freeSave != null,
                onClick = onFree,
            )
            // Same height for both tiles, whatever the length of their captions.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                MenuTile(stringResource(R.string.home_settings), stringResource(R.string.settings_caption), Modifier.weight(1f).fillMaxHeight(), onSettings)
                MenuTile(stringResource(R.string.home_stats), stringResource(R.string.statistics_caption), Modifier.weight(1f).fillMaxHeight(), onStats)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** First floor not yet cleared in this difficulty; the last one when the campaign is done. */
fun nextFloor(profile: Profile, difficulty: String, totalFloors: Int): Int {
    for (ordinal in 1..totalFloors) {
        if (!profile.record(difficulty, Districts.floorId(ordinal)).completed) return ordinal
    }
    return totalFloors
}

@Composable
private fun modeName(mode: String): String = when (mode) {
    "marathon" -> stringResource(R.string.free_marathon)
    ModeKeys.SPRINT -> stringResource(R.string.free_sprint)
    ModeKeys.ULTRA -> stringResource(R.string.free_ultra)
    else -> stringResource(R.string.free_zen)
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.brand_block))
                append("\n")
                withStyle(SpanStyle(color = BlockNightColors.Accent)) { append(stringResource(R.string.brand_night)) }
            },
            style = MaterialTheme.typography.displayLarge.copy(color = BlockNightColors.TextStrong),
        )
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The current district as one lit tower: 50 windows in 10 floors of 5, read from the bottom. Cleared floors
 * glow at 55 %, the current one is accent with a halo, the rest are dark (DESIGN §5 mini-tower, enlarged).
 */
@Composable
private fun DistrictTower(done: List<Boolean>, current: Int, description: String, onClick: () -> Unit) {
    Canvas(
        modifier = Modifier
            .size(width = 96.dp, height = 132.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val unit = 1.dp.toPx()
        val cols = 5
        val rows = done.size / cols
        val bodyTop = 14 * unit
        val bodyLeft = 10 * unit
        val bodyWidth = size.width - 2 * bodyLeft
        // Roof and antenna.
        drawRect(BlockNightColors.Line, topLeft = Offset(size.width / 2 - unit, 0f), size = Size(2 * unit, bodyTop - 4 * unit))
        drawCircle(BlockNightColors.Pink, radius = 2.5f * unit, center = Offset(size.width / 2, 2.5f * unit))
        drawRect(BlockNightColors.BuildingB, topLeft = Offset(bodyLeft + 8 * unit, bodyTop - 5 * unit), size = Size(bodyWidth - 16 * unit, 5 * unit))
        drawRoundRect(
            BlockNightColors.BuildingB,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, size.height - bodyTop),
            cornerRadius = CornerRadius(3 * unit),
        )
        val pad = 7 * unit
        val gap = 3 * unit
        val cellW = (bodyWidth - 2 * pad - gap * (cols - 1)) / cols
        val cellH = (size.height - bodyTop - 2 * pad - gap * (rows - 1)) / rows
        for (i in done.indices) {
            val col = i % cols
            val row = i / cols
            val left = bodyLeft + pad + col * (cellW + gap)
            val top = size.height - pad - (row + 1) * cellH - row * gap
            val color = when {
                i == current -> lerp(BlockNightColors.Accent, Color.White, 0.3f)
                done[i] -> lerp(BlockNightColors.Surface, BlockNightColors.Accent, 0.8f)
                else -> BlockNightColors.SurfaceRaised
            }
            if (i == current) {
                drawRoundRect(
                    BlockNightColors.Accent.copy(alpha = 0.45f),
                    topLeft = Offset(left - 3 * unit, top - 3 * unit),
                    size = Size(cellW + 6 * unit, cellH + 6 * unit),
                    cornerRadius = CornerRadius(3 * unit),
                )
            }
            drawRoundRect(color, topLeft = Offset(left, top), size = Size(cellW, cellH), cornerRadius = CornerRadius(1.5f * unit))
        }
    }
}

@Composable
private fun DistrictCard(eyebrow: String, name: String, progress: String, totals: String, fraction: Float, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().card().clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = eyebrow.uppercase(), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Accent, fontSize = 11.sp))
        Text(text = name, style = MaterialTheme.typography.headlineLarge.copy(color = BlockNightColors.TextStrong, fontSize = 28.sp))
        Text(text = progress, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
        ProgressBar(fraction = fraction, modifier = Modifier.padding(top = 6.dp))
        Text(text = totals, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 11.sp))
    }
}

/** Full-width menu row with a chevron. [highlight] marks something waiting (today's floor, a saved run). */
@Composable
private fun MenuRow(title: String, caption: String, highlight: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .card(radius = 16.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong, fontSize = 17.sp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (highlight) BlockNightColors.Accent else BlockNightColors.TextMuted,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = BlockNightColors.TextMuted,
            modifier = Modifier.padding(start = 8.dp).size(24.dp),
        )
    }
}

@Composable
private fun MenuTile(title: String, caption: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .card(radius = 16.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge.copy(color = BlockNightColors.TextStrong, fontSize = 16.sp))
        Text(text = caption, style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted, fontSize = 12.sp))
    }
}
