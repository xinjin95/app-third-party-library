package osu.seclab.libscope.stats;

import osu.seclab.libscope.manifest.ProcessManifest;

import java.io.File;
import java.util.Set;

public class AppStats implements Exportable{

    public File appFile;
    public ProcessManifest manifest;
    public boolean isMultiDex;

    public AppStats(File appFile) {
        this.appFile = appFile;
    }

    @Override
    public Export export() {
        return new Export(this);
    }

    private class Export {
        public Export(AppStats appStats) {
        }

        class AppInfo {
            String fileName;
            String appName;
            String packagename;
            Set<String> permissions;
            int versionCode;
            int versionMinSDK;
            int versionTargetSDK;
            String sharedUserId;
        }

        AppInfo appInfo = new AppInfo();

        int stats_packageCount;
        int stats_classCount;
        long stats_processingTime;
    }
}
