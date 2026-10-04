package com.numbered.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.appContainer
import com.numbered.app.ui.theme.TitleFont
import com.numbered.app.ui.theme.TitleStyle
import com.numbered.app.ui.theme.TitleTab
import com.numbered.app.ui.theme.TitleTreatment
import com.numbered.app.ui.theme.TitleWeight

@Composable
fun StyledTabTitle(tab: TitleTab, text: String, modifier: Modifier = Modifier) {
    val styles by LocalContext.current.appContainer.titleStyles.styles.collectAsStateWithLifecycle()
    StyledTitleText(text, styles.getValue(tab), modifier)
}

@Composable
fun StyledTitleText(text: String, selection: TitleStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge.withTitleStyle(selection),
    )
}

@Composable
internal fun TextStyle.withTitleStyle(selection: TitleStyle): TextStyle = copy(
    fontFamily = when (selection.font) {
        TitleFont.Clean -> FontFamily.SansSerif
        TitleFont.Book -> FontFamily.Serif
        TitleFont.Mono -> FontFamily.Monospace
    },
    fontWeight = when (selection.weight) {
        TitleWeight.Regular -> FontWeight.Normal
        TitleWeight.Medium -> FontWeight.Medium
        TitleWeight.Bold -> FontWeight.Bold
    },
    brush = selection.treatment.brush(),
)

@Composable
private fun TitleTreatment.brush(): Brush {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        TitleTreatment.Ink -> SolidColor(colors.onBackground)
        TitleTreatment.Accent -> SolidColor(colors.primary)
        TitleTreatment.Warm -> SolidColor(colors.secondary)
        TitleTreatment.Dawn -> Brush.linearGradient(listOf(colors.secondary, colors.tertiary))
        TitleTreatment.Dusk -> Brush.linearGradient(listOf(colors.primary, colors.tertiary))
    }
}
