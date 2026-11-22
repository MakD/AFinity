package com.makd.afinity.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.theme.isDarkSurface

private val MaxDigitBoxSize = 42.dp
private val MinDigitBoxSize = 24.dp
private const val DigitFontSizeAtMaxBox = 20f
internal const val QuickConnectCodeLength = 6

private val QuickConnectCodeInput = InputTransformation {
    val digits = asCharSequence().filter { it.isDigit() }
    if (digits.length > QuickConnectCodeLength) {
        revertAllChanges()
    } else if (digits.length != length) {
        replace(0, length, digits)
    }
}

@Composable fun rememberQuickConnectCode(): TextFieldState = rememberTextFieldState()

@Composable
fun QuickConnectDigitEntry(
    codeState: TextFieldState,
    isAuthorizing: Boolean,
    isSuccess: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.dialog_quickconnect_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        var isFocused by remember { mutableStateOf(false) }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = if (maxWidth < 260.dp) 6.dp else 8.dp
            val boxSize =
                ((maxWidth - spacing * (QuickConnectCodeLength - 1)) / QuickConnectCodeLength)
                    .coerceIn(MinDigitBoxSize, MaxDigitBoxSize)
            val digitFontSize =
                (DigitFontSizeAtMaxBox * (boxSize / MaxDigitBoxSize).coerceIn(0.65f, 1f)).sp
            val cornerRadius = if (boxSize < 34.dp) 8.dp else 10.dp

            val code = codeState.text
            BasicTextField(
                state = codeState,
                inputTransformation = QuickConnectCodeInput,
                modifier =
                    Modifier.align(Alignment.Center).onFocusChanged { isFocused = it.isFocused },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                enabled = !isAuthorizing && !isSuccess,
                textStyle = LocalTextStyle.current.copy(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                decorator = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        innerTextField()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(QuickConnectCodeLength) { index ->
                                val char = code.getOrNull(index)?.toString() ?: ""
                                val isActive =
                                    isFocused &&
                                        !isAuthorizing &&
                                        !isSuccess &&
                                        index == code.length

                                Box(
                                    modifier =
                                        Modifier.size(boxSize)
                                            .background(
                                                color =
                                                    MaterialTheme.colorScheme
                                                        .surfaceContainerHighest,
                                                shape = RoundedCornerShape(cornerRadius),
                                            )
                                            .border(
                                                width =
                                                    if (char.isNotEmpty() || isActive) 2.dp
                                                    else 1.dp,
                                                color =
                                                    when {
                                                        isSuccess -> Color(0xFF4CAF50)
                                                        errorMessage != null ->
                                                            MaterialTheme.colorScheme.error
                                                        char.isNotEmpty() || isActive ->
                                                            MaterialTheme.colorScheme.primary
                                                        else ->
                                                            MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.4f,
                                                            )
                                                    },
                                                shape = RoundedCornerShape(cornerRadius),
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = char,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = digitFontSize,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }

        val successColor =
            if (isDarkSurface()) {
                Color(0xFF81C784)
            } else {
                Color(0xFF1B6E22)
            }

        when {
            isSuccess ->
                Text(
                    text = stringResource(R.string.dialog_quickconnect_authorized),
                    style = MaterialTheme.typography.bodySmall,
                    color = successColor,
                )
            errorMessage != null ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            else -> Spacer(modifier = Modifier.height(0.dp))
        }
    }
}
