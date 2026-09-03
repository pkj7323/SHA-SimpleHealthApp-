package com.pkj.leanbulk.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun contentUriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** 공유 시트(카톡/드라이브/AI 앱 등)로 파일을 보낸다. */
    fun shareIntentFor(
        context: Context,
        file: File,
        mime: String = XLSX_MIME,
        title: String = "xlsx 공유하기"
    ): Intent {
        val uri = contentUriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /** SAF로 사용자가 고른 폴더/파일 위치에 xlsx 바이트를 그대로 복사한다. */
    fun copyTo(context: Context, source: File, targetUri: Uri) {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out) }
        }
    }
}
