package kfd.util;

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.io.IOException;

import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;

import soot.options.Options;

import soot.SootMethod;
import soot.Scene;
import soot.PackManager;

import org.xmlpull.v1.XmlPullParserException;

public class SootCallGraphBuilder {
    private final String androidSdkPath;
    //private final String androidSupportV4Path;
    private final String SourcesAndSinksFilePath;
    private final String MethodsToAnalyzeFilePath;
    private final String appPath;

    public SootCallGraphBuilder(String _appPath, String _androidSdkPath, String _SourcesAndSinksFilePath, String _MethodsToAnalyzeFilePath) {
        this.androidSdkPath = _androidSdkPath;
        this.SourcesAndSinksFilePath = _SourcesAndSinksFilePath; 
        this.MethodsToAnalyzeFilePath = _MethodsToAnalyzeFilePath;
        this.appPath = _appPath;
    }

    public CallGraph createCallGraph() {
        SetupApplication app = new SetupApplication
            (androidSdkPath,
             appPath);

        try {
            app.calculateSourcesSinksEntrypoints(SourcesAndSinksFilePath);
        }
        catch (IOException ex){
            System.err.println("Could not read file: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        catch (XmlPullParserException ex) {
            System.err.println("Could not read Android manifest file: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        soot.G.reset();

        List<String> excludeList = new LinkedList<String>();
        excludeList.add("java.*");
        excludeList.add("sun.misc.*");
        excludeList.add("android.*");
        excludeList.add("org.apache.*");
        excludeList.add("soot.*");
        excludeList.add("javax.servlet.*");
        Options.v().set_exclude(excludeList);   
        Options.v().set_no_bodies_for_excluded(true);

        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(Collections.singletonList(appPath));
        Options.v().set_force_android_jar(androidSdkPath);
        //Options.v().set_soot_classpath(androidSupportV4Path);
        Options.v().set_whole_program(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().setPhaseOption("cg.spark", "on");

        Scene.v().loadNecessaryClasses();

        SootMethod entryPoint = app.getEntryPointCreator().createDummyMain();
        Options.v().set_main_class(entryPoint.getSignature());
        Scene.v().setEntryPoints(Collections.singletonList(entryPoint));

        PackManager.v().runPacks();

        CallGraph cg = Scene.v().getCallGraph();

        return cg;
    }
}
