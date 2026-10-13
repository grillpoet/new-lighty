package com.grillpoet.lighty.core

import java.io.File

interface SourceDirectoryProvider {
    fun sourcesDir(): File
}