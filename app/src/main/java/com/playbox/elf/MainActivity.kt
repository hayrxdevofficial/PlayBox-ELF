package com.playbox.elf

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private var selectedUri: android.net.Uri? = null
    private lateinit var fileLabel: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("playbox")
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(12, 16, 28))
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(text("PLAYBOX", 32, Color.WHITE, true))
        body.addView(text("ELF CONSOLE • VERSION 0.1.0", 12, Color.rgb(95, 210, 255), true))
        body.addView(space(26))
        body.addView(text("GAME / ELF FILE", 13, Color.LTGRAY, true))
        fileLabel = text("No ELF selected", 15, Color.WHITE, false)
        body.addView(fileLabel)
        body.addView(button("SELECT ELF FILE") { picker.launch(arrayOf("*/*")) })
        body.addView(space(18))
        body.addView(text("VIRTUAL RAM", 13, Color.LTGRAY, true))
        ramSpinner = spinner(ramOptions); body.addView(ramSpinner)
        body.addView(space(14))
        body.addView(text("VIRTUAL STORAGE CAPACITY", 13, Color.LTGRAY, true))
        diskSpinner = spinner(diskOptions); body.addView(diskSpinner)
        body.addView(text("In v0.1 storage is a capacity setting only; disk read/write emulation comes later.", 12, Color.GRAY, false))
        body.addView(space(22))
        playButton = button("PLAY") { runElf() }.apply { isEnabled = false }
        body.addView(playButton)
        body.addView(space(18))
        body.addView(text("EMULATOR OUTPUT", 13, Color.LTGRAY, true))
        resultView = text("Ready. Select a compatible ELF64 executable.", 14, Color.rgb(190, 205, 225), false)
        resultView.setPadding(14, 16, 14, 16)
        resultView.setBackgroundColor(Color.rgb(24, 31, 48))
        body.addView(resultView)
        root.addView(text("Experimental x86-64 ELF test emulator. Not a PlayStation firmware or game compatibility layer.", 11, Color.GRAY, false))
        setContentView(root)
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
                nativeRunElf(output.toByteArray(), ramMb) + "\nVirtual storage setting: $disk (capacity only in v0.1)."
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
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 5, 0, 5)
    }
    private fun button(value: String, click: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { click() }
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, height) }
    private external fun nativeRunElf(bytes: ByteArray, ramMb: Int): String
}
