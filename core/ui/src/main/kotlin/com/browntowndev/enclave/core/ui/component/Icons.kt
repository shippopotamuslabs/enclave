package com.browntowndev.enclave.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val XIcon: ImageVector
    get() =
        ImageVector
            .Builder(
                name = "XIcon",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).path(fill = SolidColor(Color.Black)) {
                moveTo(18.244f, 2.25f)
                lineTo(21.552f, 2.25f)
                lineTo(14.325f, 10.51f)
                lineTo(22.827f, 21.75f)
                lineTo(16.17f, 21.75f)
                lineTo(10.956f, 14.933f)
                lineTo(4.99f, 21.75f)
                lineTo(1.68f, 21.75f)
                lineTo(9.41f, 12.915f)
                lineTo(1.254f, 2.25f)
                lineTo(8.08f, 2.25f)
                lineTo(12.793f, 8.481f)
                close()
                moveTo(17.083f, 19.77f)
                lineTo(18.916f, 19.77f)
                lineTo(7.084f, 4.126f)
                lineTo(5.117f, 4.126f)
                close()
            }.build()
