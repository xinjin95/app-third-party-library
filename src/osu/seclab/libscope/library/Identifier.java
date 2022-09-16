package osu.seclab.libscope.library;

import com.ibm.wala.dalvik.util.AndroidAnalysisScope;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import osu.seclab.libscope.Utils.ApkUtils;
import osu.seclab.libscope.Utils.FileUtility;
import osu.seclab.libscope.Utils.Utils;
import osu.seclab.libscope.Utils.WalaUtils;
import osu.seclab.libscope.main.Config;
import osu.seclab.libscope.main.runTest;
import osu.seclab.libscope.manifest.ProcessManifest;
import osu.seclab.libscope.pkg.PackageTree;
import osu.seclab.libscope.profile.Profile;
import osu.seclab.libscope.stats.AppStats;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class Identifier {
    private static Logger logger = LoggerFactory.getLogger(Identifier.class);
    private IClassHierarchy cha;
    private AppStats stats;

    public Identifier(File appFile) {
        this.stats = new AppStats(appFile);
    }

    public static AppStats run(File appFile) throws ClassHierarchyException, IOException {
        Identifier identifier = new Identifier(appFile);
        return identifier.identifyLibraries();
    }

    private AppStats identifyLibraries() throws ClassHierarchyException, IOException {
        long startTime = System.currentTimeMillis();

        logger.info("Process apk: " + stats.appFile.getName());

        stats.manifest = parseManifest(stats.appFile);

        JSONObject chaStatsJSON = createClassHierarchy();

        PackageTree packageTree = generatePackageClassTree();

        recordResults(chaStatsJSON, packageTree);

        return null;
    }

    private void recordResults(JSONObject chaStatsJSON, PackageTree packageTree) {
        Map<String, Set<String>> thirdPartyLibs = packageTree.getThirdPartyClasses(stats.manifest.getPackageName());
        JSONObject js = new JSONObject(), tmp = new JSONObject();

        int totalThirdPartyClasses = 0;
        for (Map.Entry<String, Set<String>> entry: thirdPartyLibs.entrySet()) {
            tmp.put(entry.getKey(), entry.getValue());
            totalThirdPartyClasses = totalThirdPartyClasses + entry.getValue().size();
        }
        chaStatsJSON.put("numThirdPartyPackages", thirdPartyLibs.entrySet().size());
        chaStatsJSON.put("numThirdPartyClasses", totalThirdPartyClasses);
        js.put("manifest", stats.manifest.toJson());
        js.put("chaStats", chaStatsJSON);
        js.put("thirdPartyLibs", tmp);
        FileUtility.wf(runTest.outputDir + stats.manifest.getPackageName() + ".txt", js.toString(), false);
    }

    private PackageTree generatePackageClassTree() {
        long startTime = System.currentTimeMillis();

        // generate app package tree
        PackageTree packageTree = Profile.generatePackageTree(cha);
        logger.info("- generated app package tree (in " + Utils.millisecondsToFormattedTime(System.currentTimeMillis() - startTime) + ")");
        logger.info("");

        return packageTree;
    }

    private JSONObject createClassHierarchy() throws IOException, ClassHierarchyException {
        long startTime = System.currentTimeMillis();

        stats.isMultiDex = ApkUtils.isMultiDexApk(stats.appFile);

        if (stats.isMultiDex) {
            logger.info("Multi-dex apk detected - Code is merged to single class hierarchy!");
        }

        final AnalysisScope scope = AndroidAnalysisScope.setUpAndroidAnalysisScope(new File(stats.appFile.getAbsolutePath()).toURI(), null, null, new File(Config.ANDROID_JAR).toURI());

        cha = ClassHierarchyFactory.make(scope);

        logger.info("Generated class hierarchy (in " + Utils.millisecondsToFormattedTime(System.currentTimeMillis() - startTime) + ")");

        return WalaUtils.getChaStats(cha);
    }

    private ProcessManifest parseManifest(File appFile) {
        ProcessManifest processManifest = new ProcessManifest();
        processManifest.loadManifestFile(appFile.getAbsolutePath());
        logger.info("= Manifest Parser =");
        logger.info(Utils.INDENT + "    Package name: " + processManifest.getPackageName());
        logger.info(Utils.INDENT + "    Version code: " + processManifest.getVersionCode());
        logger.info(Utils.INDENT + "   minSdkVersion: " + processManifest.getMinSdkVersion());
        logger.info(Utils.INDENT + "targetSdkVersion: " + processManifest.getTargetSdkVersion());
        logger.info(Utils.INDENT + "    SharedUserId: " + (processManifest.getSharedUserId().isEmpty()? " - none -" : processManifest.getSharedUserId()));

        logger.info(Utils.INDENT + "Library dependencies:" + (processManifest.getLibraryDependencies().isEmpty()? "  - none -" : ""));

        for (String libDep: processManifest.getLibraryDependencies())
            logger.debug(Utils.INDENT2 + "- " + libDep);

        logger.debug(Utils.INDENT + "Declared permissions: " + (processManifest.getPermissions().isEmpty()? " - none -" : ""));
        for (String p: processManifest.getPermissions()) logger.debug(Utils.INDENT2 + "# " + p);
        logger.info("");

        return processManifest;
    }
}
