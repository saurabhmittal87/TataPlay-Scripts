package com.tataplay.scripts.branches;

import com.tataplay.scripts.branches.enums.Environment;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DependencyReport {

    public static void main(String... args) throws Exception {
        new DependencyReport().printDependencyReport();
    }

    public void printDependencyReport() throws Exception {
        Map<String, Map<String, List<Application>>> dependencyTree = TataPlayUtil.fetchLatestDependencyMap(Environment.UAT, null);
        printResults(dependencyTree);
    }

    private static void printResults(Map<String, Map<String, List<Application>>>dependencyTree) {
        for (String dependency : dependencyTree.keySet().stream().sorted().collect(Collectors.toList())) {
            System.out.println(dependency);
            Map<String, List<Application>> dependencyApplication = dependencyTree.get(dependency);
            List<String> versions = dependencyApplication.keySet().stream().sorted((o1, o2) -> {
                if (StringUtils.hasText(o1) && StringUtils.hasText(o2)) {
                    String[] parts1 = o1.split("\\.");
                    String[] parts2 = o2.split("\\.");
                    if (parts1.length == 3 && parts2.length == 3) {
                        try {
                            int major1 = Integer.parseInt(parts1[0]);
                            int minor1 = Integer.parseInt(parts1[1]);
                            int subVersion1 = Integer.parseInt(parts1[2]);

                            int major2 = Integer.parseInt(parts2[0]);
                            int minor2 = Integer.parseInt(parts2[1]);
                            int subVersion2 = Integer.parseInt(parts2[2]);

                            if ((major1 > major2) || (major1 == major2 && minor1 > minor2)
                                    || (major1 == major2 && minor1 == minor2 && subVersion1 > subVersion2)) {
                                return -1;
                            } else {
                                return 1;
                            }
                        } catch (Exception ignored) {
                            if (parts1[2].matches("\\d+")) {
                                return -1;
                            } else {
                                return 1;
                            }
                        }
                    }
                }
                return 0;
            }).collect(Collectors.toList());
            for (String version : versions) {
                System.out.println(String.join(";", version, dependencyApplication.get(version).toString()));
            }
            System.out.println();
        }
    }

}
