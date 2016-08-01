package com.jetbrains.pluginverifier.configurations

import com.intellij.structure.domain.IdeVersion
import com.intellij.structure.resolvers.Resolver
import com.jetbrains.pluginverifier.api.*
import com.jetbrains.pluginverifier.repository.RepositoryManager
import com.jetbrains.pluginverifier.utils.CmdOpts
import com.jetbrains.pluginverifier.utils.CmdUtil
import com.jetbrains.pluginverifier.utils.VOptionsUtil
import java.io.File
import java.io.IOException

object CheckPluginParamsParser : ParamsParser {

  override fun parse(opts: CmdOpts, freeArgs: List<String>): CheckPluginParams {
    if (freeArgs.size <= 1) {
      throw RuntimeException("You must specify plugin to check and IDE(s), example:\n" +
          "java -jar verifier.jar check-plugin ~/work/myPlugin/myPlugin.zip ~/EAPs/idea-IU-117.963\n" +
          "java -jar verifier.jar check-plugin #14986 ~/EAPs/idea-IU-117.963")
    }
    val ideDescriptors = freeArgs.drop(1).map { File(it) }.map { IdeDescriptor.ByInstance(CmdUtil.createIde(it, opts)) }
    val pluginFiles = getPluginFiles(freeArgs[0], ideDescriptors.map { it.ideVersion })
    val jdkDescriptor = JdkDescriptor.ByFile(CmdUtil.getJdkDir(opts))
    val vOptions = VOptionsUtil.parseOpts(opts)
    val externalClasspath = CmdUtil.getExternalClassPath(opts)
    return CheckPluginParams(pluginFiles.map { PluginDescriptor.ByFile("${it.nameWithoutExtension}", "", it) }, ideDescriptors, jdkDescriptor, vOptions, true, externalClasspath)
  }

  fun getPluginFiles(pluginToTestArg: String, ideVersions: List<IdeVersion>? = null): List<File> {
    if (pluginToTestArg.startsWith("@")) {
      val pluginListFile = File(pluginToTestArg.substring(1))
      val pluginPaths = pluginListFile.readLines()
      return ideVersions!!.map { fetchPlugins(it, pluginListFile, pluginPaths) }.flatten()
    } else if (pluginToTestArg.matches("#\\d+".toRegex())) {
      val pluginId = pluginToTestArg.substring(1)
      try {
        val updateId = Integer.parseInt(pluginId)
        val pluginFile = RepositoryManager.getInstance().getPluginFile(updateId) ?: throw RuntimeException("No such plugin $pluginToTestArg")
        return listOf(pluginFile)
      } catch (e: IOException) {
        throw RuntimeException("Cannot load plugin #" + pluginId, e)
      }
    } else {
      val file = File(pluginToTestArg)
      if (!file.exists()) {
        throw IllegalArgumentException("The file $file doesn't exist")
      }
      return listOf(file)
    }
  }

  fun fetchPlugins(ideVersion: IdeVersion, pluginListFile: File, pluginPaths: List<String>): List<File> =
      pluginPaths
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .map {
            if (it.startsWith("id:")) {
              downloadPluginBuilds(it.substringAfter("id:"), ideVersion)
            } else {
              var pluginFile = File(it)
              if (!pluginFile.isAbsolute) {
                pluginFile = File(pluginListFile.parentFile, it)
              }
              if (!pluginFile.exists()) {
                throw RuntimeException("Plugin file '" + it + "' specified in '" + pluginListFile.absolutePath + "' doesn't exist")
              }
              listOf(pluginFile)
            }
          }.flatten()

  fun downloadPluginBuilds(pluginId: String, ideVersion: IdeVersion): List<File> =
      RepositoryManager.getInstance()
          .getAllCompatibleUpdatesOfPlugin(ideVersion, pluginId)
          .map { RepositoryManager.getInstance().getPluginFile(it)!! }


}

data class CheckPluginParams(val pluginDescriptors: List<PluginDescriptor>,
                             val ideDescriptors: List<IdeDescriptor>,
                             val jdkDescriptor: JdkDescriptor,
                             val vOptions: VOptions,
                             val resolveDependenciesWithin: Boolean = false,
                             val externalClasspath: Resolver = Resolver.getEmptyResolver(),
                             val progress: VProgress = DefaultVProgress()) : Params