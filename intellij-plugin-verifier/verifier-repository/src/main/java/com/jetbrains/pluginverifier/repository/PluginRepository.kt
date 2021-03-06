/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.repository

import com.jetbrains.plugin.structure.intellij.version.IdeVersion

/**
 * Represents API of the plugin repository.
 */
interface PluginRepository {

  /**
   * Name of the repository that can be shown to users.
   */
  val presentableName: String

  /**
   * Returns the latest plugins' versions compatible with [ideVersion].
   */
  fun getLastCompatiblePlugins(ideVersion: IdeVersion): List<PluginInfo>

  /**
   * Returns the last version of the plugin with ID equal to [pluginId]
   * compatible with [ideVersion].
   */
  fun getLastCompatibleVersionOfPlugin(ideVersion: IdeVersion, pluginId: String): PluginInfo?

  /**
   * Returns all versions of the plugin with ID equal to [pluginId].
   */
  fun getAllVersionsOfPlugin(pluginId: String): List<PluginInfo>

  /**
   * Given the [moduleId] returns the ID of the plugin that
   * declares this module.
   */
  fun getIdOfPluginDeclaringModule(moduleId: String): String?

}