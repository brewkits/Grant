package dev.brewkits.grant.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brewkits.grant.GrantStatus

/**
 * Color-coded badge chip for displaying grant status.
 *
 * Color palette:
 * - GRANTED        → Green
 * - PARTIAL_GRANTED → Amber/Yellow
 * - DENIED         → Orange
 * - DENIED_ALWAYS  → Red
 * - NOT_DETERMINED → Neutral grey
 * - null           → Light grey (not yet checked)
 */
@Composable
fun StatusChip(status: GrantStatus?, modifier: Modifier = Modifier) {
    val targetColor = when (status) {
        GrantStatus.GRANTED -> Color(0xFF2E7D32)          // Deep Green
        GrantStatus.PARTIAL_GRANTED -> Color(0xFFF57F17)  // Amber
        GrantStatus.DENIED -> Color(0xFFE65100)           // Deep Orange
        GrantStatus.DENIED_ALWAYS -> Color(0xFFC62828)    // Deep Red
        GrantStatus.NOT_DETERMINED -> Color(0xFF546E7A)   // Blue Grey
        null -> Color(0xFF9E9E9E)                          // Neutral Grey
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "StatusChipColor"
    )

    val label = when (status) {
        GrantStatus.GRANTED -> "✓ GRANTED"
        GrantStatus.PARTIAL_GRANTED -> "◑ PARTIAL"
        GrantStatus.DENIED -> "✕ DENIED"
        GrantStatus.DENIED_ALWAYS -> "⛔ BLOCKED"
        GrantStatus.NOT_DETERMINED -> "? UNKNOWN"
        null -> "— NOT CHECKED"
    }

    Surface(
        modifier = modifier,
        color = animatedColor,
        shape = RoundedCornerShape(50),
        shadowElevation = 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
