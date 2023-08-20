package com.rex.processor;

import com.google.auto.service.AutoService;
import com.rex.annotation.Getter;
import com.rex.modifier.TreeModifier;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.function.Consumer;

/*
*  Annotation Processor를 쉽게 등록하려는 목적으로 @AutoService를 사용한다.
*  com.rex.annotation 패키지의 Getter 애너테이션을 지원한다.
*  JDK 11 버전을 지원한다.
* */
@AutoService(Processor.class)
@SupportedAnnotationTypes("com.rex.annotation.Getter")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class GetterProcessor extends AbstractProcessor {


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // TreeModifier의 객체를 만들어준다.
        TreeModifier treeModifier = new TreeModifier(processingEnv);

        // AST 조작에 필요한 전략을 셋팅해준다.
        treeModifier.setClassDefModifyStrategy(getAppendGetterStrategy(treeModifier));

        // @Getter 애너테이션이 붙은 모든 타입(클래스, 열거타입, 인터페이스)들을 순회한다.
        for (Element element : roundEnv.getElementsAnnotatedWith(Getter.class)) {
            // @Getter 애너테이션이 클래스가 아닌 곳에 있는 경우, 에러 메시지를 빌드 로그에 남겨준다.
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Annotation Not Supported");
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing >> " + element.getSimpleName().toString());
                // @Getter 애너테이션이 클래스에 붙어 있는 경우, 모든 필드에 대해 Getter를 생성해주는 메서드를 AST 트리 조작으로 삽입한다.
                treeModifier.modifyTree(element);
            }
        }
        return true;
    }


    private Consumer<JCTree.JCClassDecl> getAppendGetterStrategy(TreeModifier treeModifier) {
        TreeMaker treeMaker = treeModifier.getTreeMaker();
        Names names = treeModifier.getNames();

        /*
        *  AST 조작 전략은 Consumer로 제공한다.
        *  -> Consumer로 제공시 이 전략을 사용하는 곳에서 accept만 호출하면 설정된 전략대로 AST 조작이 이루어진다.
        *
        * */
        return jcClassDecl -> {
            List<JCTree> members = jcClassDecl.getMembers();
            for (JCTree member : members) {
                /*
                *  모든 멤버를 순회하면서, 멤버가 변수(Field)일 경우에만 Getter를 만들어준다.
                *  AST에 삽입할 메서드는 JCTree.JCMethodDecl 타입이며, append 메서드를 활용해 삽입할 메서드를 붙여준다.
                * */
                if (member instanceof JCTree.JCVariableDecl) {
                    JCTree.JCMethodDecl getter = createGetter(treeMaker, names, (JCTree.JCVariableDecl) member);
                    jcClassDecl.defs = jcClassDecl.defs.append(getter);
                }
            }
        };
    }

    private JCTree.JCMethodDecl createGetter(TreeMaker treeMaker, Names names, JCTree.JCVariableDecl member) {

        // 필드의 이름을 문자열로 반환받는다.
        String memberName = member.name.toString();

        // 메서드의 명명 규칙은 get + 필드 이름의 첫 글자를 대문자로 + 나머지 문자
        String methodName = "get" + memberName.substring(0, 1).toUpperCase() +
                memberName.substring(1);

        // 메서드가 잘 생성되고 있는지 빌드 로그로 확인할 수 있다.
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, methodName);

        return treeMaker.MethodDef(
                treeMaker.Modifiers(1), // flag 번호에 따라 접근 제어나 추상 메서드 등 다양한 제어를 할 수 있다. 1번은 public 이다.
                names.fromString(methodName), // 메서드의 이름은 아까 지어놓은 대로 getXXXX 과 같다.
                (JCTree.JCExpression) member.getType(), // 대상 필드의 타입 그대로 반환하도록 타입을 지정해준다.
                List.nil(), // 필요하지 않은 부분이라도 List를 인수로 받고 있기 때문에 빈 리스트를 넣어준다.
                List.nil(),
                List.nil(),
                /*
                *  메서드의 본문에 해당하는 부분이다.
                *  Block의 flag는 들여쓰기 수준을 말한다.
                *  Ident 메서드는 AST 노드 중 하나로 식별자를 나타낸다. 이렇게 반들어진 식별자는 필드 변수를 나타내는 AST 구조 표현에 사용된다.
                * */
                treeMaker.Block(1,
                  List.of(treeMaker.Return(treeMaker.Ident(member.getName())))
                ),
                null
        );
    }
}
