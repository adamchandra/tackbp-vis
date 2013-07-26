package net.openreview.scalate

import org.fusesource.scalate._

object BindingHelpers extends BindingHelpers

trait BindingHelpers {
  implicit def b2ops(b:Binding) = new BindOps(b)

  class BindOps(b:Binding) {
    def withImportedMembers: Binding = b.copy(importMembers=true)
    def makeImplicit: Binding = b.copy(isImplicit=true)
    def withClassName(n:String): Binding = b.copy(className=n)
    def withClassType[T:Manifest]: Binding = b.copy(className=manifest[T].erasure.getName)
  }


  def binding[T : Manifest](name: String, importMems: Boolean, defaultValue: Option[String], kind: String, isImplicit:Boolean): Binding = {
        Binding(
          name                   = name,
          className              = manifest[T].erasure.getName,
          importMembers          = importMems,  
          defaultValue           = defaultValue,
          kind                   = kind,     
          isImplicit             = isImplicit
        )
  }

  def binding[T : Manifest](name: String): Binding = 
    binding[T](name, false, None, "val", false)

}
