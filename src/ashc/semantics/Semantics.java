package ashc.semantics;

import static ashc.error.AshError.*;
import static ashc.error.AshError.EnumError.*;

import java.util.*;

import ashc.grammar.*;
import ashc.grammar.Node.IExpression;
import ashc.grammar.Node.NodeExprs;
import ashc.grammar.Node.NodeVariable;
import ashc.grammar.Operator.EnumOperation;
import ashc.load.*;
import ashc.semantics.Member.Field;
import ashc.semantics.Member.Function;
import ashc.semantics.Member.Type;
import ashc.semantics.Member.Variable;

/**
 * Ash
 *
 * @author samtebbs, 15:09:18 - 23 May 2015
 */
public class Semantics {

    public static HashMap<QualifiedName, Type> types = new HashMap<QualifiedName, Type>();
    public static HashMap<String, QualifiedName> typeNameMap = new HashMap<String, QualifiedName>();
    public static HashMap<String, Type> aliases = new HashMap<String, Type>();
    public static Stack<Type> typeStack = new Stack<Type>();

    public static class Operation {
	public Function overloadFunc;
	public TypeI type;

	public Operation(final Function overloadFunc, final TypeI type) {
	    this.overloadFunc = overloadFunc;
	    this.type = type;
	}
    }

    public static boolean typeExists(final String typeName) {
	if (typeStack.size() > 0) for (final String generic : typeStack.peek().generics)
	    if (generic.equals(typeName)) return true;
	return typeName.equals("void") || EnumPrimitive.isPrimitive(typeName) || typeNameMap.containsKey(typeName) || aliases.containsKey(typeName);
    }

    public static boolean typeHasSuper(final String type, final String superType) {
	final Optional<Type> t = getType(type), superT = getType(superType);
	if (t.isPresent() && superT.isPresent()) return t.get().hasSuper(superT.get().qualifiedName);
	return false;
    }

    public static void addType(final Type type, final boolean enterType) {
	types.put(type.qualifiedName, type);
	if (enterType) enterType(type);
	bindName(type.qualifiedName);
    }

    public static void exitType() {
	typeStack.pop();
	Scope.getNamespace().pop();
    }

    public static void bindName(final String shortName, final QualifiedName qualifiedName) {
	typeNameMap.put(shortName, qualifiedName);
    }

    public static void bindName(final QualifiedName qualifiedName) {
	bindName(qualifiedName.shortName, qualifiedName);
    }

    public static boolean bindingExists(final String shortName) {
	return typeNameMap.containsKey(shortName);
    }

    public static Optional<Type> getType(final String id, final QualifiedName name) {
	final Optional<Type> type = getType(id);
	if (type.isPresent()) return type;
	if (name != null) TypeImporter.loadClass(name.toString(), name.shortName);
	return getType(id);
    }

    public static Optional<Type> getType(final String id) {
	return typeNameMap.containsKey(id) ? Optional.of(types.get(typeNameMap.get(id))) : aliases.containsKey(id) ? Optional.of(aliases.get(id))
		: Optional.empty();
    }

    public static boolean funcExists(final Function func) {
	final Optional<Type> type = getType(func.qualifiedName.shortName);
	// Check if it is a constructor for an existing type
	if (type.isPresent()) return type.get().getFunc(func.qualifiedName.shortName, func.parameters) != null;
	return typeStack.peek().getFunc(func.qualifiedName.shortName, func.parameters) != null;
    }

    public static void addFunc(final Function func) {
	typeStack.peek().addFunction(func);
    }

    public static boolean fieldExists(final Field field) {
	for (final Field f : typeStack.peek().fields)
	    if (f.equals(field)) return true;
	return false;
    }

    public static void addField(final Field field) {
	typeStack.peek().fields.add(field);
    }

    public static TypeI getPrecedentType(final TypeI type1, final TypeI type2) {
	if (type1.equals(type2)) return type1;
	final String name1 = type1.shortName, name2 = type2.shortName;

	if ((name1.equals("String") && (type1.arrDims == 0)) || (name2.equals("String") && (type2.arrDims == 0))) return new TypeI("String", 0, false);

	// The values in EnumPrimitive are ordered by precedence
	for (final EnumPrimitive p : EnumPrimitive.values())
	    if (p.ashName.equals(name1) || p.ashName.equals(name2)) return new TypeI(p);

	return null;
    }

    public static Field getVar(final String id, final TypeI type) {
	if (type.isTuple()) {
	    for (final TypeI tupleType : type.tupleTypes)
		if ((tupleType.tupleName != null) && tupleType.tupleName.equals(id)) return new Variable(id, tupleType);
	} else {
	    final Optional<Type> t = getType(type.shortName);
	    if (t.isPresent()) return t.get().getField(id);
	}
	return null;
    }

    public static Variable getVar(final String id, final Scope scope) {
	if (scope != null) {
	    for (final Variable var : scope.vars)
		if (var.id.equals(id)) return var;
	    if (scope.parent != null) return getVar(id, scope.parent);
	}
	return null;
    }

    public static Field getVar(final String id) {
	// Look in scope
	Variable var = null;
	if ((var = getVar(id, Scope.getScope())) != null) return var;
	// Else, look in the current type
	return getVar(id, new TypeI(typeStack.peek().qualifiedName.shortName, 0, false));
    }

    public static TypeI getFuncType(final String id, final TypeI type, final NodeExprs args) {
	if (type.isArray() && id.equals("toString()")) return new TypeI("String", 0, false);
	else {
	    final Optional<Type> t = getType(type.shortName);
	    if (t.isPresent()) return t.get().getFuncType(id, args);
	}
	return null;
    }

    public static TypeI getFuncType(final String id, final NodeExprs args) {
	return getFuncType(id, new TypeI(typeStack.peek().qualifiedName.shortName, 0, false), args);
    }

    public static Function getFunc(final String id, final TypeI type, final NodeExprs args) {
	final Optional<Type> t = type.qualifiedName != null ? getType(type.shortName, type.qualifiedName) : getType(type.shortName);
	if (t.isPresent()) return t.get().getFunc(id, args);
	return null;
    }

    public static Function getFunc(String id, final NodeExprs args) {
	final Optional<Type> type = getType(id);
	// Check if it is a constructor for an existing type
	if (type.isPresent()) {
	    id = type.get().qualifiedName.shortName;
	    return type.get().getFunc(id, args);
	}
	return getFunc(id, new TypeI(typeStack.peek().qualifiedName.shortName, 0, false), args);
    }

    public static void enterType(final Type type) {
	typeStack.push(type);
    }

    public static boolean varExists(final String id) {
	final Scope scope = Scope.getScope();
	if ((scope != null) && scope.hasVar(id)) return true;
	for (final Field field : typeStack.peek().fields)
	    if (field.qualifiedName.shortName.equals(id)) return true;
	return false;
    }

    public static void addVar(final Variable variable) {
	if (Scope.inScope()) Scope.getScope().vars.add(variable);
    }

    public static Type currentType() {
	return typeStack.peek();
    }

    public static TypeI getGenericType(final String shortName, final TypeI type) {
	final Optional<Type> typeOpt = getType(type.shortName);
	if (typeOpt.isPresent()) {
	    final Type enclosingType = typeOpt.get();
	    final int i = 0;
	    for (final String generic : enclosingType.generics)
		if (generic.equals(shortName)) if (i < type.genericTypes.size()) return type.genericTypes.get(i);
		else return new TypeI("Object", 0, false);
	}
	return null;
    }

    public static TypeI filterNullType(final TypeI exprType) {
	return exprType.isNull() ? TypeI.getObjectType().copy().setOptional(true) : exprType;
    }

    public static TypeI checkUnwrappedOptional(TypeI exprType, final Node node, final IExpression expr) {
	if (expr instanceof NodeVariable) {
	    final Field field = ((NodeVariable) expr).var;
	    if (field != null) if (!Scope.getScope().hasNullCheck(field)) semanticWarning(node.line, node.column, UNCHECKED_UNWRAP, field.qualifiedName.shortName);
	}
	if (exprType != null) {
	    exprType = exprType.copy();
	    if (!exprType.optional) semanticError(node, node.line, node.column, UNWRAPPED_VALUE_NOT_OPTIONAL, exprType);
	    exprType.optional = false;
	} else node.errored = true;
	return exprType;
    }

    public static void addAlias(final String alias, final Type type) {
	aliases.put(alias, type);
    }

    // Returning an array here is a messy hack, but the best way I can think of
    // returning both a TypeI and Function, rather than using a new class. When porting to ash, I'll just use a tuple
    public static Object[] getOperationType(final TypeI type1, final TypeI type2, final Operator operator) {
	if (type1.isNumeric() && type2.isNumeric()) {
	    final EnumPrimitive result = operator.operation.primitive;
	    return result == null ? new Object[] { getPrecedentType(type1, type2), null } : new Object[] { new TypeI(result), null };
	} else if (type1.equals(TypeI.getStringType()) && (operator.operation == EnumOperation.ADD)) return new Object[] { TypeI.getStringType(), null };

	if (type1.isArray() || type1.isTuple() || type1.isVoid() || type1.isNull()) return null;
	else if (type2.isArray() || type2.isTuple() || type2.isVoid() || type2.isNull()) return null;

	// Check for operator overloads, start with type 1
	final LinkedList<TypeI> parameter = new LinkedList<TypeI>();
	Type type;
	Function func;
	if (!type1.isPrimitive()) {
	    parameter.add(type2);
	    type = Semantics.getType(type1.shortName).get();
	    func = type.getFunc(operator.opStr, parameter);
	    if (func != null) return new Object[] { func.returnType, func };
	}
	if (!type2.isPrimitive()) {
	    // Check type 2 for operator overloads
	    parameter.clear();
	    parameter.add(type1);
	    type = Semantics.getType(type2.shortName).get();
	    func = type.getFunc(operator.opStr, parameter);
	    if (func != null) return new Object[] { func.returnType, func };
	}
	return null;
    }

    public static Operation getOperationType(final TypeI type, final Operator operator) {
	if (type.isNumeric()) return new Operation(null, type);
	if (type.isArray() || type.isNull() || type.isTuple() || type.isVoid()) return null;
	final Function func = Semantics.getType(type.shortName).get().getFunc(operator.opStr, new LinkedList<>());
	return func != null ? new Operation(func, func.returnType) : null;
    }

    public static TypeI getGeneric(final LinkedList<TypeI> generics, final int index) {
	return generics.size() <= index ? TypeI.getObjectType() : generics.get(index);
    }

}
