package com.plexo.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {

    private lateinit var connectivityManager: ConnectivityManager
    @Volatile private var wifiNetwork: Network? = null
    @Volatile private var cellNetwork: Network? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PlexoScreen()
            }
        }
    }

    @Composable
    fun PlexoScreen() {
        var urlInput by remember { mutableStateOf("") }
        var wifiReady by remember { mutableStateOf(false) }
        var cellReady by remember { mutableStateOf(false) }
        var wifiSpeedText by remember { mutableStateOf("0 KB/s") }
        var cellSpeedText by remember { mutableStateOf("0 KB/s") }
        var totalSpeedText by remember { mutableStateOf("0 KB/s") }
        var progress by remember { mutableFloatStateOf(0f) }
        var statusText by remember { mutableStateOf("Networks connect karne ke liye niche button dabayein") }
        var isDownloading by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚡ Plexo Mobile",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Wi-Fi + 5G/4G Multi-Network Downloader",
                fontSize = 14.sp,
                color = Color.Gray
            )

            // Network Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "📶 Wi-Fi: ${if (wifiReady) "Connected" else "Waiting..."}  |  Speed: $wifiSpeedText",
                        color = if (wifiReady) Color(0xFF4CAF50) else Color.LightGray
                    )
                    Text(
                        text = "📱 Mobile Data: ${if (cellReady) "Connected" else "Waiting..."}  |  Speed: $cellSpeedText",
                        color = if (cellReady) Color(0xFF2196F3) else Color.LightGray
                    )
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "🚀 Combined Speed: $totalSpeedText",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC107)
                    )
                }
            }

            // Button to Bind Both Networks
            Button(
                onClick = {
                    bindNetworks(
                        onWifi = { wifiReady = true },
                        onCell = { cellReady = true }
                    )
                    statusText = "Wi-Fi aur Mobile Data bind ho rahe hain..."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. Activate Wi-Fi + Mobile Data Together")
            }

            // URL or Magnet Input
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Direct File URL ya Magnet Link dalein") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = Color(0xFF00E676),
                trackColor = Color.DarkGray
            )

            Text(
                text = "${(progress * 100).toInt()}%  •  $statusText",
                color = Color.White,
                fontSize = 14.sp
            )

            // Start Download Button
            Button(
                enabled = !isDownloading && urlInput.isNotBlank(),
                onClick = {
                    isDownloading = true
                    if (urlInput.startsWith("magnet:?")) {
                        statusText = "Magnet/Torrent multi-network engine start ho raha hai..."
                        TorrentHelper.startMagnetDownload(
                            context = this@MainActivity,
                            magnetUri = urlInput,
                            wifiNet = wifiNetwork,
                            cellNet = cellNetwork,
                            onProgress = { pct, speed ->
                                progress = pct
                                totalSpeedText = speed
                            },
                            onComplete = {
                                statusText = "Torrent Download Complete! (Downloads folder mein save hua)"
                                isDownloading = false
                            }
                        )
                    } else {
                        scope.launch {
                            startDualHttpDownload(
                                fileUrl = urlInput,
                                onSpeedUpdate = { wSpeed, cSpeed, totSpeed, pct ->
                                    wifiSpeedText = wSpeed
                                    cellSpeedText = cSpeed
                                    totalSpeedText = totSpeed
                                    progress = pct
                                },
                                onStatus = { msg -> statusText = msg },
                                onDone = { isDownloading = false }
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Start Boosted Download", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun bindNetworks(onWifi: () -> Unit, onCell: () -> Unit) {
        val wifiReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cellReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.requestNetwork(wifiReq, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiNetwork = network
                onWifi()
            }
        })

        connectivityManager.requestNetwork(cellReq, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cellNetwork = network
                onCell()
            }
        })
    }

    private suspend fun startDualHttpDownload(
        fileUrl: String,
        onSpeedUpdate: (String, String, String, Float) -> Unit,
        onStatus: (String) -> Unit,
        onDone: () -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val networks = listOfNotNull(wifiNetwork, cellNetwork)
            if (networks.isEmpty()) {
                onStatus("Error: Pehle Step 1 button daba kar network activate karein!")
                onDone()
                return@withContext
            }

            onStatus("File size check ho raha hai...")
            val headConn = networks.first().openConnection(URL(fileUrl)) as HttpURLConnection
            headConn.requestMethod = "HEAD"
            val totalSize = headConn.contentLengthLong
            headConn.disconnect()

            if (totalSize <= 0) {
                onStatus("Server parallel chunks support nahi karta ya link galat hai.")
                onDone()
                return@withContext
            }

            val fileName = fileUrl.substringAfterLast("/").ifBlank { "plexo_file.bin" }
            val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputFile = File(outDir, fileName)
            RandomAccessFile(outputFile, "rw").use { it.setLength(totalSize) }

            val wifiBytes = AtomicLong(0)
            val cellBytes = AtomicLong(0)
            val totalDownloaded = AtomicLong(0)
            var isRunning = true

            // Live Speed Meter Coroutine (Har 1 second mein speed calculate karega)
            val meterJob = launch {
                var lastWifi = 0L
                var lastCell = 0L
                while (isRunning) {
                    delay(1000)
                    val curWifi = wifiBytes.get()
                    val curCell = cellBytes.get()
                    val wDiff = curWifi - lastWifi
                    val cDiff = curCell - lastCell
                    lastWifi = curWifi
                    lastCell = curCell

                    val pct = totalDownloaded.get().toFloat() / totalSize.toFloat()
                    onSpeedUpdate(
                        formatSpeed(wDiff),
                        formatSpeed(cDiff),
                        formatSpeed(wDiff + cDiff),
                        pct.coerceIn(0f, 1f)
                    )
                }
            }

            onStatus("Downloading via ${networks.size} networks in 8 parallel chunks...")
            val chunks = 8
            val chunkSize = totalSize / chunks

            val jobs = (0 until chunks).map { index ->
                async(Dispatchers.IO) {
                    val start = index * chunkSize
                    val end = if (index == chunks - 1) totalSize - 1 else (start + chunkSize - 1)
                    val useWifi = (index % 2 == 0 && wifiNetwork != null) || cellNetwork == null
                    val net = if (useWifi) wifiNetwork!! else cellNetwork!!

                    val conn = net.openConnection(URL(fileUrl)) as HttpURLConnection
                    conn.setRequestProperty("Range", "bytes=$start-$end")
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000

                    conn.inputStream.use { input ->
                        RandomAccessFile(outputFile, "rw").use { raf ->
                            raf.seek(start)
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                raf.write(buf, 0, read)
                                totalDownloaded.addAndGet(read.toLong())
                                if (useWifi) wifiBytes.addAndGet(read.toLong())
                                else cellBytes.addAndGet(read.toLong())
                            }
                        }
                    }
                    conn.disconnect()
                }
            }

            jobs.awaitAll()
            isRunning = false
            meterJob.cancel()
            onSpeedUpdate("0 KB/s", "0 KB/s", "0 KB/s", 1f)
            onStatus("Download Complete! Saved in Downloads/$fileName")
        } catch (e: Exception) {
            onStatus("Error: ${e.localizedMessage}")
        } finally {
            onDone()
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec >= 1024 * 1024) {
            String.format("%.2f MB/s", bytesPerSec / (1024f * 1024f))
        } else {
            String.format("%d KB/s", bytesPerSec / 1024)
        }
    }
}
