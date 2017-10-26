package com.jetbrains.pluginverifier.tests.dependencies

import com.jetbrains.plugin.structure.intellij.plugin.PluginDependencyImpl
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.dependencies.MissingDependency
import com.jetbrains.pluginverifier.dependencies.graph.DepEdge
import com.jetbrains.pluginverifier.dependencies.graph.DepGraph2ApiGraphConverter
import com.jetbrains.pluginverifier.dependencies.graph.DepGraphBuilder
import com.jetbrains.pluginverifier.dependencies.graph.DepVertex
import com.jetbrains.pluginverifier.dependencies.resolution.IdeDependencyFinder
import com.jetbrains.pluginverifier.plugin.PluginDetails
import com.jetbrains.pluginverifier.plugin.PluginDetailsProviderImpl
import com.jetbrains.pluginverifier.repository.DownloadPluginResult
import com.jetbrains.pluginverifier.repository.UpdateInfo
import com.jetbrains.pluginverifier.tests.mocks.MockIde
import com.jetbrains.pluginverifier.tests.mocks.MockIdePlugin
import com.jetbrains.pluginverifier.tests.mocks.MockPluginRepositoryAdapter
import org.jgrapht.DirectedGraph
import org.jgrapht.graph.DefaultDirectedGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * @author Sergey Patrikeev
 */
class IdeDependencyFinderTest {

  @JvmField
  @Rule
  var tempFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun `get all plugin transitive dependencies`() {
    /*
    Given following dependencies between plugins:

    `test` -> `someModule` (defined in `moduleContainer`)
    `test` -> `somePlugin`

    `myPlugin` -> `test`
    `myPlugin` -> `externalModule` (defined in external plugin `externalPlugin` which is impossible to download)
    `myPlugin` -> `com.intellij.modules.platform` (default module)

    Should find dependencies on `test`, `somePlugin`, `moduleContainer`. Dependency on `com.intellij.modules.platform` must not be indicated.
    Dependency resolution on `externalPlugin` must fail.
     */
    val testPlugin = MockIdePlugin(
        pluginId = "test",
        pluginVersion = "1.0",
        dependencies = listOf(PluginDependencyImpl("someModule", false, true), PluginDependencyImpl("somePlugin", false, false))
    )
    val somePlugin = MockIdePlugin(
        pluginId = "somePlugin",
        pluginVersion = "1.0"
    )
    val moduleContainer = MockIdePlugin(
        pluginId = "moduleContainer",
        pluginVersion = "1.0",
        definedModules = setOf("someModule")
    )

    val ide = MockIde(IdeVersion.createIdeVersion("IU-144"), bundledPlugins = listOf(testPlugin, somePlugin, moduleContainer))

    val externalModuleDependency = PluginDependencyImpl("externalModule", false, true)
    val startPlugin = MockIdePlugin(
        pluginId = "myPlugin",
        pluginVersion = "1.0",
        dependencies = listOf(PluginDependencyImpl("test", true, false), externalModuleDependency, PluginDependencyImpl("com.intellij.modules.platform", false, true))
    )

    val repository = object : MockPluginRepositoryAdapter() {
      override fun getIdOfPluginDeclaringModule(moduleId: String): String? {
        return if (moduleId == "externalModule") "externalPlugin" else null
      }

      override fun getLastCompatibleUpdateOfPlugin(ideVersion: IdeVersion, pluginId: String): UpdateInfo? {
        return if (pluginId == "externalPlugin") UpdateInfo(pluginId, pluginId, "1.0", 0, null) else null
      }

      override fun downloadPluginFile(update: UpdateInfo): DownloadPluginResult {
        return DownloadPluginResult.FailedToDownload("Failed to download test.")
      }
    }

    val pluginDetailsProvider = PluginDetailsProviderImpl(File("."))
    val ideDependencyResolver = IdeDependencyFinder(ide, repository, pluginDetailsProvider)

    val start = DepVertex("myPlugin", PluginDetails.FoundOpenPluginWithoutClasses(startPlugin))
    val graph: DirectedGraph<DepVertex, DepEdge> = DefaultDirectedGraph(DepEdge::class.java)
    val depGraphBuilder = DepGraphBuilder(ideDependencyResolver)
    depGraphBuilder.fillDependenciesGraph(start, graph)

    val dependenciesGraph = DepGraph2ApiGraphConverter().convert(graph, start)
    val deps = dependenciesGraph.vertices.map { it.id }
    assertEquals(setOf("myPlugin", "test", "somePlugin", "moduleContainer"), deps.toSet())

    assertEquals(listOf(MissingDependency(externalModuleDependency, "Failed to download test.")), dependenciesGraph.start.missingDependencies)
    assertTrue(dependenciesGraph.getMissingDependencyPaths().size == 1)
  }

}
