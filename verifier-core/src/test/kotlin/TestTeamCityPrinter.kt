import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.api.PluginInfo
import com.jetbrains.pluginverifier.api.Result
import com.jetbrains.pluginverifier.api.Verdict
import com.jetbrains.pluginverifier.dependencies.DependenciesGraph
import com.jetbrains.pluginverifier.dependencies.DependencyNode
import com.jetbrains.pluginverifier.output.PrinterOptions
import com.jetbrains.pluginverifier.output.TeamCityLog
import com.jetbrains.pluginverifier.output.TeamCityPrinter
import com.jetbrains.pluginverifier.repository.FileLock
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.repository.UpdateInfo
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream

/**
 * @author Sergey Patrikeev
 */
class TestTeamCityPrinter {

  private fun noConnectionPluginRepository() = object : PluginRepository {
    private fun noConnection(): Exception = IOException("no connection")

    override fun getLastCompatibleUpdates(ideVersion: IdeVersion): List<UpdateInfo> = throw noConnection()

    override fun getLastCompatibleUpdateOfPlugin(ideVersion: IdeVersion, pluginId: String): UpdateInfo? = throw noConnection()

    override fun getAllCompatibleUpdatesOfPlugin(ideVersion: IdeVersion, pluginId: String): List<UpdateInfo> = throw noConnection()

    override fun getPluginFile(update: UpdateInfo): FileLock? = throw noConnection()

    override fun getUpdateInfoById(updateId: Int): UpdateInfo = throw noConnection()

    override fun getIdOfPluginDeclaringModule(moduleId: String): String = throw noConnection()

    override fun getAllUpdatesOfPlugin(pluginId: String): List<UpdateInfo> = throw noConnection()
  }

  private fun mockRepository(updateInfos: List<UpdateInfo>) = object : PluginRepository {
    override fun getLastCompatibleUpdates(ideVersion: IdeVersion): List<UpdateInfo> = updateInfos

    override fun getLastCompatibleUpdateOfPlugin(ideVersion: IdeVersion, pluginId: String): UpdateInfo? = updateInfos.find { it.pluginId == pluginId }

    override fun getAllCompatibleUpdatesOfPlugin(ideVersion: IdeVersion, pluginId: String): List<UpdateInfo> = updateInfos.toList()

    override fun getPluginFile(update: UpdateInfo): FileLock? = throw UnsupportedOperationException()

    override fun getUpdateInfoById(updateId: Int): UpdateInfo = throw UnsupportedOperationException()

    override fun getIdOfPluginDeclaringModule(moduleId: String): String = throw UnsupportedOperationException()

    override fun getAllUpdatesOfPlugin(pluginId: String): List<UpdateInfo> = throw UnsupportedOperationException()
  }

  @Test
  fun `test newest suffix for updates with newest versions`() {
    val pluginInfos = listOf(PluginInfo("id", "version", UpdateInfo("id", "name", "version", 1, "")), PluginInfo("id", "version 2", UpdateInfo("id", "name", "version 2", 2, "")))
    val mockRepository = mockRepository(pluginInfos.map { it.updateInfo!! })
    val output = getTeamCityOutput(mockRepository, pluginInfos)
    Assert.assertEquals("""##teamcity[testSuiteStarted name='id']
##teamcity[testStarted name='(version)']
##teamcity[testFinished name='(version)']
##teamcity[testStarted name='(version 2 - newest)']
##teamcity[testFinished name='(version 2 - newest)']
##teamcity[testSuiteFinished name='id']
""", output)
  }

  @Test
  fun `no repository connection lead to no -newest suffix`() {
    val mockPluginRepository = noConnectionPluginRepository()
    val output = getTeamCityOutput(mockPluginRepository, listOf(PluginInfo("id", "v", UpdateInfo("id", "name", "v", 1, "vendor"))))
    Assert.assertEquals("""##teamcity[testSuiteStarted name='id']
##teamcity[testStarted name='(v)']
##teamcity[testFinished name='(v)']
##teamcity[testSuiteFinished name='id']
""", output)
  }

  private fun getTeamCityOutput(pluginRepository: PluginRepository, pluginInfos: List<PluginInfo>): String {
    val dependencyNode = DependencyNode("id", "version", emptyList())
    val output = ByteArrayOutputStream().use { bos ->
      PrintStream(bos, true, "utf-8").use { printStream ->
        val teamCityLog = TeamCityLog(printStream)
        val teamCityVPrinter = TeamCityPrinter(teamCityLog, TeamCityPrinter.GroupBy.BY_PLUGIN, pluginRepository)
        teamCityVPrinter.printResults(
            pluginInfos.map {
              Result(
                  it,
                  IdeVersion.createIdeVersion("IU-145"),
                  Verdict.OK(DependenciesGraph(dependencyNode, listOf(dependencyNode), emptyList()))
              )
            },
            PrinterOptions()
        )
      }
      bos.toString("utf-8")
    }
    return output
  }
}