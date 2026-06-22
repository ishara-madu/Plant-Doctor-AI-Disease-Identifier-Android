package com.pixeleye.plantdoctor.ui.components

import com.pixeleye.plantdoctor.R

enum class SnackbarType {
    SUCCESS,
    ERROR,
    INFO,
    WARNING,
    OFFLINE
}

data class SnackbarState(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO
)

fun SnackbarType.getIconRes(): Int {
    return when (this) {
        SnackbarType.SUCCESS -> R.drawable.circle_check
        SnackbarType.ERROR -> R.drawable.triangle_alert
        SnackbarType.INFO -> R.drawable.bell
        SnackbarType.WARNING -> R.drawable.triangle_alert
        SnackbarType.OFFLINE -> R.drawable.wifi
    }
}
