package com.jetbrains.pluginverifier.results.problems

import com.jetbrains.pluginverifier.misc.formatMessage
import com.jetbrains.pluginverifier.results.access.AccessType
import com.jetbrains.pluginverifier.results.location.ClassLocation
import com.jetbrains.pluginverifier.results.location.Location
import com.jetbrains.pluginverifier.results.modifiers.Modifiers

data class IllegalClassAccessProblem(val unavailableClass: ClassLocation,
                                     val access: AccessType,
                                     val usage: Location) : Problem() {

  override val shortDescription = "Illegal access to {0} class {1}".formatMessage(access, unavailableClass)

  override val fullDescription: String
    get() {
      val type = if (unavailableClass.modifiers.contains(Modifiers.Modifier.INTERFACE)) "interface" else "class"
      return "{0} {1} {2} is not available at {3}. This can lead to **IllegalAccessError** exception at runtime.".formatMessage(access.toString().capitalize(), type, unavailableClass, usage)
    }
}