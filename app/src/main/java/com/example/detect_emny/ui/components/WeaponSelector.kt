package com.example.detect_emeny.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.detect_emeny.config.AppConfig

@Composable
fun WeaponSelector(
    label: String,
    currentWeapon: String,
    onWeaponChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!AppConfig.ENABLE_WEAPON) return

    Button(
        onClick = {
            val nextWeapon = when (currentWeapon) {
                "sward2.png" -> "baseballbat.png"
                "baseballbat.png" -> ""
                else -> "sward2.png"
            }
            onWeaponChange(nextWeapon)
        },
        modifier = modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White)
            Text(
                when (currentWeapon) {
                    "sward2.png" -> "Sword"
                    "baseballbat.png" -> "Bat"
                    else -> "None"
                },
                color = Color.Yellow
            )
        }
    }
}
