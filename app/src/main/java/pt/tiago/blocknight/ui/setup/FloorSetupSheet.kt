@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.tiago.blocknight.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.tiago.blocknight.R
import pt.tiago.blocknight.ui.components.countText
import pt.tiago.blocknight.core.content.VariantSpec
import pt.tiago.blocknight.core.progress.FloorRecord
import pt.tiago.blocknight.ui.components.PrimaryButton
import pt.tiago.blocknight.ui.components.StarRow
import pt.tiago.blocknight.ui.components.card
import pt.tiago.blocknight.ui.components.difficultyName
import pt.tiago.blocknight.ui.theme.BlockNightColors
import pt.tiago.blocknight.ui.district.objectiveText
import pt.tiago.blocknight.data.ContentRepository

/**
 * Preparação do andar (PLANO §3.2): objetivo, modificadores, orçamento, limites de estrelas e o melhor
 * resultado. O botão Jogar é a única ação.
 */
@Composable
fun FloorSetupSheet(
    content: ContentRepository,
    ordinal: Int,
    districtName: String,
    difficulty: String,
    record: FloorRecord,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val variant = content.variant(ordinal, difficulty) ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BlockNightColors.Surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title, ordinal, stringArrayResource(R.array.floor_titles)[ordinal - 1]),
                style = MaterialTheme.typography.headlineMedium.copy(color = BlockNightColors.TextStrong),
            )
            Text(
                text = objectiveText(content, ordinal, difficulty),
                style = MaterialTheme.typography.bodyLarge.copy(color = BlockNightColors.Text),
            )
            Modifiers(variant)
            Text(
                text = difficultyName(difficulty) + " · " + countText(R.string.setup_budget_one, R.string.setup_budget_other, variant.maxPieces, variant.maxPieces) +
                    " · " + stringResource(R.string.modifier_speed, variant.speedLevel),
                style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted),
            )
            Text(
                text = stringResource(R.string.setup_stars, variant.threeStarMaxPieces, variant.twoStarMaxPieces),
                style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextStrong),
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                StarRow(stars = record.stars)
                Text(
                    text = if (record.bestPieces != null) {
                        stringResource(R.string.setup_best, record.bestPieces ?: 0, record.bestScore.toString())
                    } else {
                        stringResource(R.string.setup_no_best)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.TextMuted),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            PrimaryButton(text = stringResource(R.string.setup_play), onClick = onPlay)
        }
    }
}

@Composable
private fun Modifiers(variant: VariantSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        variant.risingGarbage?.let {
            Text(text = stringResource(R.string.modifier_rising, it.everyPieces), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Orange))
        }
        if (!variant.allowHold) {
            Text(text = stringResource(R.string.modifier_no_hold), style = MaterialTheme.typography.bodySmall.copy(color = BlockNightColors.Orange))
        }
    }
}

