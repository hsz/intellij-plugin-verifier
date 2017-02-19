package org.jetbrains.plugins.verifier.service.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.annotations.SerializedName
import com.jetbrains.pluginverifier.api.PluginDescriptor
import com.jetbrains.pluginverifier.format.UpdateInfo
import com.jetbrains.pluginverifier.persistence.GsonHolder
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.jetbrains.plugins.verifier.service.api.Result
import org.jetbrains.plugins.verifier.service.api.TaskId
import org.jetbrains.plugins.verifier.service.api.TaskStatus
import org.jetbrains.plugins.verifier.service.core.TaskManager
import org.jetbrains.plugins.verifier.service.runners.ExtractFeaturesRunner
import org.jetbrains.plugins.verifier.service.runners.FeaturesResult
import org.jetbrains.plugins.verifier.service.setting.Settings
import org.jetbrains.plugins.verifier.service.util.executeSuccessfully
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Patrikeev
 */
object FeatureService {

  private val LOG: Logger = LoggerFactory.getLogger(FeatureService::class.java)

  private val featuresExtractor: FeaturesApi = Retrofit.Builder()
      .baseUrl(Settings.FEATURE_EXTRACTOR_REPOSITORY_URL.get())
      .addConverterFactory(GsonConverterFactory.create(GsonHolder.GSON))
      .client(makeClient(LOG.isDebugEnabled))
      .build()
      .create(FeaturesApi::class.java)

  fun run() {
    Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("feature-service-%d")
            .build()
    ).scheduleAtFixedRate({ tick() }, 0, SERVICE_PERIOD, TimeUnit.MINUTES)
  }

  //5 minutes
  private const val SERVICE_PERIOD: Long = 5

  private val inProgressUpdates: MutableMap<UpdateInfo, TaskId> = hashMapOf()

  private val lastCheckDate: MutableMap<UpdateInfo, Long> = hashMapOf()

  //10 minutes
  private const val UPDATE_PROCESS_MIN_PAUSE_MILLIS = 10 * 60 * 1000

  private val userName: String by lazy {
    Settings.PLUGIN_REPOSITORY_VERIFIER_USERNAME.get()
  }

  private val password: String by lazy {
    Settings.PLUGIN_REPOSITORY_VERIFIER_PASSWORD.get()
  }

  private var isRequesting: Boolean = false

  private fun isServerTooBusy(): Boolean {
    val runningNumber = TaskManager.runningTasksNumber()
    if (runningNumber >= TaskManager.MAX_RUNNING_TASKS) {
      LOG.info("There are too many running tasks $runningNumber >= ${TaskManager.MAX_RUNNING_TASKS}")
      return true
    }
    return false
  }

  @Synchronized
  fun tick() {
    LOG.info("It's time to extract more plugins!")

    if (isServerTooBusy()) return

    if (isRequesting) {
      LOG.info("The server is already requesting new plugins list")
      return
    }

    isRequesting = true

    try {
      for (it in getUpdatesToExtract().updateIds) {
        if (isServerTooBusy()) {
          return
        }
        schedule(it)
      }

    } catch (e: Exception) {
      LOG.error("Failed to schedule updates check", e)
    } finally {
      isRequesting = false
    }
  }

  private fun schedule(updateId: Int) {
    val info = UpdateInfoCache.getUpdateInfo(updateId) ?: return
    schedule(info)
  }

  private fun schedule(updateInfo: UpdateInfo) {
    if (updateInfo in inProgressUpdates) {
      LOG.debug("Update $updateInfo is currently in progress; ignore it")
      return
    }

    val lastCheck = lastCheckDate[updateInfo]
    if (lastCheck != null && System.currentTimeMillis() - lastCheck < UPDATE_PROCESS_MIN_PAUSE_MILLIS) {
      LOG.info("Update $updateInfo was checked recently; wait at least ${UPDATE_PROCESS_MIN_PAUSE_MILLIS / 1000} seconds;")
      return
    }

    val runner = ExtractFeaturesRunner(PluginDescriptor.ByUpdateInfo(updateInfo))
    val taskId = TaskManager.enqueue(
        runner,
        { onSuccess(it) },
        { t, tid, task -> logError(t, tid, task as ExtractFeaturesRunner) },
        { tst, task -> onUpdateExtracted(task as ExtractFeaturesRunner) }
    )
    inProgressUpdates[updateInfo] = taskId
    lastCheckDate[updateInfo] = System.currentTimeMillis()
    LOG.info("Extract features of $updateInfo is scheduled with taskId #$taskId")
  }

  private fun onUpdateExtracted(task: ExtractFeaturesRunner) {
    val updateInfo = (task.pluginDescriptor as PluginDescriptor.ByUpdateInfo).updateInfo
    releaseUpdate(updateInfo)
  }

  @Synchronized
  private fun releaseUpdate(updateInfo: UpdateInfo) {
    LOG.info("Update $updateInfo is successfully extracted")
    inProgressUpdates.remove(updateInfo)
  }


  private fun logError(throwable: Throwable, taskStatus: TaskStatus, task: ExtractFeaturesRunner) {
    val updateInfo = (task.pluginDescriptor as PluginDescriptor.ByUpdateInfo).updateInfo
    LOG.error("Unable to extract features of the update $updateInfo: taskId = #${taskStatus.taskId}", throwable)
  }

  private fun onSuccess(result: Result<FeaturesResult>) {
    val results = result.result!!
    LOG.info("Update ${results.plugin} is successfully processed. Result type = ${results.resultType}; Extracted = ${results.features.size} " +
        (if (results.badPlugin != null) "bad plugin = ${results.badPlugin}; " else "") +
        "in ${result.taskStatus.elapsedTime() / 1000} s")

    try {
      sendExtractedFeatures(results, userName, password).executeSuccessfully()
    } catch(e: Exception) {
      LOG.error("Unable to send check result of the plugin ${results.plugin}", e)
    }
  }


  private fun getUpdatesToExtract(): UpdatesToExtractFeatures {
    val updates = getUpdatesToExtractFeatures(userName, password).executeSuccessfully().body()
    LOG.info("Repository get updates to extract features success: (total: ${updates.updateIds.size}): $updates")
    return updates
  }

  private fun getUpdatesToExtractFeatures(userName: String, password: String) =
      featuresExtractor.getUpdatesToExtractFeatures(createStringRequestBody(userName), createStringRequestBody(password))


  private fun sendExtractedFeatures(extractedFeatures: FeaturesResult, userName: String, password: String) =
      featuresExtractor.sendExtractedFeatures(extractedFeatures, createStringRequestBody(userName), createStringRequestBody(password))

}

data class UpdatesToExtractFeatures(@SerializedName("updateIds") val updateIds: List<Int>)

interface FeaturesApi {

  @Multipart
  @POST("/feature/getUpdatesToExtractFeatures")
  fun getUpdatesToExtractFeatures(@Part("userName") userName: RequestBody,
                                  @Part("password") password: RequestBody): Call<UpdatesToExtractFeatures>

  @Multipart
  @POST("/feature/receiveExtractedFeatures")
  fun sendExtractedFeatures(@Part("extractedFeatures") extractedFeatures: FeaturesResult,
                            @Part("userName") userName: RequestBody,
                            @Part("password") password: RequestBody): Call<ResponseBody>

}