package com.makd.afinity.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R

@Composable
fun rememberQuickConnectDigits(): Pair<Array<MutableState<String>>, Array<FocusRequester>> {
    val digits = remember { Array(6) { mutableStateOf("") } }
    val focusRequesters = remember { Array(6) { FocusRequester() } }
    return digits to focusRequesters
}

@Composable
fun QuickConnectDigitEntry(
    digits: Array<MutableState<String>>,
    focusRequesters: Array<FocusRequester>,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            digits.forEachIndexed { index, digitState ->
                BasicTextField(
                    value = digitState.value,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        if (filtered.isEmpty()) {
                            digitState.value = ""
                        } else {
                            digitState.value = filtered.takeLast(1)
                            if (index < 5) focusRequesters[index + 1].requestFocus()
                        }
                    },
                    modifier =
                        Modifier.size(42.dp)
                            .focusRequester(focusRequesters[index])
                            .onKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Backspace &&
                                        digitState.value.isEmpty() &&
                                        index > 0
                                ) {
                                    focusRequesters[index - 1].requestFocus()
                                    digits[index - 1].value = ""
                                    true
                                } else false
                            },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (index == 5) ImeAction.Done else ImeAction.Next,
                        ),
                    enabled = !isAuthorizing && !isSuccess,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle =
                        LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .border(
                                        width = if (digitState.value.isNotEmpty()) 2.dp else 1.dp,
                                        color =
                                            when {
                                                isSuccess -> Color(0xFF4CAF50)
                                                errorMessage != null ->
                                                    MaterialTheme.colorScheme.error
                                                digitState.value.isNotEmpty() ->
                                                    MaterialTheme.colorScheme.primary
                                                else ->
                                                    MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.4f
                                                    )
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            innerTextField()
                        }
                    },
                )
            }
        }
        when {
            isSuccess ->
                Text(
                    text = stringResource(R.string.dialog_quickconnect_authorized),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
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