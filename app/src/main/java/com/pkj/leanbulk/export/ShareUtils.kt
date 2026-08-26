package com.pkj.leanbulk.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun contentUriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** 공유 시트(카톡/드라이브/AI 앱 등)로 xlsx 파일을 보낸다. */
    fun shareIntentFor(context: Context, file: File): Intent {
        val uri = contentUriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "xlsx 공유하기")
    }

    /** SAF로 사용자가 고른 폴더/파일 위치에 xlsx 바이트를 그대로 복사한다. */
    fun copyTo(context: Context, source: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }
}
