package klama.ai.compose.io

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import okio.BufferedSource
import okio.buffer
import okio.source

actual class FilePicker actual constructor() {
    companion object {
        var launcher: ActivityResultLauncher<Array<String>>? = null
        var onResultCallback: ((BufferedSource?) -> Unit)? = null
        
        fun handleResult(context: Context, uri: Uri?) {
            if (uri != null) {
                val inputStream = context.contentResolver.openInputStream(uri)
                onResultCallback?.invoke(inputStream?.source()?.buffer())
            } else {
                onResultCallback?.invoke(null)
            }
        }
    }

    actual fun pickGguf(onResult: (BufferedSource?) -> Unit) {
        onResultCallback = onResult
        launcher?.launch(arrayOf("*/*"))
    }
}
