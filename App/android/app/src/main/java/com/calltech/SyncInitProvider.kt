package com.calltech

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/** Process start hote hi sync arm — app UI khole bina (install/boot/SMS/call). */
class SyncInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false

        try {
            CallSyncHelper.markBackgroundSyncEnabled(ctx, true)
            InstallFlow.runInitialSetup(ctx, "init_provider")
        } catch (error: Exception) {
            Log.e(TAG, "SyncInitProvider failed", error)
        }

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "SyncInitProvider"
    }
}
