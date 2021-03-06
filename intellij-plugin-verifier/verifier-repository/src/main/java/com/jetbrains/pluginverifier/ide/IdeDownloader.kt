/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.ide

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.repository.downloader.DownloadResult
import com.jetbrains.pluginverifier.repository.downloader.Downloader
import com.jetbrains.pluginverifier.repository.downloader.UrlDownloader
import java.nio.file.Files
import java.nio.file.Path

/**
 * [Downloader] of the IDEs.
 */
class IdeDownloader : Downloader<AvailableIde> {

  private val urlDownloader = UrlDownloader<AvailableIde> { it.downloadUrl }

  @Throws(InterruptedException::class)
  override fun download(key: AvailableIde, tempDirectory: Path): DownloadResult {
    return try {
      downloadIde(key, key.version, tempDirectory)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      DownloadResult.FailedToDownload("Unable to download $key", e)
    }
  }

  private fun downloadIde(
    availableIde: AvailableIde,
    ideVersion: IdeVersion,
    tempDirectory: Path
  ) = with(urlDownloader.download(availableIde, tempDirectory)) {
    when (this) {
      is DownloadResult.Downloaded -> {
        try {
          extractIdeToTempDir(downloadedFileOrDirectory, tempDirectory)
        } finally {
          downloadedFileOrDirectory.deleteLogged()
        }
      }
      is DownloadResult.NotFound -> DownloadResult.NotFound("IDE $ideVersion is not found: $reason")
      is DownloadResult.FailedToDownload -> DownloadResult.FailedToDownload("Failed to download IDE $ideVersion: $reason", error)
    }
  }

  private fun extractIdeToTempDir(archivedIde: Path, tempDirectory: Path): DownloadResult {
    val destinationDir = Files.createTempDirectory(tempDirectory, "")
    return try {
      archivedIde.extractTo(destinationDir)
      /**
       * Some IDE builds (like MPS) are distributed in form
       * of `<build>.zip/<single>/...`
       * where the <single> is the only directory under .zip.
       */
      stripTopLevelDirectory(destinationDir)
      DownloadResult.Downloaded(destinationDir, "", true)
    } catch (e: Throwable) {
      destinationDir.deleteLogged()
      throw e
    }
  }

  companion object {
    /**
     * If the [directory] contains a single directory,
     * that directory will be truncated and all its
     * content will be moved one level up.
     *
     * If the only contained directory is empty, it will be removed.
     */
    fun stripTopLevelDirectory(directory: Path) {
      val entries = directory.listFiles()
      if (entries.size != 1) {
        return
      }

      val single = entries.single()
      if (!single.isDirectory) {
        return
      }

      val contents = single.listFiles()
      if (contents.isEmpty()) {
        Files.delete(single)
        return
      }

      var conflict: Path? = null
      for (from in contents) {
        if (from.simpleName == single.simpleName) {
          conflict = from
          continue
        }

        val to = directory.resolve(from.simpleName)
        Files.move(from, to)
      }

      if (conflict != null) {
        //Create a unique temporary name from the set of files.
        //This name will be used as a destination of a conflicting name.
        val uniqueTempName = contents.map { it.simpleName }.max()!! + ".temp"
        val tempDestination = directory.resolve(uniqueTempName)

        //Move conflict to unique location.
        require(!tempDestination.exists())
        Files.move(conflict, tempDestination)

        //Delete empty single
        require(single.listFiles().isEmpty())
        single.deleteLogged()

        Files.move(tempDestination, single)
      } else {
        single.deleteLogged()
      }
    }
  }

}