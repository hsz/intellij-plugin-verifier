/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.base.problems

import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import org.apache.commons.io.FileUtils

abstract class PluginFileError : PluginProblem() {
  override val level
    get() = Level.ERROR
}

class IncorrectPluginFile(
  private val fileName: String,
  private val expectedFileType: String
) : PluginFileError() {
  override val message
    get() = "Incorrect plugin file $fileName. Must be a $expectedFileType"
}

class UnableToExtractZip : PluginFileError() {
  override val message
    get() = "Unable to extract plugin zip file"
}

class PluginFileSizeIsTooLarge(private val sizeLimit: Long) : PluginFileError() {
  override val message
    get() = "Plugin file size is too large. Maximum allowed size is " + FileUtils.byteCountToDisplaySize(sizeLimit)
}