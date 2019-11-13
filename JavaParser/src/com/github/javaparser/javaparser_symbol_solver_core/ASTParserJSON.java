package com.github.javaparser.javaparser_symbol_solver_core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithIdentifier;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import com.github.javaparser.metamodel.NodeMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;

public class ASTParserJSON {

	public static SimpleName getSimpleName(Node node) {
    	if (node == null) return null;
    	SimpleName name = null;
    	for (Node n : node.getChildNodes()) {
    		if (n instanceof SimpleName) {
    			if (name == null) {
    				name = (SimpleName) n;
    			}
    			else {
    				return null;
    			}
    		}
    	}
    	return name;
    }

	static boolean ignore(Node node) {
    	return
    			(node instanceof SimpleName &&
					getSimpleName(node.getParentNode().orElse(null)) == node) ||
    			node instanceof PackageDeclaration ||
    			node instanceof ImportDeclaration;
    }

	public static String getVal(Node node) {
		if (node instanceof NodeWithIdentifier) {
    		return ((NodeWithIdentifier<?>) node).getIdentifier();
    	}
		else if (node instanceof LiteralExpr) {
    		return node.toString();
    	}
    	else if (node.getChildNodesByType(SimpleName.class).size() == 1) {
    		return node.getChildNodesByType(SimpleName.class).get(0).getIdentifier();
    	}
    	else if (getSimpleName(node) != null) {
    		return getSimpleName(node).getIdentifier();
    	}
    	return "";
	}

	public static String toJSON(String source) {
		CompilationUnit cu = JavaParser.parse(source);
		ParsedNode parsed = parseAST(cu);
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static ParsedNode parseAST(Node node) {
		String[] methodsToIgnore = {"isVarArgs", "getType", "isInterface", "getIdentifier", "getValue"};

		// Associate start and end with each node
		int[] sourceStart = {};
		int[] sourceEnd = {};
		if(node instanceof NodeWithRange) {
			try {
				sourceStart = new int[]{node.getBegin().get().line, node.getBegin().get().column};
				sourceEnd = new int[]{node.getEnd().get().line, node.getEnd().get().column};
			}
			catch(NoSuchElementException e) {
				e.printStackTrace();
			}
		}
		ParsedNode res = new ParsedNode(node.getClass().getSimpleName(), getVal(node), new ArrayList<ParsedNode>(), sourceStart, sourceEnd);

		// Create the AST
		for(Node child: node.getChildNodes()) {
			if(ignore(child)) {
				continue;
			}
			ParsedNode _child = parseAST(child);
			res.getChildren().add(_child);
		}

		/*
		 * All the code below is just to handle the case that for example in binary expressions like a+b,
		 * JavaParser doesn't recognize '+' as a child node but we need that, there should be three children: 'a', 'b' and '+'.
		 *
		 * So there is some manual work involved here. We need to keep updating methodsToIgnore list as and when we encounter cases
		 * we've not observed so far. We are also adding specific cases that we need to handle like we need the Operator if the node
		 * type is BinaryExpr or we need the modifier when node type is FieldDeclaration. Based on the output on the console we decide
		 * whether a property should or should not be added as a child node. So this is essentially a screening process, not something
		 * automatically handled.
		 * */
		NodeMetaModel meta_model = node.getMetaModel();
		List<PropertyMetaModel> properties = meta_model.getDeclaredPropertyMetaModels();
		for(PropertyMetaModel property: properties) {
			if(!property.isNode()) {
				try {
					String methodName = property.getGetterMethodName();
					if(!Arrays.asList(methodsToIgnore).contains(methodName) && methodName.startsWith("get")) {
						boolean createChild = false;
						if(node instanceof FieldDeclaration || node instanceof Parameter || node instanceof VariableDeclarationExpr) {
							if(methodName.equals("getModifiers")) {
								createChild = true;
							}
						}
						else if(node instanceof BinaryExpr || node instanceof UnaryExpr || node instanceof AssignExpr) {
							if(methodName.equals("getOperator")) {
								createChild = true;
							}
						}
						if(createChild) {
							ParsedNode _child = new ParsedNode(property.getTypeName(), property.getValue(node).toString(), new ArrayList<ParsedNode>(), sourceStart, sourceEnd);
							res.getChildren().add(0, _child);
						}
						else {
							System.out.println("Unknown getter Methods: "+ methodName + " for node: "+ node.getClass().getName());
						}
					}
				} catch (SecurityException e) {
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				}
			}
		}
		return res;
	}
}
