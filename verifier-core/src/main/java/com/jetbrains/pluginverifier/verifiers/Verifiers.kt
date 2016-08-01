package com.jetbrains.pluginverifier.verifiers

import com.intellij.structure.resolvers.Resolver
import com.jetbrains.pluginverifier.api.VContext
import com.jetbrains.pluginverifier.verifiers.clazz.AbstractMethodVerifier
import com.jetbrains.pluginverifier.verifiers.clazz.InheritFromFinalClassVerifier
import com.jetbrains.pluginverifier.verifiers.clazz.InterfacesVerifier
import com.jetbrains.pluginverifier.verifiers.clazz.SuperClassVerifier
import com.jetbrains.pluginverifier.verifiers.field.FieldTypeVerifier
import com.jetbrains.pluginverifier.verifiers.field.FieldVerifier
import com.jetbrains.pluginverifier.verifiers.instruction.*
import com.jetbrains.pluginverifier.verifiers.method.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

internal object ReferencesVerifier {

  private val fieldVerifiers = arrayOf<FieldVerifier>(FieldTypeVerifier())

  private val classVerifiers = arrayOf(
      SuperClassVerifier(),
      InterfacesVerifier(),
      AbstractMethodVerifier(),
      InheritFromFinalClassVerifier()
  )

  private val methodVerifiers = arrayOf(
      OverrideNonFinalVerifier(),
      MethodReturnTypeVerifier(),
      MethodArgumentTypesVerifier(),
      MethodLocalVarsVerifier(),
      MethodThrowsVerifier(),
      MethodTryCatchVerifier()
  )

  private val instructionVerifiers = arrayOf(
      InvokeInstructionVerifier(),
      TypeInstructionVerifier(),
      LdcInstructionVerifier(),
      MultiANewArrayInstructionVerifier(),
      FieldAccessInstructionVerifier(),
      InvokeDynamicVerifier()
  )

  /**
   * @throws InterruptedException if the verification was cancelled
   */
  @Throws(InterruptedException::class)
  fun verify(ctx: VContext, checkClasses: Resolver, classLoader: Resolver) {
    checkClasses.allClasses.forEach {
      if (Thread.currentThread().isInterrupted) {
        throw InterruptedException("The verification was cancelled")
      }
      val node = checkClasses.findClass(it)
      if (node != null) {
        verifyClass(classLoader, node, ctx)
      }
    }
  }

  @SuppressWarnings("unchecked")
  private fun verifyClass(classLoader: Resolver, node: ClassNode, ctx: VContext) {
    for (verifier in classVerifiers) {
      verifier.verify(node, classLoader, ctx)
    }

    @Suppress("UNCHECKED_CAST")
    val methods = node.methods as List<MethodNode>
    for (method in methods) {
      for (verifier in methodVerifiers) {
        verifier.verify(node, method, classLoader, ctx)
      }

      val instructions = method.instructions
      val i = instructions.iterator()
      while (i.hasNext()) {
        val instruction = i.next()
        for (verifier in instructionVerifiers) {
          verifier.verify(node, method, instruction as AbstractInsnNode, classLoader, ctx)
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    val fields = node.fields as List<FieldNode>
    for (field in fields) {
      for (verifier in fieldVerifiers) {
        verifier.verify(node, field, classLoader, ctx)
      }
    }
  }

}