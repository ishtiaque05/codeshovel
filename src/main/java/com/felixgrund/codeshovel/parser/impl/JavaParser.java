package com.felixgrund.codeshovel.parser.impl;import com.felixgrund.codeshovel.changes.*;import com.felixgrund.codeshovel.entities.Ycommit;import com.felixgrund.codeshovel.entities.Yparameter;import com.felixgrund.codeshovel.exceptions.ParseException;import com.felixgrund.codeshovel.parser.AbstractParser;import com.felixgrund.codeshovel.parser.Yfunction;import com.felixgrund.codeshovel.parser.Yparser;import com.felixgrund.codeshovel.wrappers.StartEnvironment;import com.github.javaparser.ParserConfiguration;import com.github.javaparser.ast.CompilationUnit;import com.github.javaparser.ast.body.MethodDeclaration;import com.github.javaparser.ast.visitor.VoidVisitorAdapter;import com.felixgrund.codeshovel.wrappers.Commit;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import java.util.*;import static com.github.javaparser.ParserConfiguration.LanguageLevel.RAW;public class JavaParser extends AbstractParser implements Yparser {	private static long timeTaken = 0;	private Logger log = LoggerFactory.getLogger(JavaParser.class);	public static final String ACCEPTED_FILE_EXTENSION = ".java";	private List<Yfunction> allMethods;	public JavaParser(StartEnvironment startEnv, String filePath, String fileContent, Commit commit) throws ParseException {		super(startEnv, filePath, fileContent, commit);	}	@Override	public Yfunction findFunctionByNameAndLine(String name, int line) {		return findMethod(new MethodVisitor() {			@Override			public boolean methodMatches(Yfunction method) {				String methodName = method.getName();				int methodLineNumber = method.getNameLineNumber(); // TODO get() ?				return name.equals(methodName) && line == methodLineNumber;			}		});	}	@Override	public List<Yfunction> findFunctionsByLineRange(int beginLine, int endLine) {		return findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(Yfunction method) {				int lineNumber = method.getNameLineNumber();				return lineNumber >= beginLine && lineNumber <= endLine;			}		});	}	@Override	public List<Yfunction> getAllFunctions() {		return findNonAbstractMethods();	}	@Override	public Map<String, Yfunction> getAllFunctionsCount() {		List<Yfunction> matchedMethods = findNonAbstractMethods();		return transformMethodsToMap(matchedMethods);	}	private Map<String, Yfunction> transformMethodsToMap(List<Yfunction> methods) {		Map<String, Yfunction> ret = new HashMap<>();		for (Yfunction method : methods) {			ret.put(method.getId(), method);		}		return ret;	}	private List<Yfunction> findNonAbstractMethods() {		return findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(Yfunction method) {				return method.getBody() != null;			}		});	}	@Override	public Yfunction findFunctionByOtherFunction(Yfunction otherMethod) {		Yfunction function = null;		String methodNameOther = otherMethod.getName();		List<Yparameter> parametersOther = otherMethod.getParameters();		List<Yfunction> matchedMethods = findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(Yfunction method) {				String methodNameThis = method.getName();				List<Yparameter> parametersThis = method.getParameters();				boolean methodNameMatches = methodNameOther.equals(methodNameThis);				boolean parametersMatch = parametersThis.equals(parametersOther);				return methodNameMatches && parametersMatch;			}		});		int numMatches = matchedMethods.size();		if (numMatches == 1) {			function = matchedMethods.get(0);		} else if (numMatches > 1) {			log.trace("Found more than one matching function. Trying to find correct candidate.");			function = getCandidateWithSameParent(matchedMethods, otherMethod);		}		return function;	}	private Yfunction getCandidateWithSameParent(List<Yfunction> candidates, Yfunction compareMethod) {		for (Yfunction candidateMethod : candidates) {			if (candidateMethod.getParentName().equals(compareMethod.getParentName())) {				log.trace("Found correct candidate. Parent name: {}", candidateMethod.getParentName());				return candidateMethod;			}		}		return null;	}	@Override	protected void parse() throws ParseException {		CompilationUnit rootCompilationUnit = com.github.javaparser.JavaParser.parse(this.fileContent);		if (rootCompilationUnit == null) {			throw new ParseException("Could not parse root compilation unit", this.filePath, this.fileContent);		}		MethodVisitor visitor = new MethodVisitor() {			@Override			public boolean methodMatches(Yfunction method) {				return method.getBody() != null;			}		};		rootCompilationUnit.accept(visitor, null);		this.allMethods = visitor.getMatchedNodes();	}	@Override	public boolean functionNamesConsideredEqual(String aName, String bName) {		return aName != null && aName.equals(bName);	}	@Override	public double getScopeSimilarity(Yfunction function, Yfunction compareFunction) {		double ret = 0.0;		String aParentName = function.getParentName();		String bParentName = compareFunction.getParentName();		if (aParentName != null && bParentName != null) {			if (aParentName.equals(bParentName)) {				ret = 1.0;			}		}		return ret;	}	@Override	public String getAcceptedFileExtension() {		return ACCEPTED_FILE_EXTENSION;	}	@Override	public List<Ychange> getMinorChanges(Ycommit commit, Yfunction compareFunction) {		List<Ychange> changes = new ArrayList<>();		Yreturntypechange yreturntypechange = getReturnTypeChange(commit, compareFunction);		Ymodifierchange ymodifierchange = getModifiersChange(commit, compareFunction);		Yexceptionschange yexceptionschange = getExceptionsChange(commit, compareFunction);		Ybodychange ybodychange = getBodyChange(commit, compareFunction);		Yparametermetachange yparametermetachange = getParametersMetaChange(commit, compareFunction);		if (yreturntypechange != null) {			changes.add(yreturntypechange);		}		if (ymodifierchange != null) {			changes.add(ymodifierchange);		}		if (yexceptionschange != null) {			changes.add(yexceptionschange);		}		if (ybodychange != null) {			changes.add(ybodychange);		}		if (yparametermetachange != null) {			changes.add(yparametermetachange);		}		return changes;	}	private Yfunction transformMethod(MethodDeclaration method) {		return new JavaFunction(method, this.commit, this.filePath, this.fileContent);	}	private Yfunction findMethod(MethodVisitor visitor) {		Yfunction ret = null;		List<Yfunction> matchedNodes = findAllMethods(visitor);		if (matchedNodes.size() > 0) {			ret = matchedNodes.get(0);		}		return ret;	}	private List<Yfunction> findAllMethods(MethodVisitor visitor) {		List<Yfunction> matchedMethods = new ArrayList<>();		for (Yfunction method : this.allMethods) {			boolean hasBody = method.getBody() != null; // ignore abstract and interface methods			if (hasBody && visitor.methodMatches(method)) {				matchedMethods.add(method);			}		}		return matchedMethods;	}	public abstract class MethodVisitor extends VoidVisitorAdapter<Void> {		private List<Yfunction> matchedNodes = new ArrayList<>();		public abstract boolean methodMatches(Yfunction method);		@Override		public void visit(MethodDeclaration method, Void arg) {			super.visit(method, arg);			boolean hasBody = method.getBody().isPresent();			Yfunction yfunction = transformMethod(method);			if (hasBody && methodMatches(yfunction)) {				matchedNodes.add(yfunction);			}		}		public List<Yfunction> getMatchedNodes() {			return matchedNodes;		}	}}