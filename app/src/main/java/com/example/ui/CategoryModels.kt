package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class TimeCategory(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val lightColor: Color
)

val Categories = listOf(
    TimeCategory("work", "工作", Icons.Default.Laptop, Color(0xFF4F46E5), Color(0xFFEEF2FF)),
    TimeCategory("study", "学习", Icons.Default.School, Color(0xFF7C3AED), Color(0xFFF5F3FF)),
    TimeCategory("sports", "运动", Icons.Default.FitnessCenter, Color(0xFF059669), Color(0xFFECFDF5)),
    TimeCategory("rest", "休息", Icons.Default.Bed, Color(0xFFD97706), Color(0xFFFFFBEB)),
    TimeCategory("entertainment", "娱乐", Icons.Default.Gamepad, Color(0xFFDB2777), Color(0xFFFDF2F8)),
    TimeCategory("routine", "日常", Icons.Default.Coffee, Color(0xFF0891B2), Color(0xFFECFEFF))
)

fun getCategoryColor(name: String): Color {
    return Categories.find { it.name == name }?.color ?: Color(0xFF64748B)
}

fun getCategoryLightColor(name: String): Color {
    return Categories.find { it.name == name }?.lightColor ?: Color(0xFFF1F5F9)
}

fun getCategoryIcon(name: String): ImageVector {
    return Categories.find { it.name == name }?.icon ?: Icons.Default.Schedule
}
