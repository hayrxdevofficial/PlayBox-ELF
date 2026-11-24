package com.playbox.elf

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private var selectedUri: android.net.Uri? = null
    private var selectedPackageUri: android.net.Uri? = null
    private lateinit var fileLabel: TextView
    private lateinit var packageLabel: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var ramSpinner: Spinner
    private lateinit var diskSpinner: Spinner
    private lateinit var resultView: TextView
    private lateinit var playButton: Button
    private val ramOptions = listOf("128 MB", "256 MB", "512 MB", "1 GB", "2 GB", "4 GB", "6 GB")
    private val diskOptions = listOf("1 GB", "4 GB", "8 GB", "16 GB", "32 GB", "70 GB")

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            fileLabel.text = uri.lastPathSegment?.substringAfterLast('/') ?: "ELF selected"
            resultView.text = "ELF selected. Choose RAM and storage, then press PLAY."
            playButton.isEnabled = true
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        imagePreview.setImageBitmap(bitmap)
                        resultView.text = "Image loaded: ${bitmap.width} × ${bitmap.height}"
                    } else resultView.text = "Could not decode this image."
                }
            } catch (e: Exception) {
                resultView.text = "Image error: ${e.message ?: "unknown error"}"
            }
        }
    }

    private val packagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedPackageUri = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            packageLabel.text = uri.lastPathSegment?.substringAfterLast('/') ?: "Package selected"
            val name = packageLabel.text.toString().lowercase()
            resultView.text = when {
                name.endsWith(".tuns") -> ".tuns application package selected. Package installation support is being developed."
                name.endsWith(".game.surb") -> ".game.surb game package selected. Game download/install support is being developed."
                else -> "Select a file ending in .tuns or .game.surb."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("playbox")
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(12, 16, 28))
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(text("PLAYBOX", 32, Color.WHITE, true))
        body.addView(text("ELF CONSOLE • VERSION 0.2.0", 12, Color.rgb(95, 210, 255), true))

        imagePreview = ImageView(this).apply {
            setImageBitmap(createSplash())
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "PlayBox startup splash image"
        }
        body.addView(imagePreview, LinearLayout.LayoutParams(-1, dp(150)))
        body.addView(button("CHOOSE PNG / JPEG / WEBP IMAGE") {
            imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
        })
        body.addView(space(18))

        body.addView(text("GAME / ELF FILE", 13, Color.LTGRAY, true))
        fileLabel = text("No ELF selected", 15, Color.WHITE, false)
        body.addView(fileLabel)
        body.addView(button("SELECT ELF FILE") { picker.launch(arrayOf("*/*")) })
        body.addView(space(14))

        body.addView(text("PLAYBOX PACKAGES", 13, Color.LTGRAY, true))
        packageLabel = text("No .tuns / .game.surb package selected", 13, Color.WHITE, false)
        body.addView(packageLabel)
        body.addView(button("SELECT .TUNS / .GAME.SURB") { packagePicker.launch(arrayOf("*/*")) })
        body.addView(text(".tuns = PlayBox applications • .game.surb = PlayBox game packages. Import/install is not active yet.", 11, Color.GRAY, false))
        body.addView(space(18))

        body.addView(text("VIRTUAL RAM", 13, Color.LTGRAY, true))
        ramSpinner = spinner(ramOptions); body.addView(ramSpinner)
        body.addView(space(12))
        body.addView(text("VIRTUAL STORAGE CAPACITY", 13, Color.LTGRAY, true))
        diskSpinner = spinner(diskOptions); body.addView(diskSpinner)
        body.addView(text("Storage is currently a capacity setting; virtual disk read/write is not implemented yet.", 11, Color.GRAY, false))
        body.addView(space(20))

        playButton = button("PLAY ELF") { runElf() }.apply { isEnabled = false }
        body.addView(playButton)
        body.addView(space(16))
        body.addView(text("EMULATOR OUTPUT", 13, Color.LTGRAY, true))
        resultView = text("Ready. Select a compatible ELF64 executable.", 14, Color.rgb(190, 205, 225), false)
        resultView.setPadding(14, 16, 14, 16)
        resultView.setBackgroundColor(Color.rgb(24, 31, 48))
        body.addView(resultView)
        root.addView(text("Experimental x86-64 ELF test emulator. Not a PlayStation firmware or game compatibility layer.", 11, Color.GRAY, false))
        setContentView(root)
    }

    private fun createSplash(): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(9, 15, 31))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 160, 220) }
        canvas.drawRoundRect(26f, 26f, 614f, 274f, 30f, 30f, paint)
        paint.color = Color.rgb(10, 24, 46)
        canvas.drawRoundRect(38f, 38f, 602f, 262f, 24f, 24f, paint)
        paint.color = Color.rgb(80, 220, 255)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        paint.textSize = 86f
        canvas.drawText("PLAYBOX", 320f, 158f, paint)
        paint.color = Color.WHITE
        paint.textSize = 25f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText("YOUR ELF PLAYGROUND", 320f, 207f, paint)
        return bitmap
    }

    private fun runElf() {
        val uri = selectedUri ?: return
        playButton.isEnabled = false
        resultView.text = "Reading ELF file…"
        Thread {
            val result = try {
                val input = contentResolver.openInputStream(uri) ?: error("Cannot open selected file")
                val output = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(65536)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 128 * 1024 * 1024) error("ELF exceeds the current 128 MiB file limit")
                        output.write(buffer, 0, count)
                    }
                }
                val ramMb = when (ramSpinner.selectedItem.toString()) {
                    "128 MB" -> 128; "256 MB" -> 256; "512 MB" -> 512; "1 GB" -> 1024
                    "2 GB" -> 2048; "4 GB" -> 4096; else -> 6144
                }
                val disk = diskSpinner.selectedItem.toString()
                nativeRunElf(output.toByteArray(), ramMb) + "\nVirtual storage setting: $disk (capacity only for now)."
            } catch (e: Exception) {
                "Could not run ELF: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread { resultView.text = result; playButton.isEnabled = true }
        }.start()
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setPadding(8, 8, 8, 8)
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 5, 0, 5)
    }
    private fun button(value: String, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { click() }
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private external fun nativeRunElf(bytes: ByteArray, ramMb: Int): String
}
