package pt.tiago.blocknight.ui.district

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.tiago.blocknight.R
import pt.tiago.blocknight.ui.components.countText
import pt.tiago.blocknight.core.content.Districts
import pt.tiago.blocknight.core.progress.Profile
import pt.tiago.blocknight.data.ContentRepository
import pt.tiago.blocknight.ui.components.ProgressBar
import pt.tiago.blocknight.ui.components.SecondaryButton
import pt.tiago.blocknight.ui.components.StarRow
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.theme.BlockNightColors

/**
 * Mapa do bairro (PLANO §3.2, DESIGN §5). A torre lê-se de baixo para cima: o andar 1 fica em baixo e a lista
 * abre no andar atual. Bloqueados aparecem com cadeado e explicação; nunca só com opacidade.
 */
@Composable
fun DistrictScreen(
    content: ContentRepository,
    profile: Profile,
    districtId: Int,
    difficulty: String,
    onDifficulty: (String) -> Unit,
    onDistrict: (Int) -> Unit,
    onFloor: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val district = Districts.ALL[districtId - 1]
    val first = (districtId - 1) * Districts.FLOORS_PER_DISTRICT + 1
    val floors = (first until first + Districts.FLOORS_PER_DISTRICT).toList()
    val current = floors.firstOrNull { !profile.record(difficulty, Districts.floorId(it)).completed } ?: floors.last()
    val stars = floors.sumOf { profile.record(difficulty, Districts.floorId(it)).stars }
    val done = floors.count { profile.record(difficulty, Districts.floorId(it)).completed }
    val listState = rememberLazyListState()
    val floorTitles = stringArrayResource(R.array.floor_titles)

    LaunchedEffect(districtId, difficulty) {
        listState.scrollToItem((current - first).coerceAtLeast(0))
    }

    Box(modifier = Modifier.fillMaxSize().background(BlockNightColors.Background).safeDrawingPadding()) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 520.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = stringResource(R.string.cd_back), onClick = onBack)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(enabled = districtId > 1) { onDistrict(districtId - 1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "‹", style = MaterialTheme.typography.headlineLarge.copy(color = if (districtId > 1) BlockNightColors.TextStrong else BlockNightColors.Line))
                }
                Text(
                    text = district.name,
                    style = MaterialTheme.typography.headlineLarge.copy(color = BlockNightColors.TextStrong, fontSize = 28.sp),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(enabled = districtId < Districts.COUNT) { onDistrict(districtId + 1) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "›", style = MaterialTheme.typography.headlineLarge.copy(color = if (districtId < Districts.COUNT) BlockNightColors.TextStrong else BlockNightColors.Line))
                }
            }
            DifficultyPicker(selected = difficulty, onSelect = onDifficulty)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "$done / ${Districts.FLOORS_PER_DISTRICT} · " + countText(R.string.cd_stars_one, R.string.cd_stars_other, stars, stars),
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted),
                )
            }
            ProgressBar(fraction = done / Districts.FLOORS_PER_DISTRICT.toFloat(), modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(floors, key = { it }) { ordinal ->
                    FloorRow(
                        ordinal = ordinal,
                        title = floorTitles[ordinal - 1],
                        subtitle = objectiveText(content, ordinal, difficulty),
                        record = profile.record(difficulty, Districts.floorId(ordinal)),
                        unlocked = profile.isUnlocked(difficulty, ordinal) { Districts.floorId(it) } && content.isAvailable(ordinal),
                        available = content.isAvailable(ordinal),
                        isCurrent = ordinal == current,
                        onClick = { onFloor(ordinal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyPicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().card(radius = 14.dp).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf("easy", "medium", "hard").forEach { id ->
            val on = id == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        if (on) BlockNightColors.Accent.copy(alpha = 0.12f) else BlockNightColors.Surface,
                        androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
                    )
                    .clickable(onClick = { onSelect(id) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (id) {
                        "easy" -> stringResource(R.string.difficulty_easy)
                        "hard" -> stringResource(R.string.difficulty_hard)
                        else -> stringResource(R.string.difficulty_medium)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (on) BlockNightColors.Accent else BlockNightColors.TextMuted,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FloorRow(
    ordinal: Int,
    title: String,
    subtitle: String,
    record: pt.tiago.blocknight.core.progress.FloorRecord,
    unlocked: Boolean,
    available: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val description = when {
        isCurrent -> stringResource(R.string.floor_row_current, ordinal, title, subtitle)
        !available -> stringResource(R.string.floor_row_pending, ordinal)
        !unlocked -> stringResource(R.string.floor_row_locked, ordinal)
        else -> stringResource(R.string.floor_row_done, ordinal, title, countText(R.string.cd_stars_one, R.string.cd_stars_other, record.stars, record.stars))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(
                if (isCurrent) BlockNightColors.Accent else BlockNightColors.Surface,
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            .clickable(enabled = unlocked, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = ordinal.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                color = if (isCurrent) BlockNightColors.OnAccent else if (unlocked) BlockNightColors.TextStrong else BlockNightColors.TextMuted,
                fontSize = 15.sp,
            ),
            modifier = Modifier.widthIn(min = 36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isCurrent) BlockNightColors.OnAccent else BlockNightColors.TextStrong,
                    fontSize = 14.sp,
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isCurrent) BlockNightColors.OnAccent else BlockNightColors.TextMuted,
                    fontSize = 11.sp,
                ),
            )
        }
        when {
            isCurrent -> Text(text = stringResource(R.string.floor_play), style = MaterialTheme.typography.labelLarge.copy(color = BlockNightColors.OnAccent, fontSize = 13.sp))
            !available -> Text(text = stringResource(R.string.cd_pending), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted))
            !unlocked -> Text(text = "🔒", style = MaterialTheme.typography.bodySmall)
            else -> StarRow(stars = record.stars)
        }
    }
}

/** Texto curto do objetivo de um andar, em linguagem natural (PLANO §3.2). */
@Composable
fun objectiveText(content: ContentRepository, ordinal: Int, difficulty: String): String {
    val variant = content.variant(ordinal, difficulty) ?: return ""
    return when (val spec = variant.objective.type) {
        "lines" -> countText(R.string.objective_lines_one, R.string.objective_lines_other, variant.objective.n ?: 0, variant.objective.n ?: 0)
        "dig" -> stringResource(R.string.objective_dig)
        "targets" -> stringResource(R.string.objective_targets)
        "score" -> stringResource(R.string.objective_score, (variant.objective.points ?: 0L).toString())
        "quads" -> countText(R.string.objective_quads_one, R.string.objective_quads_other, variant.objective.n ?: 0, variant.objective.n ?: 0)
        "tspins" -> countText(R.string.objective_tspins_one, R.string.objective_tspins_other, variant.objective.n ?: 0, variant.objective.n ?: 0)
        "combo" -> stringResource(R.string.objective_combo, variant.objective.n ?: 0)
        else -> spec
    }
}
