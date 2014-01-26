package test.scala

import java.io.{ StringWriter, PrintWriter }
import compiler._
import org.scalatest.FlatSpec

class SimpleClassSpec extends FlatSpec {
   
   def emitValidSource(className: String): String = {
      val writer = new StringWriter
      val output = new PrintWriter(writer)
      
      output.println("public class " + className + " {")
      output.println("public void apply() { System.out.println(\"Called from: " + className + "\"); }" )
      output.println("}")

      output.close
      writer.toString
   }

   "A Java Runtime Compiler class" should "compile simple class without error" in {
         val compiler = new RuntimeJavaCompiler
         val className = "SimpleClass"
         val src = emitValidSource(className)
         val obj = compiler.compile(className, src)
         assert(obj != null)
      }

      it should "throw Exception if an error occur" in {
         intercept[Exception] {
         }
      }
}
