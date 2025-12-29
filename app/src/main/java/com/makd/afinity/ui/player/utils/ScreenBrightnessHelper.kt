package com.makd.afinity.ui.player.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.makd.afinity.R

@Composable
fun RequiresBrightnessPermission(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(Settings.System.canWrite(context)) }

    var hasDismissed by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.System.canWrite(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (hasPermission || hasDismissed) {
        content()
    } else {
        PermissionRequestUI(
            context = context,
            onDismiss = { hasDismissed = true }
        )
    }
}

@Composable
fun PermissionRequestUI(
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_brightness),
                contentDescription = null
            )
        },
        title = {
            Text(text = "Brightness Control")
        },
        text = {
            Text(
                text = "To provide smooth brightness gestures without conflicting with your phone's Auto-Brightness, " +
                        "Afinity needs permission to modify system settings.\n\n" +
                        "Without this, brightness controls may be inconsistent."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Allow Access")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

@Composable
fun ScreenBrightnessController(brightness: Float) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    val hasWritePermission = remember(context) { Settings.System.canWrite(context) }

    var originalBrightnessMode by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(brightness) {
        if (brightness < 0f) {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = layoutParams

            if (hasWritePermission && originalBrightnessMode != null) {
                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        originalBrightnessMode!!
                    )
                    originalBrightnessMode = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            if (hasWritePermission) {
                try {
                    val resolver = context.contentResolver
                    if (originalBrightnessMode == null) {
                        originalBrightnessMode = Settings.System.getInt(
                            resolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE
                        )
                    }
                    Settings.System.putInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = brightness
            activity.window.attributes = layoutParams
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = layoutParams

            if (hasWritePermission && originalBrightnessMode != null) {
                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        originalBrightnessMode!!
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}