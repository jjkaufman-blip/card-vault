package com.cardvault.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cardvault.CardVaultApp
import com.cardvault.data.Card
import com.cardvault.scan.CardTextParser
import com.cardvault.scan.ImageUtils
import com.cardvault.scan.Ocr
import com.cardvault.scan.loadPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Holds a freshly scanned card until he confirms it on the edit screen. */
object ScanDraft {
    var card: Card? = null
    var message: String? = null
}

private enum class Step { FRONT, BACK, READING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onScanned: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = CardVaultApp.instance.repo

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var step by remember { mutableStateOf(Step.FRONT) }
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var back by remember { mutableStateOf<Bitmap?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun finish(frontBmp: Bitmap, backBmp: Bitmap?) {
        step = Step.READING
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                val frontLines = runCatching { Ocr.readText(frontBmp) }.getOrDefault(emptyList())
                val backLines = backBmp?.let { runCatching { Ocr.readText(it) }.getOrDefault(emptyList()) } ?: emptyList()
                val codes = runCatching { Ocr.readBarcodes(frontBmp) }.getOrDefault(emptyList())
                CardTextParser.parse(frontLines, backLines, codes)
            }
            var guess = result
            var message: String? = null
            // Graded slab: let PSA fill in the exact card and grade.
            if (guess.isGraded && guess.certNumber.isNotBlank() && repo.settings.hasPsa) {
                try {
                    val cert = repo.psa.lookup(guess.certNumber)
                    guess = guess.copy(
                        player = cert.player.ifBlank { guess.player },
                        year = cert.year.ifBlank { guess.year },
                        brand = matchBrand(cert.brand).ifBlank { guess.brand },
                        setName = cert.brand.takeIf { matchBrand(it).isBlank() } ?: guess.setName,
                        cardNumber = cert.cardNumber.ifBlank { guess.cardNumber },
                        grader = "PSA",
                        grade = cert.grade.ifBlank { guess.grade },
                    )
                    message = "Slab details filled in from PSA cert ${cert.certNumber}."
                } catch (e: Exception) {
                    message = e.message
                }
            }
            val (frontPath, backPath) = withContext(Dispatchers.IO) {
                ImageUtils.save(frontBmp, repo.imageDir) to backBmp?.let { ImageUtils.save(it, repo.imageDir) }
            }
            ScanDraft.card = Card(
                player = guess.player, year = guess.year, brand = guess.brand, setName = guess.setName,
                cardNumber = guess.cardNumber, team = guess.team, position = guess.position,
                isGraded = guess.isGraded, grader = guess.grader, grade = guess.grade, certNumber = guess.certNumber,
                frontImage = frontPath, backImage = backPath,
            )
            ScanDraft.message = message ?: "Check the details below, fix anything that's off, then save."
            onScanned()
        }
    }

    fun accept(bmp: Bitmap) {
        when (step) {
            Step.FRONT -> { front = bmp; step = Step.BACK }
            Step.BACK -> { back = bmp; finish(front!!, bmp) }
            Step.READING -> Unit
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val bmp = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
            busy = false
            if (bmp != null) accept(bmp)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            Step.FRONT -> "Scan the front"
                            Step.BACK -> "Now the back"
                            Step.READING -> "Reading card…"
                        }
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (step == Step.READING) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Reading the card's text…", color = Color.White)
                }
            } else {
            if (hasPermission && cameraError == null) {
                CameraPreview(
                    onCaptured = { accept(it) },
                    onError = { cameraError = it },
                    captureRequest = { capture -> CaptureHolder.trigger = capture },
                )
                GuideOverlay()
            } else {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        cameraError ?: "Camera permission is needed to scan. You can also pick photos you've already taken.",
                        color = Color.White, textAlign = TextAlign.Center,
                    )
                    if (!hasPermission) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                }
            }

            // Thumbnail of the front once taken
            front?.let { f ->
                Image(
                    bitmap = f.asImageBitmap(),
                    contentDescription = "Front",
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).width(60.dp).height(84.dp)
                        .clip(RoundedCornerShape(6.dp)).border(2.dp, Gold, RoundedCornerShape(6.dp)),
                )
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xAA000000)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (step == Step.FRONT) "Line the card up inside the frame. For a PSA slab, include the label."
                    else "Flip the card over and line up the back.",
                    color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    OutlinedButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From photo")
                    }
                    if (hasPermission && cameraError == null) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(Color.White).padding(5.dp)
                                .clip(CircleShape).background(if (busy) Color.Gray else Gold),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = { if (!busy) { busy = true; CaptureHolder.trigger?.invoke() } },
                                modifier = Modifier.fillMaxSize(),
                            ) {}
                        }
                    }
                    if (step == Step.BACK) {
                        TextButton(onClick = { finish(front!!, null) }) { Text("Skip back", color = Color.White) }
                    }
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            }
        }
    }

    // Clear the busy spinner whenever a capture completes (accept/finish moves the step on).
    LaunchedEffect(step, front, back) { busy = false }
}

/** Lets the shutter button reach the camera's capture function. */
private object CaptureHolder {
    var trigger: (() -> Unit)? = null
}

@Composable
private fun CameraPreview(
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    captureRequest: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                previewView.post {
                    try {
                        p.unbindAll()
                        val viewPort = previewView.viewPort
                        if (viewPort != null) {
                            val group = UseCaseGroup.Builder()
                                .setViewPort(viewPort)
                                .addUseCase(preview)
                                .addUseCase(imageCapture)
                                .build()
                            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                        } else {
                            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        }
                    } catch (e: Exception) {
                        onError("No camera available on this device. Use \"From photo\" instead.")
                    }
                }
            } catch (e: Exception) {
                onError("Couldn't start the camera. Use \"From photo\" instead.")
            }
        }, ContextCompat.getMainExecutor(context))

        captureRequest {
            imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = try {
                        ImageUtils.toCroppedBitmap(image)
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    scope.launch(Dispatchers.Main) {
                        if (bmp != null) onCaptured(bmp) else onError("Couldn't process that photo. Try again or use \"From photo\".")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    scope.launch(Dispatchers.Main) { onError("Photo failed: ${exception.message}") }
                }
            })
        }

        onDispose {
            provider?.unbindAll()
            CaptureHolder.trigger = null
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Darkens everything outside a card-shaped frame. Same geometry as ImageUtils.cropToGuide. */
@Composable
private fun GuideOverlay() {
    Canvas(
        Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        var w = size.width * ImageUtils.GUIDE_WIDTH_FRACTION
        var h = w / ImageUtils.CARD_ASPECT
        if (h > size.height * 0.95f) {
            h = size.height * 0.95f
            w = h * ImageUtils.CARD_ASPECT
        }
        val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
        drawRect(Color(0x99000000))
        drawRoundRect(Color.Transparent, topLeft, Size(w, h), CornerRadius(24f), blendMode = BlendMode.Clear)
        drawRoundRect(Gold, topLeft, Size(w, h), CornerRadius(24f), style = Stroke(width = 6f))
    }
}

private fun matchBrand(text: String): String {
    val lower = text.lowercase()
    return com.cardvault.scan.Catalog.brands.firstOrNull { (_, keys) -> keys.any { lower.contains(it) } }?.first.orEmpty()
}
