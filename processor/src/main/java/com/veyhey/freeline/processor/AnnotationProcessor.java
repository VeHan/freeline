package com.veyhey.freeline.processor;

import com.google.auto.service.AutoService;
import com.google.gson.Gson;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {

    public static final String OPTION_FORCE_APT = "freelineForceAnnotationProcessorsPath";
    public static final String OPTION_FREELINE_DESCRIPTION = "freelineDescription";
    public static final String OPTION_FREELINE_MODULE = "freelineModule";

    private List<Class> forceAnnotationProcessors;
    private Set<String> forceAnnotationProcessorClasses = new HashSet<>();
    private Set<String> forceAnnotationProcessorFiles = new HashSet<>();
    private List<AbstractProcessor> processors;
    private List<Boolean> processorResults;
    private String freelineDescriptionPath;
    private String moduleName;
    private URLClassLoader urlClassLoader;
    private boolean complete;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        handleOptions();
    }

    @Override
    public Set<String> getSupportedOptions() {
        Set<String> ret = new HashSet<String>();
        ret.add(OPTION_FORCE_APT);
        ret.add(OPTION_FREELINE_MODULE);
        ret.add(OPTION_FREELINE_DESCRIPTION);
        for (AbstractProcessor processor :
                processors) {
            ret.addAll(processor.getSupportedOptions());
        }
        return ret;
    }


    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> ret = new HashSet<String>();
        for (AbstractProcessor processor :
                processors) {
            ret.addAll(processor.getSupportedAnnotationTypes());
        }
        return ret;
    }

    private void handleOptions() {
        forceAnnotationProcessors = new ArrayList<>();
        String forceAptValue = processingEnv.getOptions().get(OPTION_FORCE_APT);
        freelineDescriptionPath = processingEnv.getOptions().get(OPTION_FREELINE_DESCRIPTION);
        moduleName = processingEnv.getOptions().get(OPTION_FREELINE_MODULE);

        List<URL> forceAnnotationProcessorPaths = new ArrayList<>();

        // 解析processorPath
        if (null != forceAptValue) {
            String[] forceAptStrs = forceAptValue.split(",");
            for (String aptStr :
                    forceAptStrs) {
                try {
                    forceAnnotationProcessorPaths.add(new URL("file://" + aptStr));
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }
        urlClassLoader = new URLClassLoader(forceAnnotationProcessorPaths.toArray(new URL[0]));

        // 设置自定义ClassLoader为默认ClassLoader的父ClassLoader
        try {
            Field field = ClassLoader.class.getDeclaredField("parent");
            field.setAccessible(true);
            field.set(this.getClass().getClassLoader(), urlClassLoader);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        //查找注解处理类
        if (null != forceAptValue) {
            String[] forceAptStrs = forceAptValue.split(",");
            for (String aptStr :
                    forceAptStrs) {
                findAnnotationProcessor(aptStr);
            }
        }

        // 初始化所以注解处理器
        processors = new ArrayList<>();
        processorResults = new ArrayList<>();
        for (Class processorClass : forceAnnotationProcessors) {
            try {
                AbstractProcessor abstractProcessor = (AbstractProcessor) processorClass.newInstance();
                abstractProcessor.init(processingEnv);
                processors.add(abstractProcessor);
                processorResults.add(false);
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

    }

    private void findAnnotationProcessor(String zipPath) {
        try {
            ZipFile zipFile = new ZipFile(zipPath);
            ZipEntry entry = zipFile.getEntry("META-INF/services/javax.annotation.processing.Processor");
            if (entry != null) {
                InputStream stream = zipFile.getInputStream(entry);
                String processors = FreelineUtils.convertStreamToString(stream);
                String[] split = processors.split("\n");
                for (String className : split) {
                    if (!"".equals(className)) {
                        forceAnnotationProcessors.add(urlClassLoader.loadClass(className));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (complete) {
            return true;
        }


        for (int i = 0; i < processors.size(); i++) {
            if (!processorResults.get(i)) {
                boolean complete = processors.get(i).process(set, roundEnvironment);
                processorResults.set(i, complete);

                for (String annotationType :
                        processors.get(i).getSupportedAnnotationTypes()) {
                    findForceAPClasses(roundEnvironment, annotationType);
                }
            }
        }

        ProjectDescription projectDescription = FreelineUtils.getJson(freelineDescriptionPath, ProjectDescription.class);
        if (projectDescription == null) {
            throw new RuntimeException("freeline not init !\n  Please Run ./gradlew checkBeforeCleanBuild");
        }

        complete = true;
        if (processorResults.size() > 0) {
            for (boolean processorResult : processorResults) {
                if (!processorResult) {
                    complete = false;
                    break;
                }
            }
        }

        if (complete) {
            findForceAPSources(projectDescription);
            FreelineUtils.saveJson(new Gson().toJson(this.forceAnnotationProcessorFiles), String.format("%s/freeline-force-apt/%s/force_annotation_processor_files.json", projectDescription.buildCacheDir, moduleName), true);
        }

        return complete;
    }


    private void findForceAPSources(ProjectDescription projectDescription) {
        if (projectDescription != null) {
            Map<String, ProjectDescription.ProjectSourceSetsBean> sourceSets = projectDescription.projectSourceSets;
            if (sourceSets == null) {
                return;
            }
            ProjectDescription.ProjectSourceSetsBean moduleSourceSets = sourceSets.get(moduleName);
            if (moduleSourceSets == null) {
                return;
            }
            List<String> srcDirectory = moduleSourceSets.mainSrcDirectory;
            for (String srcDir :
                    srcDirectory) {
                File file = new File(srcDir);
                if (!file.exists()) {
                    continue;
                }
                for (String className : forceAnnotationProcessorClasses) {
                    String srcPath = className.replace(".", "/") + ".java";
                    File srcFile = new File(srcDir, srcPath);
                    if (srcFile.exists()) {
                        forceAnnotationProcessorFiles.add(srcFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * 查找强制apt处理的类
     *
     * @param roundEnvironment
     * @param annotationType
     */
    private void findForceAPClasses(RoundEnvironment roundEnvironment, String annotationType) {
        Set<? extends Element> elements = null;
        try {
            elements = roundEnvironment.getElementsAnnotatedWith((Class<? extends Annotation>) Class.forName(annotationType));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        if (elements == null) {
            return;
        }
        for (Element element : elements) {
            if (element.getKind() == ElementKind.CLASS) {
                Name name = element.getSimpleName();
                PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(element);
                forceAnnotationProcessorClasses.add(packageElement.getQualifiedName() + "." + name.toString());
            } else {
                List<? extends Element> enclosedElements = element.getEnclosedElements();

                Element enclosedElement;
                if (enclosedElements.size() > 0) {
                    enclosedElement = enclosedElements.get(0);
                } else {
                    enclosedElement = element.getEnclosingElement();
                }
                if (enclosedElement != null) {
                    Name name = enclosedElement.getSimpleName();
                    PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(enclosedElement);
                    forceAnnotationProcessorClasses.add(packageElement.getQualifiedName() + "." + name.toString());
                }
            }
        }
    }
}
