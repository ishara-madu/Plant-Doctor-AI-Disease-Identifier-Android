package com.pixeleye.plantdoctor.utils

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

object CameraUtils {

    private const val DATE_FORMAT = "yyyyMMdd_HHmmss"

    fun createCameraProvider(
        context: Context,
        onCameraProviderReady: (ProcessCameraProvider) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                onCameraProviderReady(cameraProvider)
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun createPreviewUseCase(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Preview {
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
        return preview
    }

    fun createImageCaptureUseCase(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        imageCapture: ImageCapture
    ): ImageCapture {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            imageCapture
        )
        return imageCapture
    }

    fun captureImage(
        imageCapture: ImageCapture,
        context: Context,
        executor: Executor,
        onImageCaptured: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val photoFile = createTempImageFile(context)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                    } catch (e: Exception) {
                        Uri.fromFile(photoFile)
                    }
                    onImageCaptured(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun createTempImageFile(context: Context): File {
        val timestamp = SimpleDateFormat(DATE_FORMAT, Locale.US).format(System.currentTimeMillis())
        val storageDir = context.cacheDir
        return File.createTempFile("plant_${timestamp}_", ".jpg", storageDir)
    }

    fun getCameraExecutor(context: Context): Executor {
        return ContextCompat.getMainExecutor(context)
    }
}
