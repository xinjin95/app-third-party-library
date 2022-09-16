package osu.seclab.libscope.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import osu.seclab.libscope.Utils.FileUtility;
import osu.seclab.libscope.Utils.Utils;
import osu.seclab.libscope.library.Identifier;
import java.util.Date;

import java.io.File;

public class runTest {
    public static String apkPath;
    public static String outputDir;
    public static String parseLogPath;
    private static final Logger logger = LoggerFactory.getLogger(runTest.class);
    private static String packageName;

    public static void main(String[] args) {

        apkPath = args[0];
        outputDir = args[1];
        parseLogPath = args[2];
//        apkPath = "/home/xin/Documents/project/iot_measurement/apks/com.limebike.apk";
//        outputDir = "/home/xin/Documents/code/python/iot-measure/third_party_library/data/results/";
//        parseLogPath = "/home/xin/Documents/code/python/iot-measure/third_party_library/data/log/";


        try{
            Identifier.run(new File(apkPath));
        } catch (Throwable t) {
            String content;
            Date data = new Date();
            content = "****************************************" + data.toString() + "****************************************";
            logger.error(content);
            FileUtility.wf(runTest.parseLogPath + getPackageName() + ".txt", content, true);
            content = "[FATAL " + (t instanceof Exception? "EXCEPTION" : "ERROR") + "] analysis aborted: " + t.getMessage();
            logger.error(content);
            FileUtility.wf(runTest.parseLogPath + getPackageName() + ".txt", content, true);
            content = Utils.stacktrace2Str(t);
            logger.error(content);
            FileUtility.wf(runTest.parseLogPath + getPackageName() + ".txt", content, true);
        }

    }

    public static String getPackageName() {
        return packageName;
    }

    private static void extractName(){
        String[] paths = apkPath.split("/");
        packageName = paths[paths.length-1].substring(0, paths[paths.length-1].length()-4);
    }
}
