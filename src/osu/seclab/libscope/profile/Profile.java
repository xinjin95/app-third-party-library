package osu.seclab.libscope.profile;

import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import osu.seclab.libscope.Utils.Utils;
import osu.seclab.libscope.pkg.PackageTree;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class Profile implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(Profile.class);

    public static PackageTree generatePackageTree(IClassHierarchy cha) {
        logger.info("= PackageTree =");
        PackageTree tree = PackageTree.make(cha, true);
        tree.print(true);

        logger.debug("");
        logger.debug("Package names (included classes):");
        Map<String,Integer> pTree = tree.getPackages();
//        Map<String, Set<String>> packageClasses = tree.getPackageClasses();
        for (String pkg: pTree.keySet()) {
            logger.debug(Utils.INDENT + pkg + " (" + pTree.get(pkg) + ")");
//            if (packageClasses.containsKey(pkg) && packageClasses.get(pkg) != null) {
//                logger.debug(Utils.INDENT + Utils.INDENT + pkg + " (" + packageClasses.get(pkg).size() + ")");
//            }
        }
        logger.info("");

        return tree;
    }


}
