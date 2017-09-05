package edu.isnap.javaparser;

import java.io.File;
import java.io.FileNotFoundException;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithIdentifier;

public class JavaNodeParser {

    public static void main(String[] args) throws FileNotFoundException {

    	CompilationUnit cu = JavaParser.parse(
    			new File("../iSnap/src/edu/isnap/hint/util/Spreadsheet.java"));

    	recurse(cu, 0);

    	System.out.println(cu.getMetaModel());

    }

    static void recurse(Node node, int stack) {
    	String indent = "";
    	for (int i = 0; i < stack; i++) indent += "  ";
    	System.out.printf("%s%s: %s\n", indent, getType(node), getName(node));
    	for (Node n : node.getChildNodes()) {
    		if (ignore(n)) continue;
    		recurse(n, stack + 1);
    	}
    }

    static String getType(Node node) {
    	return node.getClass().getSimpleName();
    }

    static String getName(Node node) {
    	if (node instanceof NodeWithIdentifier) {
    		return ((NodeWithIdentifier<?>) node).getIdentifier();
//    	} else if (node instanceof FieldDeclaration) {
//    		return ((FieldDeclaration) node).getModifiers().toString();
    	} else if (node instanceof LiteralExpr) {
    		return node.toString();
    	} else if (node.getChildNodesByType(SimpleName.class).size() == 1) {
    		return node.getChildNodesByType(SimpleName.class).get(0).getIdentifier();
    	} else if (getSimpleName(node) != null) {
    		return getSimpleName(node).getIdentifier();
    	}
    	return "";
    }

    static boolean ignore(Node node) {
    	return
    			(node instanceof SimpleName &&
					getSimpleName(node.getParentNode().orElse(null)) == node) ||
    			node instanceof PackageDeclaration ||
    			node instanceof ImportDeclaration;
    }

    static SimpleName getSimpleName(Node node) {
    	if (node == null) return null;
    	SimpleName name = null;
    	for (Node n : node.getChildNodes()) {
    		if (n instanceof SimpleName) {
    			if (name == null) {
    				name = (SimpleName) n;
    			} else {
    				return null;
    			}
    		}
    	}
    	return name;
    }
}
