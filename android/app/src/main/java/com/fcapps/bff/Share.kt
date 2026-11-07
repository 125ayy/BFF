package com.fcapps.bff

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.util.Locale

/** The share sheet: WhatsApp gets the APK plus a link; everything else gets the link. */
object Share {

    private fun shareText(ctx: Context): String {
        val pt = Locale.getDefault().language == "pt"
        return ctx.getString(if (pt) R.string.share_text_pt else R.string.share_text_en)
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
        try {
            val apk = File(ctx.applicationInfo.sourceDir)
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val out = File(dir, "BestFoneFinder.apk")
            apk.copyTo(out, overwrite = true)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText(ctx))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val wa = Intent(i).setPackage("com.whatsapp")
            try {
                ctx.startActivity(wa)
            } catch (e: Exception) {
                ctx.startActivity(Intent.createChooser(i, "Share the BFF app"))
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not attach the app; sharing the link", Toast.LENGTH_SHORT).show()
            shareLink(ctx)
        }
    }
}
