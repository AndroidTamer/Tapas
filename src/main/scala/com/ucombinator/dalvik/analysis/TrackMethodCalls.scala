package com.ucombinator.dalvik.analysis

import xml.{XML, NodeSeq}
import com.ucombinator.dalvik.AST._


/** Class for performing abstract evaluation on a single method.
  *
  * @constructor creates a new method abstract evaluator for the specified
  *              method of the specified class.
  * @param clazz the class containing the method.
  * @param method the method defintion to be analyzed.
  */
class MethodAbstractEval(clazz: ClassDef, method: MethodDef) {
  
  /** A simple method to process the bytecode of a method looking for the set
    * of methods that can be called from this method.
    *
    * This procedure does not perform any abstract evaluation, it is here
    * mostly to serve as a first pass approximation of what we want this
    * analzyer to do.
    *
    * @return returns the set of method definitions that could be called here.
    */
  def gatherCalledMethods: Set[Method] = {
    if (method.code == null || method.code.insns == null) {
      Set.empty[Method]
    } else {
      method.code.insns.foldLeft(Set.empty[Method]) {
        (set, insn) => insn match {
          case InvokeSuper(args, b)          => set + b
          case InvokeDirect(args, b)         => set + b
          case InvokeStatic(args, b)         => set + b
          case InvokeInterface(args, b)      => set + b
          case InvokeVirtual(args, b)        => set + b
          case InvokeVirtualRange(c, a, b)   => set + b
          case InvokeSuperRange(c, a, b)     => set + b
          case InvokeDirectRange(c, a, b)    => set + b
          case InvokeStaticRange(c, a, b)    => set + b
          case InvokeInterfaceRange(c, a, b) => set + b
          case _                             => set
        }
      }
    }
  }
}

class SourceSinkMethodCallAnalyzer(fn: String, clazzes: Array[ClassDef]) {
  /* The following list comes from a standard list of sources and sinks.  Some
   * of these seem less like a source or sink procedure, then it does a
   * combination of source and sink methods with constructors for objects that
   * are associated with some (but not all) of them.  Also note, that this list
   * does not differentiate the method by type, only by name.  This is based on
   * the simplifying assumption that if one version of the method leaks, all of
   * the methods of this name leak. */

  val xmlFile = XML.loadFile(fn)
  private def buildMap(root: NodeSeq): Map[String,Array[String]] = {
    root.foldLeft(Map.empty[String,Array[String]]) {
      (m, node) => (node \ "method").foldLeft(m) {
        (m, node) => {
          val clazz = "L" + (node \ "@class-name").toString.replace(".", "/") + ";"
          val methodName = (node \ "@name").toString
          if (m.contains(clazz)) {
             val entries = m(clazz)
             if (entries.contains(methodName))
                m
             else
                m + (clazz -> (entries :+ methodName))
          } else {
            m + (clazz -> Array(methodName))
          }
        }
      }
    }
  }
 
  val knownSources = buildMap(xmlFile \ "sources")
  val knownSinks = buildMap(xmlFile \ "sinks")
  val knownOther = buildMap(xmlFile \ "other")

  var _sources = Set.empty[MethodDef]
  var _sinks   = Set.empty[MethodDef]
  var _other   = Set.empty[MethodDef]
  var classMap = clazzes.foldLeft(Map.empty[String,ClassDef]) { (m, cd) => m + (cd.name -> cd) }

  for (cd <- clazzes) {
    for (md <- cd.methods) {
      new MethodAbstractEval(cd, md).gatherCalledMethods foreach {
        (m) => {
           if (knownSources isDefinedAt m.className) {
             val knownSourceMethods = knownSources(m.className)
             if (knownSourceMethods contains m.name)
               _sources += md
           }
           if (knownSinks isDefinedAt m.className) {
             val knownSinkMethods = knownSinks(m.className)
             if (knownSinkMethods contains m.name)
               _sinks += md
           }
           if (knownOther isDefinedAt m.className) {
             val knownOtherMethods = knownOther(m.className)
             if (knownOtherMethods contains m.name)
               _other += md
           }
        }
      }
    }
  }

  def sources = _sources
  def sinks   = _sinks
  def other   = _other
}

class MethodCallAnalyzer(clazzes: Array[ClassDef]) {
  var classMap = Map.empty[String,Map[String,Option[Set[Method]]]]

  private def doAnalysis(className: String, methodName: String): Option[Set[Method]] = {
    clazzes find { (cd) => cd.name == className } match {
      case Some(classDef) =>
        classDef.methods find { (md) => md.name == methodName } match {
          case Some(methodDef) => {
            val evaluator = new MethodAbstractEval(classDef, methodDef)
            Some(evaluator.gatherCalledMethods)
          }
          case None => None
        }
      case None => None
    }
  }
  
  def lookupMethods(className: String, methodName: String): Option[Set[Method]] = {
    if (classMap isDefinedAt className) {
      val methodMap = classMap(className)
      if (methodMap isDefinedAt methodName) {
        methodMap(methodName)
      } else {
        val methods = doAnalysis(className, methodName)
        classMap = classMap.updated(className, classMap(className) + (methodName -> methods))
        methods
      }
    } else {
      val methods = doAnalysis(className, methodName)
      classMap += className -> Map(methodName -> methods)
      methods
    }
  }
}
