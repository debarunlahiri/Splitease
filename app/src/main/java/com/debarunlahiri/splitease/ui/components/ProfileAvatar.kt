package com.debarunlahiri.splitease.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

@Composable
internal fun ProfileAvatar(photoUrl: String?) {
    if (photoUrl.isNullOrBlank()) {
        Symbol(Icons.Rounded.Person, Modifier.size(72.dp))
        return
    }
    SubcomposeAsyncImage(
        model = photoUrl,
        contentDescription = "Your profile picture",
        modifier = Modifier.size(72.dp).clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = { Symbol(Icons.Rounded.Person, Modifier.fillMaxSize()) },
        error = { Symbol(Icons.Rounded.Person, Modifier.fillMaxSize()) },
    )
}
