package org.foldercamera

import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Tests choose their own base directory, so the first-launch system folder picker must not cover the app. */
class FolderCameraTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        targetContext.getSharedPreferences("configuration", Context.MODE_PRIVATE).edit().putBoolean("folderPrompted", true).commit()
        super.onCreate(arguments)
    }
}
