package com.scypheon.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun ImageAttachmentRow(
    imageUri: Uri?,
    onImageSelected: (Uri) -> Unit,
    onImageRemoved: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onImageSelected(uri)
    }

    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { launcher.launch("image/*") },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = "Attach Image",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (imageUri != null) {
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .width(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = "Attached Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onImageRemoved,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove Image",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
