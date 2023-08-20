package com.rex.processor;

import com.google.auto.service.AutoService;
import com.rex.annotation.Setter;
import com.rex.modifier.TreeModifier;
import com.sun.tools.javac.code.TypeTag;
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


@AutoService(Processor.class)
@SupportedAnnotationTypes("com.rex.annotation.Setter")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class SetterProcessor extends AbstractProcessor {


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // TreeModifier의 객체를 만들어준다.
        TreeModifier treeModifier = new TreeModifier(processingEnv);

        // AST 조작에 필요한 전략을 셋팅해준다.
        treeModifier.setClassDefModifyStrategy(getAppendSetterStrategy(treeModifier));

        // @Setter 애너테이션이 붙은 모든 타입(클래스, 열거타입, 인터페이스)들을 순회한다.
        for (Element element : roundEnv.getElementsAnnotatedWith(Setter.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                // @Setter 애너테이션이 클래스가 아닌 곳에 있는 경우, 에러 메시지를 빌드 로그에 남겨준다.
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Annotation Not Supported");
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing >> " + element.getSimpleName().toString());
                // @Setter 애너테이션이 클래스에 붙어 있는 경우, 모든 필드에 대해 Setter를 생성해주는 메서드를 AST 트리 조작으로 삽입한다.
                treeModifier.modifyTree(element);
            }
        }
        return true;
    }


    private Consumer<JCTree.JCClassDecl> getAppendSetterStrategy(TreeModifier treeModifier) {
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
                 *  모든 멤버를 순회하면서, 멤버가 변수(Field)일 경우에만 Setter를 만들어준다.
                 *  AST에 삽입할 메서드는 JCTree.JCMethodDecl 타입이며, append 메서드를 활용해 삽입할 메서드를 붙여준다.
                 * */
                if (member instanceof JCTree.JCVariableDecl) {
                    JCTree.JCMethodDecl setter = createSetter(treeMaker, names, (JCTree.JCVariableDecl) member);
                    jcClassDecl.defs = jcClassDecl.defs.append(setter);
                }
            }
        };
    }

    private JCTree.JCMethodDecl createSetter(TreeMaker treeMaker, Names names, JCTree.JCVariableDecl member) {
        // 필드의 이름을 문자열로 반환받는다.
        String memberName = member.name.toString();

        // 메서드의 명명 규칙은 set + 필드 이름의 첫 글자를 대문자로 + 나머지 문자
        String methodName = "set" + memberName.substring(0, 1).toUpperCase() +
                memberName.substring(1);

        // 메서드가 잘 생성되고 있는지 빌드 로그를 통해 확인할 수 있다.
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, methodName);

        /*
        *  파라미터가 있는 메서드이기 때문에, 필요한 파라미터를 설정해주어야 한다.
        *  파라미터의 이름, 파라미터의 타입을 넣어주어야 한다.
        *  owner는 사용하지 않는 인수이므로 null을 넣어준다.
        * */
        JCTree.JCVariableDecl param = treeMaker.Param(names.fromString("_" + memberName), member.vartype.type, null);


        return treeMaker.MethodDef(
                treeMaker.Modifiers(1), // public 접근 제어자를 가진다.
                names.fromString(methodName), // 메서드의 이름은 setXXXX와 같은 규칙으로 생성된다.
                treeMaker.TypeIdent(TypeTag.VOID), // 반환 타입이 없으므로, VOID를 지정해준다.
                List.nil(), // 필요 없는 인수는 List.nil() 로 emptyList를 넣어준다.
                List.of(param), // Getter와 다르게 파라미터가 있으므로, 파라미터를 넣어준다.
                List.nil(),
                /*
                *  들여쓰기 레벨은 1로 지정한다.
                *  treeMaker.Exec는 AST에서 실행문(Statement)를 나타내는 노드를 생성한다.
                *  treeMaker.Assign은 AST에서 대입문(Assignment)을 나타내는 노드를 생성한다.
                *  treeMaker.Ident는 식별자를 생성하는 역할을 한다.
                *  즉, member라는 이름을 가진 변수에, param.name 이름으로 들어오는 파라미터의 값을 대입하는 대입문을
                *  실행하라 라는 의미다.
                * */
                treeMaker.Block(1,
                  List.of(treeMaker.Exec(treeMaker.Assign(treeMaker.Ident(member), treeMaker.Ident(param.name))))
                ),
                null
        );
    }
}
