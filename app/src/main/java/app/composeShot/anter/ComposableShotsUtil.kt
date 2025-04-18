package app.composeShot.anter

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class for capturing screenshots of Composable functions and sharing them.
 * Works across all Android versions with proper logging.
 */
object ComposableShotsUtil {
    // Logging tag
    private const val TAG = "ComposableShotsUtil"

    // Log level configuration
    enum class LogLevel { NONE, ERROR, INFO, VERBOSE }
    var logLevel = LogLevel.INFO

    /**
     * Captures a screenshot of a composable by embedding it in the current activity.
     * Uses ViewTreeObserver to ensure view is fully laid out before capturing.
     *
     * @param context Should be an Activity context
     * @param content The composable function to capture
     * @param width Width of the screenshot (default: screen width)
     * @param height Height of the screenshot (default: wrap content)
     * @param onScreenshotCaptured Callback with the captured bitmap
     * @param onError Callback for error handling
     */
    fun captureComposable(
        context: Context,
        content: @Composable () -> Unit,
        width: Int = context.resources.displayMetrics.widthPixels,
        height: Int? = null,
        onScreenshotCaptured: (Bitmap) -> Unit,
        onError: (Exception) -> Unit = { e ->
            log(LogLevel.ERROR, "Error capturing composable: ${e.message}", e)
        }
    ) {
        log(LogLevel.INFO, "Starting composable capture")

        try {
            // Get activity from context
            val activity = when (context) {
                is Activity -> context
                else -> {
                    val msg = "Context must be an Activity for composable capture"
                    log(LogLevel.ERROR, msg)
                    onError(IllegalArgumentException(msg))
                    return
                }
            }

            // Ensure we're on main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Handler(Looper.getMainLooper()).post {
                    captureComposable(context, content, width, height, onScreenshotCaptured, onError)
                }
                return
            }

            // Create a temporary container for our ComposeView
            val composeView = ComposeView(activity).apply {
                // Set a unique ID to find it later
                id = View.generateViewId()

                // Set the content
                setContent(content)
            }

            // Add our view to the activity's root view
            val decorView = activity.window.decorView as ViewGroup
            val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)

            log(LogLevel.VERBOSE, "Adding temporary ComposeView to activity root")

            // Use FrameLayout.LayoutParams with the specified width/height
            val params = FrameLayout.LayoutParams(
                width,
                height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            )

            rootView.addView(composeView, params)

            // We need to ensure the view is fully laid out and drawn before capturing
            composeView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    // Remove the listener to avoid multiple calls
                    composeView.viewTreeObserver.removeOnPreDrawListener(this)

                    // Need to wait until after this drawing pass is complete
                    composeView.post {
                        // And then wait for one more frame to be sure
                        composeView.postDelayed({
                            try {
                                log(LogLevel.INFO, "View ready for capture: ${composeView.width}x${composeView.height}")

                                // Double check that view has non-zero size
                                if (composeView.width <= 0 || composeView.height <= 0) {
                                    rootView.removeView(composeView)
                                    onError(IllegalStateException("ComposeView has zero width or height"))
                                    return@postDelayed
                                }

                                // Capture bitmap using manual drawing rather than drawToBitmap()
                                val bitmap = Bitmap.createBitmap(
                                    composeView.width,
                                    composeView.height,
                                    Bitmap.Config.ARGB_8888
                                )

                                val canvas = Canvas(bitmap)
                                composeView.draw(canvas)

                                log(LogLevel.INFO, "Screenshot captured: ${bitmap.width}x${bitmap.height}")

                                // Clean up - remove the temporary view
                                rootView.removeView(composeView)
                                log(LogLevel.VERBOSE, "Temporary ComposeView removed")

                                // Return the bitmap
                                onScreenshotCaptured(bitmap)
                            } catch (e: Exception) {
                                log(LogLevel.ERROR, "Error capturing bitmap", e)
                                // Clean up even on error
                                rootView.removeView(composeView)
                                onError(e)
                            }
                        }, 50) // Short delay to ensure rendering is complete
                    }

                    // Continue with drawing
                    return true
                }
            })
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to set up composable capture", e)
            onError(e)
        }
    }

    /**
     * Alternative approach for capturing using the current view's context.
     * For use within an existing composable.
     */
    fun captureFromExistingView(
        existingView: View,
        content: @Composable () -> Unit,
        width: Int,
        height: Int? = null,
        onScreenshotCaptured: (Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val context = existingView.context
        val activity = context as? Activity ?: run {
            onError(IllegalArgumentException("View must be attached to an Activity"))
            return
        }

        captureComposable(
            context = activity,
            content = content,
            width = width,
            height = height,
            onScreenshotCaptured = onScreenshotCaptured,
            onError = onError
        )
    }

    /**
     * Shares a bitmap using Android's share intent.
     */
    fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        title: String = "Share Image",
        fileName: String = "screenshot_${System.currentTimeMillis()}.png",
        onError: (Exception) -> Unit = { e ->
            log(LogLevel.ERROR, "Error sharing bitmap: ${e.message}", e)
        }
    ) {
        log(LogLevel.INFO, "Preparing to share bitmap: $fileName")

        try {
            // Create cache directory if it doesn't exist
            val imagesFolder = File(context.cacheDir, "composableShots")
            if (!imagesFolder.exists()) {
                val created = imagesFolder.mkdirs()
                log(LogLevel.VERBOSE, "Created directory: $created")
            }

            // Create file and save bitmap
            val file = File(imagesFolder, fileName)
            log(LogLevel.VERBOSE, "Saving bitmap to: ${file.absolutePath}")

            FileOutputStream(file).use { outputStream ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                log(LogLevel.VERBOSE, "Bitmap compressed successfully: $compressed")
            }

            // Get content URI via FileProvider
            val authority = "${context.packageName}.fileprovider"
            log(LogLevel.VERBOSE, "Using FileProvider authority: $authority")

            val contentUri = FileProvider.getUriForFile(context, authority, file)
            log(LogLevel.INFO, "ContentURI generated: $contentUri")

            // Create and launch share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, title))
            log(LogLevel.INFO, "Share intent launched with title: $title")
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Error in shareBitmap", e)
            onError(e)
        }
    }

    /**
     * Captures and immediately shares a composable.
     */
    fun captureAndShare(
        context: Context,
        content: @Composable () -> Unit,
        title: String = "Share Image",
        width: Int = context.resources.displayMetrics.widthPixels,
        height: Int? = null,
        onError: (Exception) -> Unit = { e ->
            log(LogLevel.ERROR, "Error in captureAndShare: ${e.message}", e)
        }
    ) {
        log(LogLevel.INFO, "Starting captureAndShare process")

        if (context !is Activity) {
            onError(IllegalArgumentException("Context must be an Activity for captureAndShare"))
            return
        }

        captureComposable(
            context = context,
            content = content,
            width = width,
            height = height,
            onScreenshotCaptured = { bitmap ->
                log(LogLevel.INFO, "Screenshot captured, proceeding to share")
                shareBitmap(context, bitmap, title, onError = onError)
            },
            onError = onError
        )
    }

    /**
     * Saves a bitmap to the device's storage based on Android version.
     *
     * @return The URI of the saved file
     */
    suspend fun saveBitmapToStorage(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "composableShot_${System.currentTimeMillis()}.png",
        mimeType: String = "image/png",
        quality: Int = 100
    ): Uri? = withContext(Dispatchers.IO) {
        log(LogLevel.INFO, "Saving bitmap to storage: $fileName")

        try {
            // Handle different Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+) - Use MediaStore
                log(LogLevel.VERBOSE, "Using MediaStore API (Android 10+)")
                saveImageWithMediaStore(context, bitmap, fileName, mimeType, quality)
            } else {
                // Pre-Android 10 - Direct file access
                log(LogLevel.VERBOSE, "Using direct file access (pre-Android 10)")
                saveImageToExternalStorage(context, bitmap, fileName, quality)
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Failed to save bitmap to storage", e)
            null
        }
    }

    /**
     * Saves image using MediaStore API (Android 10+)
     */
    private suspend fun saveImageWithMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        mimeType: String,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    val success = bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
                    log(LogLevel.VERBOSE, "MediaStore compression success: $success")
                }

                // Clear pending flag
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                log(LogLevel.INFO, "Image saved successfully via MediaStore: $uri")
            }

            uri
        } catch (e: Exception) {
            log(LogLevel.ERROR, "MediaStore save failed", e)
            null
        }
    }

    /**
     * Saves image to external storage directly (pre-Android 10)
     */
    private suspend fun saveImageToExternalStorage(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(picturesDir, "ComposableShots")

            if (!appDir.exists()) {
                val created = appDir.mkdirs()
                log(LogLevel.VERBOSE, "Created app directory: $created")
            }

            val imageFile = File(appDir, fileName)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream)
                outputStream.flush()
            }

            // Notify gallery about the new file
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val contentUri = Uri.fromFile(imageFile)
            mediaScanIntent.data = contentUri
            context.sendBroadcast(mediaScanIntent)

            log(LogLevel.INFO, "Image saved successfully to: ${imageFile.absolutePath}")
            contentUri
        } catch (e: IOException) {
            log(LogLevel.ERROR, "External storage save failed", e)
            null
        }
    }

    /**
     * Internal logging function that respects the configured log level
     */
    private fun log(level: LogLevel, message: String, throwable: Throwable? = null) {
        if (level.ordinal <= logLevel.ordinal) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val logMessage = "[$timestamp] $message"

            when (level) {
                LogLevel.ERROR -> Log.e(TAG, logMessage, throwable)
                LogLevel.INFO -> Log.i(TAG, logMessage)
                LogLevel.VERBOSE -> Log.v(TAG, logMessage)
                LogLevel.NONE -> { /* No logging */ }
            }
        }
    }

    /**
     * Cleans up temporary files created by the utility
     */
    fun cleanupTempFiles(context: Context) {
        try {
            val imagesFolder = File(context.cacheDir, "composableShots")
            if (imagesFolder.exists()) {
                log(LogLevel.INFO, "Cleaning up temporary files")
                imagesFolder.listFiles()?.forEach { file ->
                    val deleted = file.delete()
                    log(LogLevel.VERBOSE, "Deleted file ${file.name}: $deleted")
                }
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Error cleaning up temp files", e)
        }
    }
}