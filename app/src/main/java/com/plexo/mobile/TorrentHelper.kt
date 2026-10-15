package com.plexo.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.net.Inet4Address

object TorrentHelper {

    fun startMagnetDownload(
        context: Context,
        magnetUri: String,
        wifiNet: Network?,
        cellNet: Network?,
        onProgress: (Float, String) -> Unit,
        onComplete: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // Step 1: Wi-Fi aur Mobile Data dono ke local IP addresses nikalna
            val ipList = mutableListOf<String>()
            listOfNotNull(wifiNet, cellNet).forEach { net ->
                val linkProps = cm.getLinkProperties(net)
                linkProps?.linkAddresses?.forEach { linkAddr ->
                    val addr = linkAddr.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        ipList.add("${addr.hostAddress}:6881")
                    }
                }
            }

            // Step 2: Libtorrent ko dono network interfaces par listen karne ke liye set karna
            val sp = SettingsPack()
            if (ipList.isNotEmpty()) {
                sp.setString(
                    settings_pack.string_types.listen_interfaces.swigValue(),
                    ipList.joinToString(",")
                )
            }

            val session = SessionManager()
            session.start(SessionParams(sp))

            val saveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            
            // Magnet metadata fetch karna (30 sec timeout)
            val torrentData = session.fetchMagnet(magnetUri, 30, saveDir)
            if (torrentData != null) {
                val torrentFile = File(saveDir, "temp.torrent")
                torrentFile.writeBytes(torrentData)
                session.download(org.libtorrent4j.TorrentInfo(torrentFile), saveDir)

                // Live progress monitor karna
                while (session.isRunning) {
                    delay(1000)
                    val rate = session.downloadRate()
                    val speedStr = if (rate >= 1024 * 1024) {
                        String.format("%.2f MB/s", rate / (1024f * 1024f))
                    } else {
                        String.format("%d KB/s", rate / 1024)
                    }
                    onProgress(0.5f, speedStr) // Progress aur combined speed update
                }
            }
            session.stop()
            onComplete()
        }
    }
}
