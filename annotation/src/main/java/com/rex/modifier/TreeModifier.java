package com.rex.modifier;


import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.util.Objects;
import java.util.function.Consumer;

public class TreeModifier {

    private Trees trees;
    private Context context;
    private TreeMaker treeMaker;
    private Names names;
    private TreePathScanner<Object, CompilationUnitTree> scanner;

    public TreeModifier(ProcessingEnvironment processingEnvironment) {
        final JavacProcessingEnvironment javacProcessingEnvironment = (JavacProcessingEnvironment) processingEnvironment;
        this.trees = Trees.instance(processingEnvironment);
        this.context = javacProcessingEnvironment.getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    public void setClassDefModifyStrategy(Consumer<JCTree.JCClassDecl> strategy) {
        this.scanner = new TreePathScanner<>() {
            @Override
            public Trees visitClass(ClassTree node, CompilationUnitTree compilationUnitTree) {
                JCTree.JCCompilationUnit compilationUnit = (JCTree.JCCompilationUnit) compilationUnitTree;
                if (compilationUnit.sourcefile.getKind() == JavaFileObject.Kind.SOURCE) {
                    compilationUnit.accept(new TreeTranslator() {
                        /*
                        *  AST 클래스 노드를 순회하며 AST를 재정의 한다.
                        *  -> 컴파일 타임에 추상 구문 트리(Abstract Syntax Tree)를 조작(원하는 메서드 삽입)
                        * */
                        @Override
                        public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                            super.visitClassDef(jcClassDecl);
                            /*
                            *    AST 수정이 이루어지는 부분이다.
                            *    AST 수정 전략은 외부에서 주입 받아 사용할 수 있다.
                            * */
                            strategy.accept(jcClassDecl);
                        }
                    });
                }

                return trees;
            }
        };
    }


    /*
    *  AST를 순회하면서 AST를 조작한다.
    *  -> 원하는 메서드를 실제 삽입하는 부분이다.
    * */
    public void modifyTree(Element element) {
        if (Objects.nonNull(scanner)) {
            final TreePath path = trees.getPath(element);
            scanner.scan(path, path.getCompilationUnit());
        }
    }

    public TreeMaker getTreeMaker() {
        return treeMaker;
    }

    public Names getNames() {
        return names;
    }
}
