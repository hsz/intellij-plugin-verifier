package com.jetbrains.pluginverifier.dependencies

import com.intellij.structure.plugin.Plugin
import com.intellij.structure.plugin.PluginDependency
import com.intellij.structure.resolvers.Resolver
import com.jetbrains.pluginverifier.dependency.DependencyResolver
import com.jetbrains.pluginverifier.misc.closeLogged
import org.jgrapht.DirectedGraph
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable

data class DepEdge(val dependency: PluginDependency, val isModule: Boolean) : DefaultEdge() {
  public override fun getTarget(): Any = super.getTarget()

  public override fun getSource(): Any = super.getSource()
}

data class DepVertex(val id: String, val resolveResult: DependencyResolver.Result) : Closeable {
  override fun equals(other: Any?): Boolean = other is DepVertex && id == other.id

  override fun hashCode(): Int = id.hashCode()

  override fun close() = resolveResult.close()

}

class DepGraphBuilder(private val dependencyResolver: DependencyResolver) : Closeable {
  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DepGraphBuilder::class.java)
  }

  private val graph: DirectedGraph<DepVertex, DepEdge> = DefaultDirectedGraph(DepEdge::class.java)

  fun build(plugin: Plugin, resolver: Resolver): Pair<DirectedGraph<DepVertex, DepEdge>, DepVertex> {
    LOG.debug("Building dependencies graph for $plugin")
    val startResult = DependencyResolver.Result.FoundReady(plugin, resolver)
    val startVertex = DepVertex(plugin.pluginId ?: "", startResult)
    traverseDependencies(startVertex)
    return graph to startVertex
  }

  override fun close() = graph.vertexSet().forEach { it.closeLogged() }

  private fun findDependencyOrFillMissingReason(pluginDependency: PluginDependency, isModule: Boolean): DepVertex? =
      getAlreadyResolvedDependency(pluginDependency) ?: resolveDependency(isModule, pluginDependency)

  private fun getAlreadyResolvedDependency(pluginDependency: PluginDependency): DepVertex? =
      graph.vertexSet().find { pluginDependency.id == it.id }

  private fun resolveDependency(isModule: Boolean, pluginDependency: PluginDependency): DepVertex {
    val resolved = dependencyResolver.resolve(pluginDependency, isModule)
    return DepVertex(pluginDependency.id, resolved)
  }

  private fun getPlugin(resolveResult: DependencyResolver.Result): Plugin? = when (resolveResult) {
    is DependencyResolver.Result.FoundReady -> resolveResult.plugin
    is DependencyResolver.Result.CreatedResolver -> resolveResult.plugin
    is DependencyResolver.Result.Downloaded -> resolveResult.plugin
    is DependencyResolver.Result.ProblematicDependency -> null
    is DependencyResolver.Result.NotFound -> null
    DependencyResolver.Result.Skip -> null
  }

  private fun traverseDependencies(current: DepVertex) {
    if (graph.containsVertex(current)) {
      return
    }
    graph.addVertex(current)
    val plugin = getPlugin(current.resolveResult) ?: return
    for (pluginDependency in plugin.moduleDependencies + plugin.dependencies) {
      val isModule = pluginDependency in plugin.moduleDependencies
      val dependency = findDependencyOrFillMissingReason(pluginDependency, isModule) ?: continue
      traverseDependencies(dependency)
      graph.addEdge(current, dependency, DepEdge(pluginDependency, isModule))
    }
  }

}