package compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.URI;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.JavaFileManager;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeJavaCompiler {
   private final JavaCompiler compiler;
   private final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

   public RuntimeJavaCompiler() {
      this.compiler = ToolProvider.getSystemJavaCompiler();

      if (this.compiler == null) {
         throw new RuntimeException("Seems that JDK not installed. Try to install JDK or add to classpath tools.jar");
      }
   }

   public Object compile(String className, String source) throws CompileException, ClassNotFoundException,
   	  InstantiationException, IllegalAccessException {
      final InMemoryFileManager fileManager = new InMemoryFileManager(compiler);
      final List<JavaFileObject> files = new ArrayList<JavaFileObject>();

      files.add(new SourceFile(className, source));
      CompilationTask task = compiler.getTask(null, fileManager, this.diagnostics, null, null, files);

      boolean result = task.call();
      if (!result) {
         throw new CompileException(getFormatedError());
      }

      ClassLoader loader = new CustomLoader(this.getClass().getClassLoader(),
						fileManager.getObjectFiles());
      return loader.loadClass(className).newInstance();
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

   class OutputFile extends SimpleJavaFileObject {
       private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

       public OutputFile(String name, Kind kind) {
	   super(URI.create("memo:///" + name.replace('.', '/') + kind.extension), kind);
       }

       public byte[] toByteArray() {
	   return this.stream.toByteArray();
       }

       @Override
       public ByteArrayOutputStream openOutputStream() {
	   return this.stream;
       }
   }

   class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
       private Map<String, OutputFile> outputFiles = new HashMap<String, OutputFile>();

       public InMemoryFileManager(JavaCompiler compiler) {
	   super((StandardJavaFileManager)compiler.getStandardFileManager(null, null, null));
       }

       public Map<String, OutputFile> getObjectFiles() {
	   return new HashMap<String, OutputFile>(outputFiles);
       }

       @Override
       public OutputFile getJavaFileForOutput(Location location, String name, Kind kind, FileObject source) {
	   OutputFile output = new OutputFile(name, kind);
	   this.outputFiles.put(name, output);
	   return output;
       }
   }

   class CustomLoader extends ClassLoader {
       private final Map<String, OutputFile> objectFiles;

       public CustomLoader(ClassLoader parent, Map<String, OutputFile> objectFiles) {
	   super(parent);
	   this.objectFiles = objectFiles;
       }

       @Override
       protected Class findClass(String name) throws ClassNotFoundException {
	   OutputFile obj =  this.objectFiles.remove(name);
	   if (obj != null) {
	       byte[] array = obj.toByteArray();
	       return defineClass(name, array, 0, array.length);
	   }

	   return super.findClass(name);
       }
   }

}
