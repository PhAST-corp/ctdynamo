package ai.phast.ctdynamo.processor;

import ai.phast.ctdynamo.annotations.DynamoItem;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * At compile time this class will inspect all classes annotated with @{@link DynamoItem} and build dynamo tables
 * and/or codecs for them.
 */
@AutoService(Processor.class)
public class DynamoProcessor extends AbstractProcessor {

    /** Used to report errors out */
    private Messager messager;

    /** Tools and constants for working with mirror types */
    private TypeTools typeTools;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        typeTools = new TypeTools(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(DynamoItem.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(DynamoItem.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Only classes can have annotation " + DynamoItem.class.getSimpleName(), element);
                return true;
            }
            var annotation = element.getAnnotation(DynamoItem.class);
            try {
                var deferFrom = typeTools.getAnnonationClassValue(annotation::deferredFrom);
                var isDeferral = !typeTools.equal(deferFrom, typeTools.objectMirror);
                if (annotation.defer() && !isDeferral) {
                    // Make a deferral class so we'll come back to this later, after lombok has run
                    buildDeferralClass((TypeElement)element, annotation);
                } else {
                    if (isDeferral) {
                        // We don't work with this class; this is a deferral class created in an earlier round. Instead
                        // we want to work with the original class that triggered creation of the deferral.
                        element = processingEnv.getTypeUtils().asElement(deferFrom);
                    }
                    var writer = new CtClassGenerator((TypeElement)element, typeTools, annotation.ignoreNulls());
                    var outputs = Arrays.asList(annotation.value());
                    if (outputs.contains(DynamoItem.Output.TABLE)) {
                        writer.buildTableClass().writeTo(processingEnv.getFiler());
                    }
                    if (outputs.contains(DynamoItem.Output.CODEC)) {
                        writer.buildCodecClass().writeTo(processingEnv.getFiler());
                    }
                }
            } catch (CtException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement() == null ? element : e.getElement());
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error writing table: " + e, element);
            } catch (Exception e) {
                e.printStackTrace();
                messager.printMessage(Diagnostic.Kind.ERROR, "Failure to process: " + e, element);
            }
        }
        return true;
    }

    /**
     * Build a deferral class that will trigger us running again in a later round. We want to be sure to run after Lombok,
     * so we see the setters and getters that lombok creates. We do that by creating a "deferral class," an empty class
     * whose @DynamoItem annotation is a copy of the original, but with an added "deferredFrom=" value pointing us back
     * to the original. Since the deferral class will be processed in a later round, we know that Lombok has already
     * finished on the original class.
     * @param element The class that we need to process
     * @param annotation The annotation from the original class
     * @throws IOException If we have an error writing the deferral class
     */
    private void buildDeferralClass(TypeElement element, DynamoItem annotation) throws IOException {
        var annotationSpec = AnnotationSpec.builder(DynamoItem.class)
                                 .addMember("deferredFrom", "$T.class", element)
                                 .addMember("ignoreNulls", Boolean.toString(annotation.ignoreNulls()))
                                 .addMember("value", Arrays.stream(annotation.value())
                                                         .map(v -> "$T." + v.name())
                                                         .collect(Collectors.joining(", ", "{", "}")),
                                     Arrays.stream(annotation.value()).map(v -> DynamoItem.Output.class).toArray())
                                 .build();
        var classBuilder = TypeSpec.classBuilder(element.getSimpleName() + "DynamoDefer")
                               .addAnnotation(annotationSpec)
                               .addJavadoc(CodeBlock.builder().add("This class is used internally by the ctDynamo processor. It is needed to ensure\n"
                                                                       + "than the dynamo processor runs after annotation processors that change the\n"
                                                                       + "class (such as lombok). If you are not using any class-changing annotations,\n"
                                                                       + "you can set \"defer=false\" in your DynamoItem annotations to prevent the\n"
                                                                       + "creation of classes like this").build());
        var qualifiedName = element.getQualifiedName().toString();
        var packageSplit = qualifiedName.lastIndexOf('.');
        JavaFile.builder(packageSplit > 0 ? qualifiedName.substring(0, packageSplit) : "", classBuilder.build()).build()
            .writeTo(processingEnv.getFiler());
    }
}
