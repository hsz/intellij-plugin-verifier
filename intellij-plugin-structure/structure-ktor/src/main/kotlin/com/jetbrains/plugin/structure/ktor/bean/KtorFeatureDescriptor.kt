/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.ktor.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KtorVendor(
  @SerialName(VENDOR_NAME)
  val name: String? = null,
  @SerialName(VENDOR_EMAIL)
  val vendorEmail: String? = null,
  @SerialName(VENDOR_URL)
  val vendorUrl: String? = null
)

@Serializable
data class KtorFeatureDocumentation(
  @SerialName(DOCUMENTATION_DESCRIPTION)
  val description: String? = null,
  @SerialName(DOCUMENTATION_USAGE)
  val usage: String? = null,
  @SerialName(DOCUMENTATION_OPTIONS)
  val options: String? = null
)

@Serializable
data class KtorFeatureDescriptor(
  @SerialName(ID)
  val pluginId: String? = null,
  @SerialName(NAME)
  val pluginName: String? = null,
  @SerialName(VERSION)
  val pluginVersion: String? = null,
  @SerialName(KTOR_VERSION)
  val ktorVersion: String? = null,
  @SerialName(SHORT_DESCRIPTION)
  val shortDescription: String? = null,
  @SerialName(COPYRIGHT)
  val copyright: String? = null,
  @SerialName(VENDOR)
  val vendor: KtorVendor? = null,
  @SerialName(REQUIRED_FEATURES)
  val requiredFeatures: List<String> = emptyList(), // Feature IDs.
  @SerialName(INSTALL_RECEIPT)
  val installRecipe: FeatureInstallRecipe? = null,
  @SerialName(GRADLE_INSTALL)
  val gradleInstall: GradleInstallRecipe? = null,
  @SerialName(MAVEN_INSTALL)
  val mavenInstall: MavenInstallRecipe? = null,
  @SerialName(DEPENDENCIES)
  val dependencies: List<BuildSystemDependency> = emptyList(),
  @SerialName(TEST_DEPENDENCIES)
  val testDependencies: List<BuildSystemDependency> = emptyList(),
  @SerialName(DOCUMENTATION)
  val documentation: KtorFeatureDocumentation? = null
)