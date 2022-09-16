# About

This is a lightweight tool, which only takes ~10 sec for an app in 70MB, for identifying the third-party libraries used in the mobile apps.
Sepcifically, this tool scans the packages in the dex file inside the APK files, 
and then removes the packages belonging to Android framework SDK
(android.jar) and those developed by app owners.

## Usage

The `jar` files are provided and the script for parsing a single file.

Under the [script/jar_exe](script/jar_exe) directory, we provide 
the `LibScope.jar` file and the dependencies under [script/jar_exe/lib](script/jar_exe/lib).

To run this tool, you can simply run:

```bash
cd script/jar_exe
java -jar LibScope.jar path_to_apk result_dir log_dir
```

where `path_to_apk` is the path to the mobile app APK file, `result_dir` is the directory of results, 
and `log_dir` is the directory of logs.

For example, by running the sample apps in [script/apks](script/apks), we obtain the following results.
```json
{
    "thirdPartyLibs": {
        "ch.qos.logback.classic.spi": [
            "LoggerContextAwareBase",
            "LoggerContextListener",
            ...
        ],
        ...
    },
    "manifest": {
        "minSdkVersion": 26,
        "sharedUserId": "",
        "permissions": [
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            ...
        ],
        "packageName": "com.august.luna",
        "targetSdkVersion": 29,
        "versionCode": 210426272,
        "libraryDependencies": [
            "org.apache.http.legacy"
        ],
    },
    "chaStats": {
        "numThirdPartyClasses": 24570,
        "numPublicMethods": 189875,
        "numPublicClasses": 30073,
        "numMiscMethods": 2405,
        "numMethods": 192280,
        "numClasses": 30073,
        "numInnerClasses": 14565,
        "numThirdPartyPackages": 1361
    }
}
```
where `ch.qos.logback.classic.spi` is the package name (you can refer to [this link](https://docs.oracle.com/javase/tutorial/java/concepts/package.html) for the definition of package name), and `LoggerContextAwareBase` is the class name. Basically, `ch.qos.logback.classic.spi.LoggerContextAwareBase.java` is the corresponding java source file.

## Installation

If you are interested in installing this tool, we provide the source code. And it was developed and tested on IntelliJ.

## Acknowledgement

This tool is developed based on [LibScout](https://github.com/reddr/LibScout) (a tool for identifying library vulnerabilities).

## Citation

If you find this tool useful, please consider to cite our paper [IoTSpotter](https://www.adwaitnadkarni.com/downloads/manandhar-ccs22.pdf) (bib coming soon):

```plaintext

```