/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository.files

/**
 * Represents possible outcomes of [fetching] [FileRepository.getFile] a file
 * from the [file repository] [FileRepository].
 */
sealed class FileRepositoryResult {
  /**
   * The file is fetched and a [file lock] [lockedFile] is registered for it.
   */
  data class Found(val lockedFile: FileLock) : FileRepositoryResult()

  /**
   * The file is not found due to [reason].
   */
  data class NotFound(val reason: String) : FileRepositoryResult()

  /**
   * The file is failed to be fetched due to [reason].
   * The exception thrown on fetching is [error].
   */
  data class Failed(val reason: String, val error: Exception) : FileRepositoryResult()
}