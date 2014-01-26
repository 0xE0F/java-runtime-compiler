package compiler;

import javax.tools.DiagnosticCollector;

public class CompileException extends Exception {
   
   public CompileException(String msg) { super(msg); }

   public CompileException(String msg, Throwable cause) { super(msg, cause); }
   
   public CompileException(DiagnosticCollector diagnostics) {
   
   }
}
