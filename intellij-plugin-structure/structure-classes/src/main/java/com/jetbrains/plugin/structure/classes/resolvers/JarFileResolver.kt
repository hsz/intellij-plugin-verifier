/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.classes.resolvers

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.classes.utils.AsmUtil
import com.jetbrains.plugin.structure.classes.utils.getBundleBaseName
import com.jetbrains.plugin.structure.classes.utils.getBundleNameByBundlePath
import org.objectweb.asm.tree.ClassNode
import java.nio.channels.ClosedChannelException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class JarFileResolver(
  private val jarPath: Path,
  override val readMode: ReadMode,
  private val fileOrigin: FileOrigin
) : Resolver() {

  private companion object {
    private const val CLASS_SUFFIX = ".class"

    private const val PROPERTIES_SUFFIX = ".properties"

    private const val SERVICE_PROVIDERS_PREFIX = "META-INF/services/"
  }

  private val classes: MutableSet<String> = hashSetOf()

  private val packageSet = PackageSet()

  private val bundleNames = hashMapOf<String, MutableSet<String>>()

  private val serviceProviders: MutableMap<String, Set<String>> = hashMapOf()

  private val zipFs: FileSystem

  private val zipRoot: Path

  @Volatile
  private var closeStacktrace: Throwable? = null

  private val isClosed = AtomicBoolean()

  init {
    require(jarPath.exists()) { "File does not exist: $jarPath" }
    require(jarPath.simpleName.endsWith(".jar") || jarPath.simpleName.endsWith(".zip")) { "File is neither a .jar nor .zip archive: $jarPath" }
    zipFs = FileSystems.newFileSystem(jarPath, JarFileResolver::class.java.classLoader)
    zipRoot = zipFs.rootDirectories.single()
    readClassNamesAndServiceProviders()
  }

  private fun readClassNamesAndServiceProviders() {
    val visitedDirs = hashSetOf<Path>()
    Files.walkFileTree(zipRoot, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {
        if (!visitedDirs.add(dir)) {
          return FileVisitResult.SKIP_SUBTREE
        }
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
        val entryName = getPathInJar(file)
        when {
          entryName.endsWith(CLASS_SUFFIX) -> {
            val className = entryName.substringBeforeLast(CLASS_SUFFIX)
            classes.add(className)
            packageSet.addPackagesOfClass(className)
          }
          entryName.endsWith(PROPERTIES_SUFFIX) -> {
            val fullBundleName = getBundleNameByBundlePath(entryName)
            bundleNames.getOrPut(getBundleBaseName(fullBundleName)) { hashSetOf() } += fullBundleName
          }
          entryName.startsWith(SERVICE_PROVIDERS_PREFIX) && entryName.count { it == '/' } == 2 -> {
            val serviceProvider = entryName.substringAfter(SERVICE_PROVIDERS_PREFIX)
            serviceProviders[serviceProvider] = readServiceImplementationNames(serviceProvider)
          }
        }
        return FileVisitResult.CONTINUE
      }
    })
  }

  private fun getPathInJar(entry: Path): String = zipRoot.relativize(entry).toString().toSystemIndependentName()

  private fun readServiceImplementationNames(serviceProvider: String): Set<String> {
    val entry = SERVICE_PROVIDERS_PREFIX + serviceProvider
    val entryPath = zipFs.getPath(entry)
    if (!entryPath.exists()) {
      return emptySet()
    }
    return entryPath.readLines().map { it.substringBefore("#").trim() }.filterNotTo(hashSetOf()) { it.isEmpty() }
  }

  val implementedServiceProviders: Map<String, Set<String>>
    get() = serviceProviders

  override val allPackages
    get() = packageSet.getAllPackages()

  override val allBundleNameSet: ResourceBundleNameSet
    get() = ResourceBundleNameSet(bundleNames)

  override val allClasses
    get() = classes

  override fun processAllClasses(processor: (ResolutionResult<ClassNode>) -> Boolean): Boolean {
    checkIsOpen()
    Files.walk(zipRoot).use { stream ->
      for (zipEntry in stream.filter { it.simpleName.endsWith(CLASS_SUFFIX) }) {
        val className = getPathInJar(zipEntry).removeSuffix(CLASS_SUFFIX)
        val result = readClass(className, zipEntry)
        if (!processor(result)) {
          return false
        }
      }
    }
    return true
  }

  override fun containsClass(className: String) = className in classes

  override fun containsPackage(packageName: String) = packageSet.containsPackage(packageName)

  override fun resolveClass(className: String): ResolutionResult<ClassNode> {
    checkIsOpen()
    if (className !in classes) {
      return ResolutionResult.NotFound
    }
    val classPath = zipRoot.resolve(className + CLASS_SUFFIX)
    if (!classPath.exists()) {
      ResolutionResult.NotFound
    }
    return readClass(className, classPath)
  }

  override fun resolveExactPropertyResourceBundle(baseName: String, locale: Locale): ResolutionResult<PropertyResourceBundle> {
    if (baseName !in bundleNames) {
      return ResolutionResult.NotFound
    }

    val control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES)
    val bundleName = control.toBundleName(baseName, locale)

    val resourceName = control.toResourceName(bundleName, "properties")
    val propertyResourceBundle = try {
      readPropertyResourceBundle(resourceName)
    } catch (e: IllegalArgumentException) {
      return ResolutionResult.Invalid(e.message ?: e.javaClass.name)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      return ResolutionResult.FailedToRead(e.message ?: e.javaClass.name)
    }

    if (propertyResourceBundle != null) {
      return ResolutionResult.Found(propertyResourceBundle, fileOrigin)
    }

    return ResolutionResult.NotFound
  }

  private fun readPropertyResourceBundle(bundleResourceName: String): PropertyResourceBundle? {
    checkIsOpen()
    val path = zipRoot.resolve(bundleResourceName)
    if (!path.exists()) {
      return null
    }
    return path.inputStream().use { PropertyResourceBundle(it) }
  }

  private fun readClass(className: String, classPath: Path): ResolutionResult<ClassNode> {
    return try {
      val classNode = classPath.inputStream().use {
        AsmUtil.readClassNode(className, it, readMode == ReadMode.FULL)
      }
      ResolutionResult.Found(classNode, fileOrigin)
    } catch (e: InvalidClassFileException) {
      ResolutionResult.Invalid(e.message)
    } catch (e: Exception) {
      e.rethrowIfInterrupted()
      if (e is ClosedChannelException) {
        val exception = IllegalStateException("ClosedChannelException for $className of $this from $classPath", e)
        closeStacktrace?.let { exception.addSuppressed(it) }
        throw exception
      }
      ResolutionResult.FailedToRead(e.message ?: e.javaClass.name)
    }
  }

  private fun checkIsOpen() {
    check(zipFs.isOpen) { "Jar file system must be open for $this" }
  }

  override fun close() {
    if (!isClosed.compareAndSet(false, true)) {
      throw IllegalStateException("This resolver is already closed: $this")
    }
    zipFs.close()
    closeStacktrace = RuntimeException()
  }

  override fun toString() = jarPath.toAbsolutePath().toString()

}

fun buildJarOrZipFileResolvers(
  jarsOrZips: Iterable<Path>,
  readMode: Resolver.ReadMode,
  parentOrigin: FileOrigin
): List<Resolver> {
  val resolvers = arrayListOf<Resolver>()
  resolvers.closeOnException {
    jarsOrZips.mapTo(resolvers) { file ->
      val fileOrigin = JarOrZipFileOrigin(file.simpleName, parentOrigin)
      JarFileResolver(file, readMode, fileOrigin)
    }
  }
  return resolvers
}