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
         assert(false)
         val compiler = new JavaCompiler
         val src = emitValidSource("SimpleCase")
         val obj = compiler.compile(src)
         assert(obj != null)
      }

      it should "throw Exception if an error occur" in {
         intercept[Exception] {
         }
      }
}
