package compiler;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.URI;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class RuntimeJavaCompiler {
   private final JavaCompiler compiler;
   private final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

   public RuntimeJavaCompiler() {
      this.compiler = ToolProvider.getSystemJavaCompiler();

      if (this.compiler == null) {
         throw new RuntimeException("Seems that JDK not installed. Try to install JDK or add to classpath tools.jar");
      }
   }

   public Object compile(String className, String source) throws CompileException, ClassNotFoundException, InstantiationException,
          IllegalAccessException {
      final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
      final List<JavaFileObject> files = new ArrayList<JavaFileObject>();
      
      files.add(new SourceFile(className, source));
      CompilationTask task = compiler.getTask(null, fileManager, this.diagnostics, null, null, files);

      boolean result = task.call();
      if (!result) {
         throw new CompileException(getFormatedError());
      }

      return fileManager.getClassLoader(null).loadClass(className).newInstance();
   }

   public String getFormatedError() {
      final StringWriter writer = new StringWriter();
      final PrintWriter out = new PrintWriter(writer);
     
      out.println();
      int count = 1;
      for (Diagnostic each : this.diagnostics.getDiagnostics()) {
         out.println("Error #" + count);
         out.println("[Type : " + each.getCode() + ", Code: " + each.getKind()+ "]");
         out.println("Message: " + each.getMessage(null));
         count++;
      }

      out.close();
      return writer.toString();
   }

   class SourceFile extends SimpleJavaFileObject {
      private final String content;

      public SourceFile(String name, String content) {
         super(URI.create("memo:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
         this.content = content;
      }

      @Override
         public CharSequence getCharContent(boolean ignoreEncoding) {
            return this.content;
         }
   }
}
