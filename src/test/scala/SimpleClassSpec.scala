package test.scala

import java.io.{ StringWriter, PrintWriter }
import compiler._
import org.scalatest.FlatSpec

class SimpleClassSpec extends FlatSpec {
  var classCount = 0
  def generateClassName = {
    classCount = classCount + 1
    "testClass_" + classCount
  }

  def emitValidSource(className: String): String = {
    val builder = new StringBuilder
    builder.append("public class " + className + " {")
    builder.append("public void apply() { System.out.println(\"Called from: " + className + "\"); }")
    builder.append("}")
    builder.toString
  }

  def emitInvalidSource(className: String): String = {
    val builder = new StringBuilder
    builder.append("public clss " + className + " {")
    builder.append("public void apply() { System.out.println(\"Called from: " + className + "\"); }")
    builder.append("}")
    builder.toString
  }

  def emitSourceWithoutDefaultCtor(className: String): String = {
    val builder = new StringBuilder
    builder.append("public class " + className + " {")
    builder.append("private  " + className + "() { }")
    builder.append("public void " + className +"(String name, Integer i) { System.out.println(\"Called from: \" + name); }")
    builder.append("public void apply() { }")
    builder.append("}")
    builder.toString
  }

  def checkForThrowException[T<:Exception:Manifest](className: String, srcGen: String => String, ex: Class[T]) {
    intercept[T] {
	val compiler = new RuntimeJavaCompiler
      val obj = compiler.compileToObject(className, srcGen(className))
    }
  }

  "A Java Runtime Compiler class" should "compile simple class without error" in {
    val compiler = new RuntimeJavaCompiler
    val className = generateClassName
    val src = emitValidSource(className)
    val obj = compiler.compileToObject(className, src)
    assert(obj != null)
  }

  it should "throw CompileException if an syntax error occur" in {
    val className = generateClassName
    checkForThrowException(className, emitInvalidSource, classOf[CompileException])
  }

  it should "throw IllegalAccessException if access rigth invalid"  in {
    val className = generateClassName
    checkForThrowException(className, emitSourceWithoutDefaultCtor, classOf[IllegalAccessException])
  }

  it should "throw InstantiationException if appropriate constructor not found" in {
    intercept[InstantiationException] {
    }
  }

  it should "throw ClassNotFoundException if class can't be loaded" in {
		intercept[ClassNotFoundException] {
    }
  }


}
