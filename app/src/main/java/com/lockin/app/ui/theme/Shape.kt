package com.lockin.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Shapes defined in the design guidelines: No rounded corners > 8dp on cards, 4dp on buttons, 0dp on bottom sheets.
val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),    // 4dp for buttons and small fields
    medium = RoundedCornerShape(8.dp),   // 8dp for cards
    large = RoundedCornerShape(0.dp)     // 0dp for bottom sheets
)
