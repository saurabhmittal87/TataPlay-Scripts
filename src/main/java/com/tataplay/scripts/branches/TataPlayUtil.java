package com.tataplay.scripts.branches;

import com.tataplay.scripts.branches.enums.Environment;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TataPlayUtil {

    public static final String regex_most_used = "(.*)group.*'(.*)'.*name.*'(.*)'.*version.*'(.*)'(.*)";
    public static final String regex_less_used = "(.*)'(.*):(.*):(.*)'(.*)";
    public static final Pattern pattern_most_used = Pattern.compile(regex_most_used);
    public static final Pattern pattern_less_used = Pattern.compile(regex_less_used);
    public static String PROJECT_BASE_DIRECTORY = "/home/saurabh/projects/tataplay/";
    public static String videoReadyGroup = "tv.videoready";
    public static Map<String, Application> uniqueServices = new HashMap<>();

    public static Map<String, Map<String, List<Application>>> fetchLatestDependencyMap(Environment environment, List<String> applicationsToConsider) throws Exception {
        Scanner projectScanner = new Scanner(new File("/home/saurabh/Documents/projects.csv"));
        Map<String, Map<String, List<Application>>> dependencyTree = new HashMap<>();
        if (projectScanner.hasNext()) {
            projectScanner.nextLine();
        }
        List<Application> applications = new ArrayList<>();
        while (projectScanner.hasNext()) {
            String[] text = projectScanner.nextLine().split(",");
            if (CollectionUtils.isEmpty(applicationsToConsider) || (!CollectionUtils.isEmpty(applicationsToConsider) && applicationsToConsider.contains(text[0]))) {
                String branchName = getBranchName(environment, text[2], text[3], text[4]);
                if (StringUtils.isNotBlank(branchName)) {
                    Application application = new Application(text[0], text[1], text[2], text[3], text[4], text[5]);
                    applications.add(application);
                    uniqueServices.put(application.getParentService(), application);
                }
            }
        }
        for (String uniqueService : uniqueServices.keySet().stream().sorted().collect(Collectors.toList())) {
            System.out.println("Fetching latest branch (" + Util.getBranchName(uniqueServices.get(uniqueService), environment) + ") of: " + uniqueService);
            checkoutLatestBranch(uniqueService, Util.getBranchName(uniqueServices.get(uniqueService), environment));
        }
        for (Application application : applications) {
            updateData(application, dependencyTree);
        }
        return dependencyTree;
    }

    private static void checkoutLatestBranch(String uniqueService, String branchName) throws IOException {
        File project = new File(PROJECT_BASE_DIRECTORY + uniqueService);
        stashChanges(project);
        checkoutBranch(project, branchName);
        pullBranch(project, branchName);
    }

    private static void stashChanges(File project) throws IOException {
        printResults(Runtime.getRuntime().exec("git reset HEAD", null, project));
        printResults(Runtime.getRuntime().exec("git stash", null, project));
    }

    private static void checkoutBranch(File project, String branchName) throws IOException {
        printResults(Runtime.getRuntime().exec("git checkout " + branchName, null, project));
    }

    private static void pullBranch(File project, String branchName) throws IOException {
        printResults(Runtime.getRuntime().exec("git pull origin " + branchName, null, project));
    }

    public static void printResults(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

    private static void updateData(Application application, Map<String, Map<String, List<Application>>> dependencyTree) throws FileNotFoundException {
        Scanner buildScanner = new Scanner(new File(PROJECT_BASE_DIRECTORY + application.getApplicationName() + "/build.gradle"));
        while (buildScanner.hasNext()) {
            String text = buildScanner.nextLine();
            Matcher matcher = pattern_most_used.matcher(text);
            if (matcher.find()) {
                String dependencyProvider = matcher.group(2);
                String dependencyName = matcher.group(3);
                String dependencyVersion = matcher.group(4);
                updateDependencyTree(dependencyProvider, dependencyName, dependencyVersion, dependencyTree, application);
            }

            matcher = pattern_less_used.matcher(text);
            if (matcher.find()) {
                String dependencyProvider = matcher.group(2);
                String dependencyName = matcher.group(3);
                String dependencyVersion = matcher.group(4);
                updateDependencyTree(dependencyProvider, dependencyName, dependencyVersion, dependencyTree, application);
            }
        }
        buildScanner.close();
    }

    private static void updateDependencyTree(String dependencyProvider, String dependencyName, String dependencyVersion, Map<String, Map<String, List<Application>>> dependencyTree, Application application) {
        if (dependencyProvider.equals(videoReadyGroup)) {
            Map<String, List<Application>> dependencyApplications = getDependencyApplicationsMap(dependencyName, dependencyTree);
            List<Application> applications = getApplicationList(dependencyVersion, dependencyApplications);
            applications.add(application);
            dependencyApplications.put(dependencyVersion, applications);
            dependencyTree.put(dependencyName, dependencyApplications);
        }
    }

    private static Map<String, List<Application>> getDependencyApplicationsMap(String dependencyName, Map<String, Map<String, List<Application>>> dependencyTree) {
        Map<String, List<Application>> dependencyApplications = dependencyTree.get(dependencyName);
        return Objects.nonNull(dependencyApplications) ? dependencyApplications : new HashMap<>();
    }

    private static List<Application> getApplicationList(String dependencyVersion, Map<String, List<Application>> dependencyApplications) {
        List<Application> applications = dependencyApplications.get(dependencyVersion);
        return Objects.nonNull(applications) ? applications : new ArrayList<>();
    }

    private static String getBranchName(Environment environment, String masterBranch, String uatBranch, String litUatBranchName) {
        return switch (environment) {
            case PRODUCTION -> masterBranch;
            case UAT -> uatBranch;
            case UAT_LIT -> litUatBranchName;
        };
    }
}


