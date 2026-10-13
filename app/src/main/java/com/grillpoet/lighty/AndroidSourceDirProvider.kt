package com.grillpoet.lighty

import android.content.Context
import com.grillpoet.lighty.core.SourceDirectoryProvider
import java.io.File

class AndroidSourceDirProvider(
    private val context: Context
) : SourceDirectoryProvider {
    override fun sourcesDir(): File =
        File(context.filesDir, "sources").apply { mkdirs() }
}