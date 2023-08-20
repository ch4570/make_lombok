package com.rex.processor;

import com.google.auto.service.AutoService;
import com.rex.annotation.AccessLevel;
import com.rex.annotation.NoArgsConstructor;
import com.rex.modifier.TreeModifier;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
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
@SupportedAnnotationTypes("com.rex.annotation.NoArgsConstructor")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class NoArgsProcessor extends AbstractProcessor  {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TreeModifier treeModifier = new TreeModifier(processingEnv);

        for (Element element : roundEnv.getElementsAnnotatedWith(NoArgsConstructor.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Annotation Not Supported");
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Processing >> " + element.getSimpleName().toString());

                // AccessLevel에 따라 생성자의 접근 제어 레벨이 변동되기 때문에 애너테이션에 접근해서 값을 뽑아온다.
                AccessLevel accessLevel = element.getAnnotation(NoArgsConstructor.class).accessLevel();
                // AST 조작 전략을 셋팅해준다.
                treeModifier.setClassDefModifyStrategy(getAppendNoArgsStrategy(treeModifier, accessLevel));
                treeModifier.modifyTree(element);
            }
        }
        return true;
    }


    private Consumer<JCTree.JCClassDecl> getAppendNoArgsStrategy(TreeModifier treeModifier, AccessLevel accessLevel) {
        TreeMaker treeMaker = treeModifier.getTreeMaker();
        Names names = treeModifier.getNames();

        return jcClassDecl -> {
            ListBuffer<JCTree> newMembers = new ListBuffer<>();
            for (JCTree member : jcClassDecl.defs) {
                /*
                *  컴파일러는 기본적으로 파라미터 있는 생성자가 없다면, 파라미터가 없는 public 생성자를 넣어주게 된다.
                *  이럴 경우 생성자가 중복 정의되기 때문에 원하는 접근 제어 레벨의 생성자를 삽입할때 에러가 발생한다.
                *  따라서 이미 매개변수 없는 생성자가 있다면 제외해야 한다.
                * */
                if (member instanceof JCTree.JCMethodDecl) {

                    // 메서드가 생성자인지 확인한다.
                    boolean isConstructor = ((JCTree.JCMethodDecl) member).name.toString().equals("<init>");

                    // 생성자이면서 파라미터가 있다면 다시 넣어준다.
                    if (isConstructor && !((JCTree.JCMethodDecl) member).getParameters().isEmpty()) {
                        newMembers.add(member);
                    } else if (!isConstructor) {
                        // 생성자가 아니라면 메서드이므로 추가한다.
                        newMembers.append(member);
                    }
                } else {
                    // 메서드가 아닌 멤버는 전부 넣어준다.
                    newMembers.append(member);
                }

            }
            jcClassDecl.defs = newMembers.toList();
            JCTree.JCMethodDecl noArgs = createNoArgs(treeMaker, names, accessLevel);
            jcClassDecl.defs = jcClassDecl.defs.append(noArgs);
        };
    }

    private JCTree.JCMethodDecl createNoArgs(TreeMaker treeMaker, Names names, AccessLevel accessLevel) {

        long flag = 0L;

        switch (accessLevel) {
            case PUBLIC:
                flag = 1L;
                break;
            case PACKAGE:
                flag = 0L;
                break;
            case PRIVATE:
                flag = 2L;
                break;
            case PROTECTED:
                flag = 4L;
                break;
        }


        return treeMaker.MethodDef(
                treeMaker.Modifiers(flag),
                names.init,
                treeMaker.TypeIdent(TypeTag.VOID),
                List.nil(),
                List.nil(),
                List.nil(),
                treeMaker.Block(0, List.nil()),
                null
        );
    }
}
