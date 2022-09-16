package osu.seclab.libscope.manifest;

import android.content.res.AXmlResourceParser;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import osu.seclab.libscope.Utils.Utils;
import pxb.android.axml.AXMLPrinter;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProcessManifest implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(ProcessManifest.class);
    private Set<String> entryPointsClasses = new HashSet<String>();
    private String packageName = "";
    private int versionCode = 0;
    private int minSdkVersion = 1;  // if not explicitly set, defaults to 1
    private int targetSdkVersion = 1;  // if not explicitly set, defaults to minSdkValue
    private String sharedUserId = "";
    private String applicationName = "";
    private Set<String> permissions = new TreeSet<String>();
    private Set<String> libDependencies = new HashSet<String>();
    private static final long serialVersionUID = -6763632946511685516L;
    public final String MANIFEST_FILENAME = "AndroidManifest.xml";

    private void handleAndroidManifestFile(String apkPath, IManifestHandler handler) {
        File apkFile = new File(apkPath);
        if (!apkFile.exists()) {
            logger.error("File '" + apkPath + "' does not exist");
            throw new RuntimeException("File '" + apkPath + "' does not exist");
        }
        boolean found = false;
        try {
            ZipFile archive = null;
            try {
                archive = new ZipFile(apkFile);
                Enumeration<?> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    String entryName = entry.getName();
                    // We are dealing with the Android manifest
                    if (entryName.equals(MANIFEST_FILENAME)) {
                        found = true;
                        handler.handleManifest(archive.getInputStream(entry));
                        break;
                    }
                }
            } finally {
                if (archive != null)
                    archive.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error when looking for manifest in apk: " + e);
        }
        if (!found) {
            logger.error("No manifest file found in apk");
            throw new RuntimeException("No manifest file found in apk");
        }
    }

    public void loadManifestFile(String apkPath) {
        handleAndroidManifestFile(apkPath, new IManifestHandler() {
            @Override
            public void handleManifest(InputStream stream) {
                loadClassesFromBinaryManifest(stream);
            }
        });
    }



    protected void loadClassesFromBinaryManifest(InputStream manifestIS) {
        try {
            AXmlResourceParser parser = new AXmlResourceParser();
            parser.open(manifestIS);

            int type = -1;
            boolean applicationEnabled = true;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                switch (type) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.START_TAG:
                        String tagName = parser.getName();
                        if (tagName.equals("manifest")) {
                            this.packageName = getAttributeValue(parser, "package");
                            this.sharedUserId = getAttributeValue(parser, "sharedUserId");
                            try {
                                this.versionCode = Integer.parseInt(getAttributeValue(parser, "versionCode"));
                            } catch (NumberFormatException e) {
                                logger.warn("Could not parse versionCode: " + getAttributeValue(parser, "versionCode"));
                            }
                        } else if (tagName.equals("activity")
                                || tagName.equals("receiver")
                                || tagName.equals("service")
                                || tagName.equals("provider")) {
                            // We ignore disabled activities
                            if (!applicationEnabled)
                                continue;
                            String attrValue = getAttributeValue(parser, "enabled");
                            if (attrValue != null && attrValue.equals("false"))
                                continue;

                            // Get the class name
                            attrValue = getAttributeValue(parser, "name");
                            entryPointsClasses.add(expandClassName(attrValue));
                        } else if (tagName.equals("uses-permission") || tagName.equals("permission")) {
                            String permissionName = getAttributeValue(parser, "name");
                            // We probably don't want to do this in some cases, so leave it
                            // to the user
                            // permissionName = permissionName.substring(permissionName.lastIndexOf(".") + 1);
                            this.permissions.add(permissionName);
                        } else if (tagName.equals("uses-library")) {
                            String libraryName = getAttributeValue(parser, "name");
                            this.libDependencies.add(libraryName);
                        } else if (tagName.equals("uses-sdk")) {
                            try {
                                this.minSdkVersion = Integer.parseInt(getAttributeValue(parser, "minSdkVersion"));
                            } catch (NumberFormatException e) {
                                logger.warn("Could not parse minSdkVersion: " + getAttributeValue(parser, "minSdkVersion"));
                            }
                            try {
                                this.targetSdkVersion = Integer.parseInt(getAttributeValue(parser, "targetSdkVersion"));
                            } catch (NumberFormatException e) { /* targetSdkValue is optional */	}

                        } else if (tagName.equals("application")) {
                            // Check whether the application is disabled
                            String attrValue = getAttributeValue(parser, "enabled");
                            applicationEnabled = (attrValue == null || !attrValue.equals("false"));

                            // Get the application name which is also the fully-qualified
                            // name of the custom application object
                            this.applicationName = getAttributeValue(parser, "name");
                            if (this.applicationName != null && !this.applicationName.isEmpty())
                                this.entryPointsClasses.add(expandClassName(this.applicationName));
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getAttributeValue(AXmlResourceParser parser, String attributeName) {
        for (int i = 0; i < parser.getAttributeCount(); i++)
            if (parser.getAttributeName(i).equals(attributeName))
                return AXMLPrinter.getAttributeValue(parser, i);
        return "";
    }

    /**
     * Generates a full class name from a short class name by appending the
     * globally-defined package when necessary
     * @param className The class name to expand
     * @return The expanded class name for the given short name
     */
    private String expandClassName(String className) {
        if (className.startsWith(".")) {
            return this.packageName + className;
        } else if (!className.contains(".")) {  // if only the classname is present without leading dot, Android's manifest parser safely expands the class name as if there was a leading dot
            return this.packageName + "." + className;
        } else {
            return className;
        }
    }

    public String getApplicationName() {
        return this.applicationName;
    }

    public Set<String> getPermissions() {
        return this.permissions;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public int getVersionCode() {
        return this.versionCode;
    }

    public String getSharedUserId() {
        return this.sharedUserId;
    }

    public Set<String> getLibraryDependencies() {
        return this.libDependencies;
    }

    public JSONObject toJson() {
        JSONObject js = new JSONObject();
        js.put("packageName", getPackageName());
        js.put("versionCode", getVersionCode());
        js.put("minSdkVersion", getMinSdkVersion());
        js.put("targetSdkVersion", getTargetSdkVersion());
        js.put("sharedUserId", getSharedUserId());
        js.put("libraryDependencies", getLibraryDependencies());
        js.put("permissions", getPermissions());
        return js;
    }

    public int getMinSdkVersion() { return this.minSdkVersion; }

    public int getTargetSdkVersion() { return this.targetSdkVersion > 1? this.targetSdkVersion : this.minSdkVersion; }
}
