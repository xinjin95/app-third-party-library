package osu.seclab.libscope.pkg;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import osu.seclab.libscope.Utils.PackageUtils;
import osu.seclab.libscope.Utils.WalaUtils;

import java.io.Serializable;
import java.util.*;

public class PackageTree implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(PackageTree.class);

    private Node rootNode;
    private static Map<String, Set<String>> packageClasses = new HashMap<>(0);

    public class Node implements Serializable {
        private static final long serialVersionUID = -2117889548993263279L;

        public String name;
        public int clazzCount;
        public List<Node> childs;

        public Node(String name) {
            this.name = name;
            this.clazzCount = 0;
            this.childs = new ArrayList<Node>();
        }

        public int getNumberOfLeafNodes() {
            int result = 0;
            for (Node child: childs)
                if (child.isLeaf()) result++;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Node))
                return false;
            return ((Node) obj).name.equals(this.name);
        }

        public void print(boolean includeClazzCount) {
            print("", true, includeClazzCount, drawingCharacters.get(false));
        }

        final Map<Boolean, String[]> drawingCharacters = new HashMap<Boolean, String[]>() {{
            // unicode box-drawing characters ("└── ",  "├── ", "│   ")
            put(false, new String[]{"\u2514\u2500\u2500 ", "\u251C\u2500\u2500 ", "\u2502   "});

            // ascii characters
            put(true , new String[]{"|___ ", "|--- ", "|   "});
        }};

        private void print(String prefix, boolean isTail, boolean includeClazzCount, final String[] charset) {
            logger.info(prefix + (isTail ? charset[0] : charset[1]) + name + (includeClazzCount && clazzCount > 0? " (" + clazzCount + ")" : ""));

            for (int i = 0; i < childs.size(); i++) {
                childs.get(i).print(prefix + (isTail ? "    " : charset[2]), i == childs.size()-1, includeClazzCount, charset);
            }
        }

        @Override
        public String toString() {
            return this.name;
        }

        public boolean hasClasses() {
            return this.clazzCount > 0;
        }

        public boolean isLeaf() {
            return childs.isEmpty();
        }
    }

    private static void recordClass(IClass clazz) {
        String fullClassName = WalaUtils.simpleName(clazz);
        List<String> struct = PackageUtils.parsePackage(fullClassName, true);
        String packageName = String.join(".", struct.subList(0, struct.size()-1));
        String className = struct.get(struct.size()-1);
        if (!packageClasses.containsKey(packageName)) {
            packageClasses.put(packageName, new HashSet<>());
        }
        packageClasses.get(packageName).add(className);
    }

    public static PackageTree make(IClassHierarchy cha, boolean appClassesOnly) {
        return make(cha, appClassesOnly, null);
    }

    public Map<String, Set<String>> getThirdPartyClasses(String appPackageName) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry: getPackageClasses().entrySet()) {
            String packageName = entry.getKey();
            if (packageName != null && !packageName.equals("")) {
                if (appPackageName == null || appPackageName.equals("") || !packageName.contains(appPackageName)) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    public static PackageTree make(IClassHierarchy cha, boolean appClassesOnly, Set<String> filteredPackages) {
        PackageTree tree = new PackageTree();
        for (IClass clazz: cha) {
            if (!appClassesOnly || (appClassesOnly && WalaUtils.isAppClass(clazz))) {
                if (filteredPackages == null || !filteredPackages.contains(PackageUtils.getPackageName(clazz))) {
                    tree.update(clazz);
                    recordClass(clazz);
                }
            }
        }
        return tree;

    }

    public boolean update(IClass clazz) {
        List<String> struct = PackageUtils.parsePackage(clazz);
        return update(struct);
    }

    private PackageTree() {
        this.rootNode = new Node("Root");
    }

    private boolean update(List<String> packageStruct) {
        // update
        Node curNode = rootNode;
        if (packageStruct.isEmpty())
            curNode.clazzCount++;
        else {
            for (int i = 0; i < packageStruct.size(); i++) {
                Node n = matchChilds(curNode, packageStruct.get(i));

                if (n != null) {
                    curNode = n;
                } else {
                    Node newNode = new Node(packageStruct.get(i));
                    curNode.childs.add(newNode);
                    curNode = newNode;
                }

                if (i == packageStruct.size()-1) {
                    curNode.clazzCount++;
                }
            }
        }

        return true;
    }

    private Node matchChilds(Node n, String str) {
        for (Node node: n.childs) {
            if (node.name.equals(str))
                return node;
        }
        return null;
    }

    public void print(boolean includeClazzCount) {
        logger.info("Root Package: " + (getRootPackage() == null? " - none -" : getRootPackage()));

        if (rootNode.childs.size() == 1 && !rootNode.hasClasses())
            rootNode.childs.get(0).print(includeClazzCount);
        else
            rootNode.print(includeClazzCount);
    }

    /**
     * Determine root package of the tree (if any). Expands to the longest unique package name.
     * Note: This method only works for libraries. It's not applicable to apps since there are many different namespaces/libraries involved.
     * @return  the unique root package name or null otherwise
     */
    public String getRootPackage() {
        String rootPackage = "";
        Node curNode = rootNode;

        // This is another heuristic to determine the proper root package in presence of another lib dependency
        // whose package name differs at depth 1 or at depth 2 if depth 1 is some common namespace
        if (rootNode.childs.size() > 1 ||
                (rootNode.childs.size() == 1 && (rootNode.childs.get(0).name.equals("com") ||
                        rootNode.childs.get(0).name.equals("de") ||
                        rootNode.childs.get(0).name.equals("org")))) {

            if (rootNode.childs.size() == 1) {
                curNode = rootNode.childs.get(0);
                rootPackage += curNode.name;
            }

            int id = 0;
            int max = 0;
            // determine largest subtree in terms of packages
            for (int i = 0; i < curNode.childs.size(); i++) {
                int tmp = getPackages(curNode.childs.get(i), "", true).size();
                if (tmp > max) {
                    id = i;
                    max = tmp;
                }
            }

            curNode = curNode.childs.get(id);
            rootPackage += (rootPackage.isEmpty()? "" : ".") + curNode.name;

            if (curNode.hasClasses())
                return rootPackage.isEmpty()? null : rootPackage;
        }

        while (curNode.childs.size() == 1) {
            curNode = curNode.childs.get(0);
            rootPackage += (rootPackage.isEmpty()? "" : ".") + curNode.name;

            if (curNode.hasClasses()) break;
        }

        // disallow incomplete root packages of depth 1 that start with common namespace
        if (rootPackage.equals("com") || rootPackage.equals("de") || rootPackage.equals("org")) {
            rootPackage = "";
        }

        return rootPackage.isEmpty()? null : rootPackage;
    }

    private Map<String, Integer> getPackages(Node n, String curPath, boolean dumpAllPackages) {
        TreeMap<String, Integer> res = new TreeMap<String, Integer>();

        if (n.hasClasses() || dumpAllPackages)
            res.put(curPath + n.name, n.clazzCount);

        if (!n.isLeaf()) {
            for (Node c: n.childs) {
                res.putAll(getPackages(c, curPath + (n.name.equals("Root")? "" : n.name + "."), dumpAllPackages));
            }
        }

        return res;
    }

    /**
     * Dump package names that contain at least one class
     * @return  a mapping from package name to number of included classes
     */
    public Map<String, Integer> getPackages() {
        return getPackages(rootNode, "", false);
    }

    public Map<String, Set<String>> getPackageClasses() {
        return packageClasses;
    }
}
