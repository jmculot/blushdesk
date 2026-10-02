package com.blushdesk.app.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blushdesk.app.ui.theme.Dimens
import com.blushdesk.app.utils.AppFiles
import java.io.File

/** What a dialog needs from the ViewModel to handle photos, without depending on the ViewModel itself. */
class PhotoActions(
    val importPhoto: suspend (Uri) -> String?,
    val discard: (String?) -> Unit,
)

/**
 * Avatar preview with "Take photo" and "Choose photo" buttons.
 *
 *  - Gallery uses the system Photo Picker, which needs no permission: the user hands over only
 *    the one image they pick.
 *  - Camera asks the device's camera app to write into a FileProvider URI in our cache, so the app
 *    never needs the CAMERA permission.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoPickerField(
    imageUri: String?,
    name: String,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp = Dimens.avatarPicker,
) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = cameraUri
        cameraUri = null
        if (saved && target != null) onPicked(target)
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(
            imageUri = imageUri,
            name = name.ifBlank { "?" },
            size = avatarSize,
            ring = BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    problem = null
                    val file = File(AppFiles.cacheDir(context, AppFiles.CAMERA_DIR), "capture_${System.currentTimeMillis()}.jpg")
                    val uri = AppFiles.uriFor(context, file)
                    cameraUri = uri
                    try {
                        camera.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        cameraUri = null
                        problem = "No camera app is available on this device."
                    }
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Take photo")
                }
                OutlinedButton(onClick = {
                    problem = null
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Choose photo")
                }
            }
            if (imageUri != null) {
                TextButton(onClick = onRemove) { Text("Remove photo") }
            }
            problem?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
