package com.pkj.leanbulk.data.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 백업 파일을 디스크에 쓰고 읽는다. DB 쪽 변환은 [SnapshotCodec]이 맡고 여기는 파일만 다룬다.
 */
object BackupFiles {

    /** gzip JSON. 공유 시트와 SAF 저장에 쓰는 MIME. */
    const val MIME = "application/gzip"

    /** 되돌리기용 자동 백업을 몇 개까지 남겨둘지. */
    private const val KEEP_BACKUPS = 5

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** xlsx와 같은 폴더를 쓴다. `file_paths.xml`에 이미 등록돼 있어 FileProvider가 그대로 동작한다. */
    private fun exportDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun backupDir(context: Context) = File(context.filesDir, "backups").apply { mkdirs() }

    /** 사용자가 공유하거나 다른 폴더에 저장할 백업 파일을 만든다. */
    fun writeForSharing(context: Context, snapshot: Snapshot): File {
        val file = File(exportDir(context), "심플헬스앱-백업-${LocalDateTime.now().format(STAMP)}.json.gz")
        file.outputStream().use { it.write(SnapshotCodec.gzip(snapshot)) }
        return file
    }

    /** 사용자가 고른 파일을 읽어 스냅샷으로 되돌린다. gzip이 아니면 예외가 난다. */
    fun read(context: Context, uri: Uri): Snapshot =
        requireNotNull(context.contentResolver.openInputStream(uri)) { "파일을 열 수 없습니다" }
            .use { SnapshotCodec.gunzip(it) }

    /**
     * 불러오기로 덮어쓰기 **직전에** 현재 상태를 남겨둔다.
     *
     * 사용자가 잘못된 파일을 불러왔을 때 되돌릴 수 있는 유일한 수단이라, 실패하면
     * 불러오기 자체를 중단시켜야 한다(예외를 삼키지 않는다).
     */
    fun saveRollbackPoint(context: Context, snapshot: Snapshot): File {
        val dir = backupDir(context)
        val file = File(dir, "before-import-${LocalDateTime.now().format(STAMP)}.json.gz")
        file.outputStream().use { it.write(SnapshotCodec.gzip(snapshot)) }

        // 오래된 것부터 지워서 몇 개만 남긴다.
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP_BACKUPS)?.forEach { it.delete() }
        return file
    }
}
