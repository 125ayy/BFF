package com.fcapps.bff

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The share sheet: WhatsApp gets the APK plus a link; everything else gets the link. */
object Share {

    private fun shareText(ctx: Context): String {
        return ctx.getString(R.string.share_text)
    }

    fun open(ctx: Context) {
        val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.sheet_share, null)
        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(view)
        view.findViewById<android.view.View>(R.id.optWhatsApp).setOnClickListener { dialog.dismiss(); shareApkToWhatsApp(ctx) }
        view.findViewById<android.view.View>(R.id.optOther).setOnClickListener { dialog.dismiss(); shareLink(ctx) }
        view.findViewById<android.view.View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun shareLink(ctx: Context) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText(ctx))
        }
        ctx.startActivity(Intent.createChooser(i, "Share BFF"))
    }

    /** Copy our own APK to the FileProvider cache and send it, with the link as the caption, to WhatsApp. */
    private fun shareApkToWhatsApp(ctx: Context) {
        val activity = ctx as? ComponentActivity ?: run { shareLink(ctx); return }
        if (!ctx.applicationInfo.splitSourceDirs.isNullOrEmpty()) {
            Toast.makeText(ctx, R.string.share_split_link, Toast.LENGTH_SHORT).show()
            shareLink(ctx)
            return
        }
        activity.lifecycleScope.launch {
            try {
                val out = withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
                    val target = File(dir, "BestFoneFinder-${System.nanoTime()}.apk")
                    File(ctx.applicationInfo.sourceDir).copyTo(target, overwrite = false)
                }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, shareText(ctx))
                    clipData = android.content.ClipData.newRawUri("BFF APK", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                ctx.startActivity(intent)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                shareLink(ctx)
            }
        }
    }
}
