package osu.seclab.libscope.Utils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WalaUtils {

    private static final Logger logger = LoggerFactory.getLogger(WalaUtils.class);

    public static final String WALA_FAKE_ROOT_CLASS = "com.ibm.wala.FakeRootClass";

    public static JSONObject getChaStats(IClassHierarchy cha) {
        TreeSet<String> publicMethods = new TreeSet<>();
        int clCount = 0;
        int innerClCount = 0;
        int publicClCount = 0;
        int miscMethodCount = 0;

        HashMap<AndroidClassType, Integer> clazzTypes = new HashMap<>();
        for (AndroidClassType t: AndroidClassType.values())
            clazzTypes.put(t, 0);

        for (IClass clazz: cha) {
            if (isAppClass(clazz)) {
                AndroidClassType type = classifyClass(clazz);
                clazzTypes.put(type, clazzTypes.get(type)+1);
                logger.trace("App Class: " + simpleName(clazz) + "  (" + type + ")");
                clCount++;
                if (isInnerClass(clazz)) {
                    innerClCount++;
                }
                if (clazz.isPublic()) {
                    publicClCount++;
                }
                for (IMethod im: clazz.getDeclaredMethods()) {
                    if (im.isBridge() || im.isSynthetic()) continue;

                    if (im.isPublic()) {
                        publicMethods.add(im.getSignature());
                    } else {
                        miscMethodCount++;
                    }
                }
            }
        }

        logger.info("");
        logger.info("= ClassHierarchy Stats =");
        logger.info(Utils.INDENT + "# of classes: " + clCount);
        logger.info(Utils.INDENT + "# thereof inner classes: " + innerClCount);
        logger.info(Utils.INDENT + "# thereof public classes: " + publicClCount);
        for (AndroidClassType t: AndroidClassType.values())
            logger.info(Utils.INDENT2 + t + " : " + clazzTypes.get(t));
        logger.info(Utils.INDENT + "# methods: " + (publicMethods.size() + miscMethodCount));
        logger.info(Utils.INDENT2 + "# of publicly accessible methods: " + publicMethods.size());
        logger.info(Utils.INDENT2 + "# of non-accessible methods: " + miscMethodCount);
        logger.info("");

        JSONObject js = new JSONObject();
        js.put("numClasses", clCount);
        js.put("numInnerClasses", innerClCount);
        js.put("numPublicClasses", publicClCount);
        js.put("numMethods", (publicMethods.size() + miscMethodCount));
        js.put("numPublicMethods", publicMethods.size());
        js.put("numMiscMethods", miscMethodCount);
        return js;
    }

    private static AndroidClassType classifyClass(IClass clazz) {
        for (IClass c : getSuperClassesIncluding(clazz)) {
            String className = simpleName(c);
            switch (className) {
                case AndroidEntryPointConstants.ACTIVITYCLASS:
                    return AndroidClassType.Activity;
                case AndroidEntryPointConstants.FRAGMENTCLASS:
                case AndroidEntryPointConstants.SUPPORTFRAGMENTCLASS:
                case AndroidEntryPointConstants.ANDROIDX_FRAGMENT:
                    return AndroidClassType.Fragment;
                case AndroidEntryPointConstants.SERVICECLASS:
                    return AndroidClassType.Service;
                case AndroidEntryPointConstants.BROADCASTRECEIVERCLASS:
                    return AndroidClassType.BroadcastReceiver;
                case AndroidEntryPointConstants.CONTENTPROVIDERCLASS:
                    return AndroidClassType.ContentProvider;
                case AndroidEntryPointConstants.APPLICATIONCLASS:
                    return AndroidClassType.Application;
                case AndroidEntryPointConstants.ASYNCTASKCLASS:
                    return AndroidClassType.AsyncTask;
                case AndroidEntryPointConstants.THREADCLASS:
                    return AndroidClassType.Thread;
                case AndroidEntryPointConstants.RUNNABLECLASS:
                    return AndroidClassType.Runnable;
                case AndroidEntryPointConstants.HANDLERCLASS:
                    return AndroidClassType.Handler;
                case AndroidEntryPointConstants.VIEWGROUP_TYPE:
                    return AndroidClassType.LayoutContainer;
                case AndroidEntryPointConstants.VIEW_TYPE:
                case AndroidEntryPointConstants.WEBVIEW_TYPE:
                    return AndroidClassType.View;
                default:
                    return AndroidClassType.Plain;
            }
        }
        return AndroidClassType.Plain;
    }

    public static List<IClass> getSuperClassesIncluding(IClass clazz) {
        LinkedList<IClass> superclasses = new LinkedList<IClass>(getSuperClasses(clazz));
        superclasses.addFirst(clazz);

        return superclasses;
    }

    public static List<IClass> getSuperClasses(IClass clazz) {
        ArrayList<IClass> superclasses = new ArrayList<IClass>();

        while (clazz.getSuperclass() != null) {
            clazz = clazz.getSuperclass();
            superclasses.add(clazz);
        }

        return superclasses;
    }

    public static boolean isAppClass(IClass clazz) {
        boolean isEmptyInnerClass = WalaUtils.isInnerClass(clazz)
                && isAnonymousInnerClass(clazz)
                && (clazz.getDeclaredMethods().isEmpty() ||
                (clazz.getDeclaredMethods().size() == 1 && clazz.getDeclaredMethods().iterator().next().isClinit())
                        && clazz.getDeclaredInstanceFields().isEmpty()
                        && clazz.getDeclaredStaticFields().isEmpty()
                        && clazz.getDirectInterfaces().isEmpty());

//        logger.info(simpleName(clazz) + ",\n\t isEmptyInnerClass=" + !isEmptyInnerClass +
//                ",\n\t isApplicationLoader=" + clazz.getClassHierarchy().getScope().isApplicationLoader(clazz.getClassLoader())
//                + ",\n\t isAndroidResourceClass=" + !isAndroidResourceClass(clazz)
//                + ",\n\t isSynthetic=" + !clazz.isSynthetic());

        return clazz.getClassHierarchy().getScope().isApplicationLoader(clazz.getClassLoader()) && !isAndroidResourceClass(clazz) && !isEmptyInnerClass && !clazz.isSynthetic();
    }

    private static boolean isAndroidResourceClass(IClass clazz) {
        // match R and BuildConfig class and their inner classes
        String clazzName = getClassName(clazz);
        return clazzName.equals("R") || clazzName.startsWith("R$") || clazzName.equals("BuildConfig");

    }

    private static boolean isInnerClass(IClass clazz) {
        return getClassName(clazz).contains("$");
    }

    private static String getClassName(IClass clazz) {
        String clazzName = clazz.getName().toString().substring(clazz.getName().toString().lastIndexOf("/")+1);
        return clazzName.endsWith(";")? clazzName.substring(0, clazzName.length()-1) : clazzName;
    }

    public static boolean isAnonymousInnerClass(final IClass clazz) {
        return isAnonymousInnerClass(simpleName(clazz));
    }

    private static boolean isAnonymousInnerClass(final String clazzName) {
        final Pattern anonymousInnerClassPattern = Pattern.compile("^.+\\$[0-9]+$");
        final Matcher matcher = anonymousInnerClassPattern.matcher(clazzName);

        return matcher.matches();
    }

    public static String simpleName(IClass c) {
        return c == null? "null" : Utils.convertToFullClassName(c.getName().toString());
    }
}
