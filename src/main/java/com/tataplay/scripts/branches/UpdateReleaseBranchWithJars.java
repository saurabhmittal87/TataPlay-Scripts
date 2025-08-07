package com.tataplay.scripts.branches;

import com.tataplay.scripts.branches.enums.Environment;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class UpdateReleaseBranchWithJars {
    private static final Map<String, String> jars = new HashMap<>();
    private static final List<String> dependencies = new ArrayList<>();
    private static final Set<Application> impactedApplications = new HashSet<>();
    private static final Environment environment = Environment.UAT;
    private static final String RELEASE_BRANCH_NAME = "uat";
    private static final List<String> applicationsToConsider = null;
    private static final Map<String, List<String>> prohibitedApplicationsToUpdate = Map.of("androidStick-thirdParty",
            List.of("rest-api"), "common-event-domains", Arrays.asList("event-listener", "event-processor"),
            "tatasky-sms-connector", List.of("cms-ui"));

    static {
//        jars.put("homescreen-db-util", " 9.6.1-UAT-SNAPSHOT");
//        jars.put("subscriber-db-util", "8.2.7-UAT-SNAPSHOT");
        jars.put("common-constants", "5.24.9-UAT-SNAPSHOT");
//        jars.put("common-pojo", "7.7.9-UAT-SNAPSHOT");
//        jars.put("common-sql-domains", "6.1.3-UAT-SNAPSHOT");
//        jars.put("cache", "3.1.5-UAT-SNAPSHOT");
//        jars.put("common-db-tsf", "4.9.1-UAT-SNAPSHOT");
//        jars.put("content-db-util", "4.0.7-UAT-SNAPSHOT");
//        jars.put("tatasky-connector-comviva", "2.1.0");
//        jars.put("third-party-utils", "2.6.7");
//        jars.put("mm-domains", "8.2.4");
//        jars.put("tatasky-sms-connector", "3.5.4-UAT-SNAPSHOT");
//        jars.put("transaction-logger", "4.5.7");
//        jars.put("partner-db-entities", "0.2.0");
//        jars.put("pubnub-router-client", "1.3.3");
//        jars.put("cache-manager", "1.5.1-UAT-SNAPSHOT");
//        jars.put("common-event-domains", "0.3.5-UAT-SNAPSHOT");
//        jars.put("androidStick-thirdParty", "4.3.4-UAT-SNAPSHOT");
//        jars.put("search-client", "1.0.3-UAT-SNAPSHOT");
//        jars.put("content-cache-utils", "5.4.2");
//        jars.put("homescreen-cache-utils", "5.5.9");
//        jars.put("tatasky-connector-comviva", "2.0.3");
//        jars.put("search-utils", "3.2.1");
//        jars.put("swagger-java-client-customer", "2.0.1");
//        jars.put("common-event-domains", "1.7.7");
        dependencies.addAll(jars.keySet());
    }

    public static void main(String... args) throws Exception {
        Map<String, Map<String, List<Application>>> dependencyTree = com.tataplay.scripts.branches.TataPlayUtil.fetchLatestDependencyMap(environment, applicationsToConsider);

        List<Application> applicationList = getApplicationsList(dependencyTree);

        for (String uniqueService : getUniqueServices(applicationList)) {
            System.out.println("Fetching latest code of: " + uniqueService);
            createReleaseBranch(uniqueService);
        }
        for (Application application : applicationList.stream().sorted(Comparator.comparing(Application::getApplicationName)).collect(Collectors.toList())) {
            System.out.println("Updated latest jar in : " + application.getApplicationName());
            updateWithNewJarVersions(application);
        }
        printPushCodeCommands();
        printUpdateInformation();
    }

    private static void printPushCodeCommands() throws IOException {
        for (Application application : impactedApplications) {
            pushReleaseBranch(application);
        }
    }

    /**
     * Prints information about services updated
     */
    private static void printUpdateInformation() {
        String startText = jars.size() == 1 ? "Updated following jar " : "Updated following jars ";
        startText += "[" + getJarsName() + "]";
        System.out.println(startText + " in " + RELEASE_BRANCH_NAME + " branch for following:");
        List<String> jars = impactedApplications.stream().filter(applications -> applications.getType().equals("Jar")).map(Application::getParentService).collect(Collectors.toList());
        List<String> services = impactedApplications.stream().filter(applications -> applications.getType().equals("Service")).map(Application::getApplicationName).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(services)) {
            System.out.println("\nservices: " + services);
        }
        if (!CollectionUtils.isEmpty(jars)) {
            System.out.println("\njars: " + jars);
            System.out.println("\n* It's recommended to create a new version of these jars as well and get that updated in applications");
        }
    }

    private static String getJarsName() {
        String[] texts = new String[jars.size()];
        int counter = 0;
        for (String jar : jars.keySet()) {
            texts[counter++] = String.join(":", jar, jars.get(jar));
        }
        return String.join(", ", texts);
    }

    private static List<String> getUniqueServices(List<Application> applications) {
        if (!CollectionUtils.isEmpty(applications)) {
            Set<String> uniqueServices = new HashSet<>();
            applications.forEach(application -> uniqueServices.add(application.getParentService()));
            return new ArrayList<>(uniqueServices).stream().sorted().collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static List<Application> getApplicationsList(Map<String, Map<String, List<Application>>> dependencyTree) {
        Set<Application> applications = new HashSet<>();
        for (String dependencyJar : jars.keySet()) {
            Map<String, List<Application>> applicationVersionMap = dependencyTree.get(dependencyJar);
            if (applicationVersionMap != null) {
                for (String version : applicationVersionMap.keySet()) {
                    List<Application> applicationList = applicationVersionMap.get(version);
                    applications.addAll(applicationList);
                }
            }

        }
        return applications.stream().sorted(Comparator.comparing(Application::getParentService)).collect(Collectors.toList());
    }

    private static void createReleaseBranch(String uniqueApplication) throws IOException {
        File project = new File(TataPlayUtil.PROJECT_BASE_DIRECTORY + uniqueApplication);
        printResults(Runtime.getRuntime().exec("git fetch", null, project));
        printResults(Runtime.getRuntime().exec("git checkout " + RELEASE_BRANCH_NAME, null, project));
        printResults(Runtime.getRuntime().exec("git checkout -b " + RELEASE_BRANCH_NAME, null, project));
        printResults(Runtime.getRuntime().exec("git pull origin " + RELEASE_BRANCH_NAME, null, project));
    }


    private static void pushReleaseBranch(Application projectName) throws IOException {
        File project = new File(TataPlayUtil.PROJECT_BASE_DIRECTORY + projectName.getParentService());
        printResults(Runtime.getRuntime().exec("git add build.gradle", null, project));
        System.out.println("cd " + project.getAbsolutePath());
        System.out.println("git reset HEAD");
        System.out.println("git add build.gradle");
        System.out.println("git commit -m '" + RELEASE_BRANCH_NAME + ": Updated dependencies version'");
        System.out.println("git push origin " + RELEASE_BRANCH_NAME);
    }

    private static void pushReleaseBranch(Map<String, List<Application>> applicationMap) throws IOException {
        for (String parentApplication : applicationMap.keySet()) {
            List<Application> applications = applicationMap.get(parentApplication);
            File project = new File(TataPlayUtil.PROJECT_BASE_DIRECTORY + applications.get(0).getParentService());
            System.out.println("cd " + project.getAbsolutePath());
            System.out.println("git reset HEAD");
            for (Application application : applications) {
                if (applications.size() > 1) {
                    File subProject = new File(TataPlayUtil.PROJECT_BASE_DIRECTORY + application.getApplicationName());
                    System.out.println("cd " + subProject.getAbsolutePath());
                }
                System.out.println("git add build.gradle");
            }
            System.out.println("git commit -m '" + RELEASE_BRANCH_NAME + ": Updated dependencies version'");
            System.out.println("git push origin " + RELEASE_BRANCH_NAME);
        }
    }

    private static Map<String, List<Application>> getApplicationMap() {
        Map<String, List<Application>> applicationMap = new HashMap<>();
        for (Application application : impactedApplications) {
            String parentApplication = application.getParentService();
            List<Application> applications = applicationMap.get(parentApplication);
            if (Objects.isNull(applications)) {
                applications = new ArrayList<>();
            }
            applications.add(application);
            applicationMap.put(parentApplication, applications);
        }
        return applicationMap;
    }

    private String getReleaseBranchName(Application projectName, Environment environment) {
        if (StringUtils.equals("ta-worker", projectName.getApplicationName())) {
            return Util.getBranchName(projectName, environment);
        } else {
            return RELEASE_BRANCH_NAME;
        }
    }

    public static void printResults(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

    private static void updateWithNewJarVersions(Application application) throws IOException {
        File buildDotGradleFile = new File(TataPlayUtil.PROJECT_BASE_DIRECTORY + application.getApplicationName() + "/build.gradle");
        String applicationName = application.getApplicationName();
        Scanner buildScanner = new Scanner(buildDotGradleFile);
        StringBuilder buildDotGradleFileContent = new StringBuilder();
        while (buildScanner.hasNext()) {
            String text = buildScanner.nextLine();
            boolean contentUpdated = false;
            Matcher matcher = TataPlayUtil.pattern_most_used.matcher(text);
            if (matcher.find()) {
                String initialPart = matcher.group(1);
                String dependencyProvider = matcher.group(2);
                String dependencyName = matcher.group(3);
                String currentDependencyVersion = matcher.group(4);
                String remainingPart = matcher.group(5);
                String newDependencyVersion = jars.get(dependencyName);
                boolean skipUpdate = prohibitedApplicationsToUpdate.containsKey(dependencyName) && prohibitedApplicationsToUpdate.get(dependencyName).contains(applicationName);
                System.out.println(dependencyName + "-" + applicationName);
                if (!skipUpdate && dependencyProvider.equals(TataPlayUtil.videoReadyGroup) && dependencies.contains(dependencyName) && !StringUtils.equals(newDependencyVersion, currentDependencyVersion)) {
                    String newText = initialPart + "group: '" + TataPlayUtil.videoReadyGroup + "', name: '" + dependencyName + "', version: '" + newDependencyVersion + "'" + (StringUtils.isBlank(remainingPart) ? "" : remainingPart);
                    buildDotGradleFileContent.append(newText).append("\n");
                    contentUpdated = true;
                    impactedApplications.add(application);
                }
            }


//            matcher = TataPlayUtil.pattern_less_used.matcher(text);
//            if (matcher.find()) {
//                String start = matcher.group(1);
//                String dependencyProvider = matcher.group(2);
//                String dependencyName = matcher.group(3);
//                String currentDependencyVersion = matcher.group(4);
//                String newDependencyVersion = jars.get(dependencyName);
//                boolean skipUpdate = prohibitedApplicationsToUpdate.containsKey(dependencyName) && prohibitedApplicationsToUpdate.get(dependencyName).contains(applicationName);
//                if (!skipUpdate && dependencyProvider.equals("tv.videoready") && dependencies.contains(dependencyName) && !StringUtils.equals(newDependencyVersion, currentDependencyVersion)) {
//                    String newText = start + "'" + dependencyProvider + ":" + dependencyName + ":" + newDependencyVersion + "'";
//                    buildDotGradleFileContent.append(newText).append("\n");
//                    contentUpdated = true;
//                    impactedApplications.add(application);
//                }
//            }
            if (!contentUpdated) {
                buildDotGradleFileContent.append(text).append("\n");
            }
        }
        buildScanner.close();

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(buildDotGradleFile))) {
            bufferedWriter.write(buildDotGradleFileContent.toString());
        }
    }
}
