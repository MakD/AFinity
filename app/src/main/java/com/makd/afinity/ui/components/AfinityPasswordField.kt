package com.makd.afinity.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AfinityPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelColor: Color? = null,
    leadingIcon: Painter? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    revealed: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
    onImeAction: (() -> Unit)? = null,
) {
    val state = rememberSyncedTextFieldState(value, onValueChange)
    val container = MaterialTheme.colorScheme.surfaceContainerHighest
    SecureTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(painter = it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        textObfuscationMode =
            if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.System,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onImeAction?.let { action -> KeyboardActionHandler { action() } },
        shape = RoundedCornerShape(14.dp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = container,
                unfocusedContainerColor = container,
                disabledContainerColor = container,
                errorContainerColor = container,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = MaterialTheme.colorScheme.error,
                focusedLabelColor = labelColor ?: MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}
