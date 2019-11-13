package com.github.javaparser.javaparser_symbol_solver_core;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class VoidVisitorStarter {
	private static final String FILE_PATH = "/Users/rajatnarang/eclipse-workspace/javaparser-symbol-solver-core/src/main/java/com/github/javaparser/javaparser_symbol_solver_core/ReversePolishNotation.java";

    public static void main(String[] args) throws Exception {

        CompilationUnit cu = JavaParser.parse(new FileInputStream(FILE_PATH));
        VoidVisitor<?> methodNameVisitor = new MethodNamePrinter();
        if(1<2 && 5>4) {
        	System.out.println("Test Statement");
        }
        methodNameVisitor.visit(cu, null);
        List<String> methodNames = new ArrayList<>();
        VoidVisitor<List<String>> methodNameCollector = new MethodNameCollector();
        methodNameCollector.visit(cu, methodNames);
        for(String x: methodNames) {
        	System.out.println("Method Name Collected: " + x);
        }

        //cu.getClass().getMethods()[0].invoke(cu)
        //cu.getAllContainedComments()

    }

    private static class MethodNamePrinter extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(FieldDeclaration md, Void arg) {
            super.visit(md, arg);
            System.out.println("Method Name Printed: " + md.getAllContainedComments());
        }
    }

    private static class MethodNameCollector extends VoidVisitorAdapter<List<String>> {

        @Override
        public void visit(MethodDeclaration md, List<String> collector) {
            super.visit(md, collector);
            collector.add(md.getNameAsString());
        }
    }

}
