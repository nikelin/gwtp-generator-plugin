package com.redshape.maven.plugins.gwt.goals;

import com.redshape.maven.plugins.gwt.AbstractGeneratorMojo;
import com.sun.codemodel.*;
import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.directorywalker.DirectoryScanner;
import com.thoughtworks.qdox.directorywalker.FileVisitor;
import com.thoughtworks.qdox.model.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Cyril on 6/14/13.
 */
@Mojo( name = "gen-presenter" )
public class GenPresenterMojo extends AbstractGeneratorMojo {

    /**
     * GWTP Constants
     */
    private static final String PRESENTER_CLASS_NAME = "com.gwtplatform.mvp.client.Presenter";
    private static final String VIEW_CLASS_NAME = "com.gwtplatform.mvp.client.View";
    private static final String PROXY_CLASS_NAME = "com.gwtplatform.mvp.client.proxy.ProxyPlace";
    private static final String PROXY_STANDARD_CLASS_NAME = "com.gwtplatform.mvp.client.annotations.ProxyStandard";
    private static final String NAME_TOKEN_CLASS_NAME = "com.gwtplatform.mvp.client.annotations.NameToken";
    private static final String UI_BINDER_CLASS_NAME = "com.google.gwt.uibinder.client.UiBinder";
    private static final String WIDGET_CLASS_NAME = "com.google.gwt.user.client.ui.Widget";
    private static final String VIEW_IMPL_CLASS_NAME = "com.gwtplatform.mvp.client.ViewImpl";

    /**
     * GWT Constants
     */
    private static final String PROVIDER_CLASS_NAME = "com.google.inject.Provider";
    private static final String INJECT_ANNOTATION_CLASS_NAME = "com.google.inject.Inject";
    private static final String EVENTBUS_CLASS_NAME = "com.google.web.bindery.event.shared.EventBus";

    @Parameter(required = true)
    private String presenterName;

    @Parameter(required = true)
    private String resourcesPath;

    @Parameter(required = true)
    private String presentersPackage;

    @Parameter(required = true)
    private String nameTokensClass;

    @Parameter( required = true )
    private String injectorClassName;

    @Parameter( required = true )
    private String moduleClassName;

    @Parameter( required = true, property = "project" )
    private MavenProject project;

    @Parameter( required = true, defaultValue = "src/main/java")
    private String outputPath;

    @Parameter(required = true)
    private String presenterViewsPackage;

    @Parameter( defaultValue = "true" )
    private boolean generateView;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            File outputFile = new File(outputPath);
            if ( !outputFile.exists() ) {
                outputFile.mkdirs();
            }

            buildCodeModel().build( outputFile );
            generateUiBinderTemplate();
            updateReferences();
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException( e.getMessage(), e );
        } catch (IOException e) {
            throw new MojoFailureException( e.getMessage(), e );
        }
    }

    protected String toFilePath( String className ) {
        return className.replaceAll("\\.", "/");
    }

    protected String toClassName( String fileName ) {
        return toClassName("", fileName);
    }

    protected String toClassName( String sourceRoot, String fileName ) {
        String path = fileName.substring( fileName.indexOf(sourceRoot ) + sourceRoot.length() )
                .replaceAll("(" + Pattern.quote("/") + "|\\\\" + ")", ".").replace(".class", "")
                .replace(".java", "");
        while ( path.startsWith(".") ) {
            path = path.substring(1);
        }

        return path;
    }

    protected void generateUiBinderTemplate() {
        File resourcesDirectory = new File( resourcesPath );
        if ( !resourcesDirectory.exists() ) {
            resourcesDirectory.mkdirs();
        }

        File resourcesTemplatePath = new File(resourcesDirectory,
                toFilePath( getPresenterViewsPackage()  ) );
        if ( !resourcesTemplatePath.exists() ) {
            resourcesTemplatePath.mkdirs();
        }

        File viewTemplateFile = new File( resourcesTemplatePath, generateViewName() + ".gwt.xml");
        try {
            if ( !viewTemplateFile.exists() ) {
                viewTemplateFile.createNewFile();
            }

            OutputStreamWriter writer = new OutputStreamWriter( new FileOutputStream(viewTemplateFile) );
            try {
                writer.write( loadUiBinderTemplate() );
                writer.flush();
            } finally {
                writer.close();
            }
        } catch ( IOException e ) {
            throw new IllegalStateException( e.getMessage(), e );
        }
    }

    protected String loadUiBinderTemplate() throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader( getClass().getClassLoader().getResourceAsStream("UiBinder.Template.xml") )
        );
        StringBuilder builder = new StringBuilder();
        String tmp;
        while ( null != ( tmp = reader.readLine() ) ) {
            builder.append( tmp ).append("\n");
        }

        return builder.toString();
    }

    protected void updateReferences() throws IOException {
        List<String> sourceRoots = project.getCompileSourceRoots();

        final JavaDocBuilder builder = new JavaDocBuilder();
        for ( final String sourceRoot : sourceRoots ) {
            DirectoryScanner scanner = new DirectoryScanner( new File(sourceRoot) );
            scanner.scan(new FileVisitor() {
                @Override
                public void visitFile(File file) {
                    try {
                        String className = toClassName(sourceRoot, file.getAbsolutePath());
                        if ( !className.equals( injectorClassName )
                                && !className.equals(moduleClassName)
                                && !className.equals(nameTokensClass) ) {
                            return;
                        }

                        builder.addSource( file );
                    } catch ( IOException e ) {
                        getLog().info("Failed to process file : " + file.getAbsolutePath(), e );
                    }
                }
            });
        }

        JavaClass injectorClazz = null;
        JavaClass moduleClazz = null;
        JavaClass nameTokensClazz = null;
        for ( JavaClass clazz : builder.getClasses() ) {
            if ( clazz.getFullyQualifiedName().equals( injectorClassName ) ) {
                injectorClazz = clazz;
            } else if ( clazz.getFullyQualifiedName().equals( moduleClassName ) ) {
                moduleClazz = clazz;
            } else if ( clazz.getFullyQualifiedName().equals(nameTokensClass) ) {
                nameTokensClazz = clazz;
            }
        }

        if ( moduleClazz != null ) {
            if ( updateModuleReferences(moduleClazz) ) {
                updateClassSource( moduleClazz );
            }
        } else {
            getLog().warn("Failed to found module class: " + moduleClassName );
        }

        if ( nameTokensClazz != null ) {
            if ( updateNameTokens(nameTokensClazz) ) {
                updateClassSource(nameTokensClazz);
            }
        } else {
            getLog().warn("Failed to found name tokens class: " + nameTokensClass );
        }

        if ( injectorClazz != null ) {
            if ( updateInjectorReferences(injectorClazz) ) {
                updateClassSource( injectorClazz );
            }
        } else {
            getLog().warn("Failed to found injector class by the given name: " + injectorClassName );
        }
    }

    protected void updateClassSource( JavaClass clazz ) throws IOException {
        getLog().info("Updating class: " + clazz.getFullyQualifiedName() );

        try {
            File file = new File( clazz.getSource().getURL().getPath() );
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter( new FileOutputStream(file) )
            );
            try {
                writer.write(clazz.getSource().toString());
                writer.flush();
            } finally {
                writer.close();
            }
        } catch ( IOException e ) {
            throw new IllegalStateException("Failed to update references in class :"
                    + clazz.getFullyQualifiedName() );
        }

        getLog().info("OK!");
    }

    protected boolean updateNameTokens( JavaClass nameTokensClazz ) {
        boolean changed = false;

        String tokenFieldName = getPresenterName().toUpperCase();

        if ( nameTokensClazz.getFieldByName(tokenFieldName) != null ) {
            getLog().debug("Name token field already exists. Skipping field creation...");
        } else {
            JavaField field = new JavaField();
            field.setName( getPresenterName().toUpperCase() );
            field.setType(new Type("String"));
            field.setModifiers(new String[]{"public", "static", "final"});
            field.setInitializationExpression("\"" + getPresenterName().toLowerCase() + "\"");
            nameTokensClazz.addField(field);
            changed = true;
        }

        String tokenAccessorName = "get" + getPresenterName();
        if ( nameTokensClazz.getMethodBySignature(tokenAccessorName, new Type[] {} ) != null ) {
            getLog().debug("Name token field accessor already exists. Skipping field accessor creation...");
        } else {
            JavaMethod method = new JavaMethod();
            method.setName("get" + getPresenterName() );
            method.setReturns(new Type("String"));
            method.setModifiers(new String[]{"static", "public", "final"});
            method.setSourceCode(" return " + tokenFieldName + ";" );
            nameTokensClazz.addMethod( method );
            changed = true;
        }

        return changed;
    }

    protected boolean updateModuleReferences( JavaClass moduleClazz ) {
        boolean changed = false;

        JavaMethod  method = moduleClazz.getMethodBySignature("configure", new Type[] {} );

        StringBuilder codeBlock = new StringBuilder();
        codeBlock.append("\n").append( "bindPresenter( " )
            .append( getPresentersPackage() ).append( "." ).append( generateClassName() ).append( ".class")
            .append(",")
            .append( generateViewInterfaceName() ).append(".class")
            .append(",")
            .append( generateViewPath() ).append( ".class" )
            .append(",")
            .append( generateProxyName() ).append(".class")
        .append(");\n");

        String codeBlockData = codeBlock.toString();
        if ( method.getSourceCode().contains(codeBlockData) ) {
            getLog().debug("Presenter already registered in the client module!");
        } else {
            method.setSourceCode( method.getSourceCode().concat( codeBlock.toString() ) );
            changed = true;
        }

        return changed;
    }

    protected boolean updateInjectorReferences( JavaClass injectorClazz ) {
        boolean changed = false;

        String presenterProviderMethodName = "get" + getPresenterName() + "Presenter";
        if ( injectorClazz.getMethodBySignature(presenterProviderMethodName, new Type[] {} ) == null ) {
            JavaMethod method = new JavaMethod(presenterProviderMethodName);
            method.setReturns( new Type(
                    PROVIDER_CLASS_NAME + "<" + getPresentersPackage() + "." + generateClassName() + ">"
            ));

            injectorClazz.addMethod(method);
            changed = true;
        } else {
            getLog().debug("Provider method for a " + getPresenterName() + " presenter already exists in " +
                    "the injector class. Skipping update...");
        }

        return changed;
    }
    
    protected JCodeModel buildCodeModel() throws JClassAlreadyExistsException {
        JCodeModel model = new JCodeModel();
        definePresenter(model);
        defineView(model);
        
        return model;
    }

    protected void definePresenter( JCodeModel model ) throws JClassAlreadyExistsException {
        JDefinedClass presenterClazz = model._package(presentersPackage)
                ._class( generateClassName() );
        presenterClazz._extends( model.ref( PRESENTER_CLASS_NAME).narrow(
                model.ref(generateViewInterfaceName()), model.ref(generateProxyName()) ) );

        defineViewInterface(model, presenterClazz);
        defineProxyInterface(model, presenterClazz);

        addConstructor(model, presenterClazz);
        addOnRevealMethod(model, presenterClazz);
        addOnBindMethod(model, presenterClazz);
        addUseManualRevealMethod(model, presenterClazz);
    }

    protected void defineView( JCodeModel model ) throws JClassAlreadyExistsException {
        JDefinedClass viewClazz = model._class(JMod.PUBLIC, generateViewPath(), ClassType.CLASS );
        viewClazz._extends( model.ref(VIEW_IMPL_CLASS_NAME) );
        viewClazz._implements( model.ref(generateViewInterfaceName()) );

        defineUiBinder(model, viewClazz);
        addViewConstructor( model, viewClazz );
    }

    protected JDefinedClass defineUiBinder( JCodeModel model, JDefinedClass viewClazz )
            throws JClassAlreadyExistsException {
        JDefinedClass uiBinderClazz = viewClazz._interface(JMod.PUBLIC, "Binder");

        uiBinderClazz._implements(
            model.ref(UI_BINDER_CLASS_NAME)
                .narrow( model.ref(WIDGET_CLASS_NAME) )
                .narrow( model.ref(viewClazz.name()) )
        );

        return uiBinderClazz;
    }

    protected void addViewConstructor( JCodeModel model, JDefinedClass viewClazz ) {
        JFieldVar eventBusField = viewClazz.field(JMod.FINAL | JMod.PRIVATE, model.ref(EVENTBUS_CLASS_NAME), "eventBus");
        JFieldVar widgetField = viewClazz.field( JMod.FINAL | JMod.PRIVATE, model.ref(WIDGET_CLASS_NAME), "widget");

        JMethod constructorMethod = viewClazz.constructor(JMod.PUBLIC);
        constructorMethod.annotate( model.ref(INJECT_ANNOTATION_CLASS_NAME) );

        JVar[] params = new JVar[2];
        params[0] = constructorMethod.param( JMod.FINAL, model.ref(EVENTBUS_CLASS_NAME), "eventBus" );
        params[1] = constructorMethod.param( JMod.FINAL, model.ref( viewClazz.fullName() + ".Binder"), "binder" );

        JBlock block = constructorMethod.body();
        block.assign( JExpr.refthis( eventBusField.name() ), params[0]);
        block.assign( JExpr.ref( widgetField.name() ), params[1].invoke("createAndBindUi").arg( JExpr._this() ) );
    }

    protected JDefinedClass defineViewInterface( JCodeModel model, JDefinedClass presenterClazz )
        throws JClassAlreadyExistsException {
        JDefinedClass viewInterface = presenterClazz._class(JMod.PUBLIC, "MyView", ClassType.INTERFACE );
        viewInterface._implements( model.ref(VIEW_CLASS_NAME) );
        return viewInterface;
    }

    protected JDefinedClass defineProxyInterface( JCodeModel model, JDefinedClass presenterClazz )
        throws JClassAlreadyExistsException {
        JDefinedClass proxyInterface = presenterClazz._class(JMod.PUBLIC, "MyProxy", ClassType.INTERFACE );
        proxyInterface._implements( model.ref(PROXY_CLASS_NAME).narrow( model.ref( presenterClazz.name() ) ) );
        proxyInterface.annotate( model.ref(NAME_TOKEN_CLASS_NAME) )
            .param("value", nameTokensClass + "." + getPresenterName().toUpperCase() );
        proxyInterface.annotate( model.ref(PROXY_STANDARD_CLASS_NAME) );
        return proxyInterface;
    }

    protected JMethod addUseManualRevealMethod( JCodeModel model, JDefinedClass presenterClazz ) {
        JMethod useManualRevealMethod = presenterClazz.method(JMod.PUBLIC, model.BOOLEAN, "useManualReveal");
        useManualRevealMethod.annotate( Override.class );
        useManualRevealMethod.body()._return( JExpr.lit(false) );
        return useManualRevealMethod;
    }

    protected JMethod addConstructor( JCodeModel model, JDefinedClass presenterClazz ) {
        JMethod constructorMethod = presenterClazz.constructor(JMod.PUBLIC);
        constructorMethod.annotate( model.ref(INJECT_ANNOTATION_CLASS_NAME) );
        JVar[] constructorVars = new JVar[3];
        constructorVars[0] = constructorMethod.param( JMod.FINAL, model.ref(EVENTBUS_CLASS_NAME), "eventBus" );
        constructorVars[1] = constructorMethod.param( JMod.FINAL, model.ref("MyView"), "view" );
        constructorVars[2] = constructorMethod.param( JMod.FINAL, model.ref("MyProxy"), "proxy" );

        JInvocation superInvoke = JExpr.invoke("super   ");
        superInvoke.arg( constructorVars[0] );
        superInvoke.arg( constructorVars[1] );
        superInvoke.arg( constructorVars[2] );

        constructorMethod.body().add( superInvoke );

        return constructorMethod;
    }

    protected JMethod addOnBindMethod( JCodeModel model, JDefinedClass presenterClazz ) {
        JMethod method = presenterClazz.method(JMod.PUBLIC, model.VOID, "onBind");
        method.annotate(Override.class);
        method.body().invoke( JExpr._super(), presenterClazz.getMethod("onBind", new JType[] {} ) );
        return method;
    }

    protected JMethod addOnRevealMethod( JCodeModel model, JDefinedClass presenterClazz ) {
        JMethod method = presenterClazz.method(JMod.PUBLIC, model.VOID, "onReveal");
        method.annotate(Override.class);
        return method;
    }

    protected String generateViewInterfaceName() {
        return presentersPackage + "." + generateClassName() + ".MyView";
    }

    protected String generateProxyName() {
        return presentersPackage + "." + generateClassName() + ".MyProxy";
    }

    protected String generateViewName() {
        return presenterName + "View";
    }

    protected String generateViewPath() {
        return presenterViewsPackage + "." + generateViewName();
    }

    protected String generateClassName() {
        return presenterName + "Presenter";
    }

    public String getPresenterName() {
        return presenterName;
    }

    public void setPresenterName(String presenterName) {
        this.presenterName = presenterName;
    }

    public String getPresentersPackage() {
        return presentersPackage;
    }

    public void setPresentersPackage(String presentersPackage) {
        this.presentersPackage = presentersPackage;
    }

    public String getPresenterViewsPackage() {
        return presenterViewsPackage;
    }

    public void setPresenterViewsPackage(String presenterViewsPackage) {
        this.presenterViewsPackage = presenterViewsPackage;
    }

    public boolean isGenerateView() {
        return generateView;
    }

    public void setGenerateView(boolean generateView) {
        this.generateView = generateView;
    }
}
