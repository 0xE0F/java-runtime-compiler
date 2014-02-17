package compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.net.URI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject.Kind;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class RuntimeJavaCompiler {
	private final JavaCompiler compiler;
	private final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
	private final List<String> options = new ArrayList<String>();

	/* Public API */
	/* ===========*/

	public RuntimeJavaCompiler(Iterable<String> options) {
		this.compiler = ToolProvider.getSystemJavaCompiler();

		if (this.compiler == null) {
			throw new RuntimeException("Seems that JDK not installed. Try to install JDK or add to classpath tools.jar");
		}

		for (String each: options) {
			this.options.add(each);
		}
	}

	public RuntimeJavaCompiler() {
		this(new ArrayList<String>());
	}

	public Object compileToObject(String className, String source) throws CompileException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		return compile(className, source, this.options).newInstance();
	}

	public Object compileToObjectWithCtor(String className, String source, Iterable<? extends Object> args) throws
			CompileException, InstantiationException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException,
			InvocationTargetException {
		Class<?> type = compile(className, source, this.options);
		Constructor<?> ctor = type.getConstructor(makeCtorParams(args));
		return ctor.newInstance(args);
	}

	public Object compileToObjectWithDependency(String className, String source, Iterable<? extends Class> dependencies) throws CompileException,
			ClassNotFoundException, InstantiationException, InvocationTargetException, IllegalAccessException {
		return compile(className, source, appendClassPathToOptions(dependencies)).newInstance();
	}

	public Object compileToObject(String className, String source, Iterable<? extends Class> dependencies, Iterable<? extends Object> args) throws
			CompileException, InstantiationException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException,
			InvocationTargetException {
		Class<?> type = compile(className, source, appendClassPathToOptions(dependencies));
		Constructor<?> ctor = type.getConstructor(makeCtorParams(args));
		return ctor.newInstance(args);
	}

	public Class compileToType(String className, String source) throws CompileException, ClassNotFoundException {
		return compile(className, source, this.options);
	}

	public Class compileToType(String className, String source, Iterable<? extends Class> dependencies) throws CompileException, ClassNotFoundException {
		return compile(className, source, appendClassPathToOptions(dependencies));
	}

	private Class[] makeCtorParams(Iterable<? extends Object> args) {
		List<Class> types = new ArrayList<Class>();
		for (Object each: args) {
			if (each == null)
				throw new IllegalArgumentException("Null arguments is not allowed");
			types.add(each.getClass());
		}

		return (Class[])types.toArray();
	}

	private Class compile(String className, String source, Iterable<String> options) throws CompileException, ClassNotFoundException {
		final InMemoryFileManager fileManager = new InMemoryFileManager(compiler);
		final List<JavaFileObject> files = new ArrayList<JavaFileObject>();

		files.add(new SourceFile(className, source));
		CompilationTask task = compiler.getTask(null, fileManager, this.diagnostics, options, null, files);

		boolean result = task.call();
		if (!result) {
			throw new CompileException(getFormatedError());
		}

		ClassLoader loader = new CustomLoader(this.getClass().getClassLoader(),
												fileManager.getObjectFiles());
		try {
			fileManager.close();
		} catch (IOException ex) {
			// Here ? In Memory  Ops ?
			ex.printStackTrace(System.err);
		}
		return loader.loadClass(className);
	}

	private Iterable<String> appendClassPathToOptions(Iterable<? extends Class> dependencies)
	{
		final String cpName = "-cp";
		final String classPathName = "-classpath";
		final Iterable<String> paths = getUniqPaths(dependencies);
		List<String> result = new ArrayList<String>(options);

		int idx = options.indexOf(cpName);
		if (idx < 0) {
			idx = options.indexOf(classPathName);
			if (idx < 0) {
				// Just append
				result.add(cpName);
				result.add(makeClassPath(null, paths));
				return result;
			}
		}

		if ((idx+1) == options.size()) {
			result.add(cpName);
			result.add(makeClassPath(null, paths));
			return result;
		}

		final String originalPath = options.get(idx+1);
		result.set(idx+1, makeClassPath(originalPath, paths));

		return result;
	}

	private Iterable<String> getUniqPaths(Iterable<? extends Class> dependencies)
	{
		Set<String> result = new HashSet<String>();

		for (Class each : dependencies) {
			result.add(each.getProtectionDomain().getCodeSource().getLocation().toString());
		}

		return result;
	}

	private String makeClassPath(String original, Iterable<String> paths)
	{
		final String pathDelim = ":";
		StringBuilder sb = new StringBuilder(original);

		for (String each : paths) {
			if (sb.length() > 0)
			sb.append(pathDelim);
			sb.append(each);
		}

		return sb.toString();
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
