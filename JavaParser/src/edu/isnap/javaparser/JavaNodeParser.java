package edu.isnap.javaparser;

import java.io.File;
import java.io.FileNotFoundException;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

public class JavaNodeParser {

    public static void main(String[] args) throws FileNotFoundException {

    	CompilationUnit cu = JavaParser.parse(
    			new File("../iSnap/src/edu/isnap/hint/util/Spreadsheet.java"));

    	System.out.println(cu);
    }
}
