package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Helper class placed in the 'android.print' package to bypass visibility restrictions
 * on PrintDocumentAdapter callbacks. This allows headless PDF generation without the print UI.
 */
class PdfPrinter(private val attributes: PrintAttributes) {

    interface Callback {
        fun onSuccess(file: File, pageCount: Int)
        fun onFailure(error: String)
        fun onCancelled()
    }

    fun generatePdf(adapter: PrintDocumentAdapter, file: File, callback: Callback) {
        adapter.onStart()

        adapter.onLayout(
            null,
            attributes,
            CancellationSignal(),
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                    try {
                        val pfd = ParcelFileDescriptor.open(
                            file,
                            ParcelFileDescriptor.MODE_READ_WRITE or
                            ParcelFileDescriptor.MODE_CREATE or
                            ParcelFileDescriptor.MODE_TRUNCATE
                        )

                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            pfd,
                            CancellationSignal(),
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(pages: Array<PageRange>) {
                                    pfd.close()
                                    adapter.onFinish()
                                    callback.onSuccess(file, info.pageCount)
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    pfd.close()
                                    adapter.onFinish()
                                    callback.onFailure(error?.toString() ?: "Write failed")
                                }

                                override fun onWriteCancelled() {
                                    pfd.close()
                                    adapter.onFinish()
                                    callback.onCancelled()
                                }
                            }
                        )
                    } catch (e: Exception) {
                        adapter.onFinish()
                        callback.onFailure(e.message ?: "Failed to open file descriptor")
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    adapter.onFinish()
                    callback.onFailure(error?.toString() ?: "Layout failed")
                }

                override fun onLayoutCancelled() {
                    adapter.onFinish()
                    callback.onCancelled()
                }
            },
            Bundle()
        )
    }
}
