package com.phoenix.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/** Row-tap helper used by list items across the browse/radio screens. */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
