package io.github.cruzpazv.pardela.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Informe de la última vez que la app se cerró sola. */
@Composable
fun AvisoFallo(informe: String, cerrar: () -> Unit) {
    val portapapeles = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = cerrar,
        containerColor = C.panel,
        title = { Texto("La app se cerró por un error", 22, C.texto, FontWeight.Bold) },
        text = {
            Column {
                Texto("Copie este informe y envíelo para que se pueda arreglar.", 17, C.texto)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Texto(informe, 12, C.gris, mono = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { portapapeles.setText(AnnotatedString(informe)); cerrar() }) {
                Texto("COPIAR", 18, C.ambar, FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = cerrar) { Texto("CERRAR", 18, C.gris, FontWeight.Bold) } },
    )
}
