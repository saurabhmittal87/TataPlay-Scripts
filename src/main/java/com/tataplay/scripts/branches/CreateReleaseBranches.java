package com.tataplay.scripts.branches;

import java.io.File;
import java.io.IOException;

public class CreateReleaseBranches {

    private static String applicationList = "lit-mobile-common-bff,pubnub-router,sms-api,ta-client,temp-login-service,lit-subscription-service,savvy-probe,binge-ensemble,binge-kafka-event-listener,binge-ta-worker,cache-manager,partner-registration-engine";
    private static String releaseBranchName = "release-20-01-2026-E";

    public static void main(String... args) throws IOException {
        String[] applications = applicationList.split(",");
        for (String application : applications) {
            checkoutLatestMaster(application.trim());
        }
        printMessage(applications);
    }

    private static void printMessage(String[] applications) {
        String applicationText = applicationList.contains(",") ? "applications" : "application";
        boolean moreThanOneService = applications.length > 1;
        System.out.print("Release branch (" + releaseBranchName + ") created for following " + applicationText + ": ");
        for (int counter = 0; counter < applications.length; counter++) {
            if (moreThanOneService && counter == applications.length - 1) {
                System.out.print(" & ");
            }
            System.out.print(applications[counter]);
            if (moreThanOneService && counter < applications.length - 2) {
                System.out.print(", ");
            }
        }
    }

    private static void checkoutLatestMaster(String projectName) throws IOException {
        File project = new File(TataPlayUtil.PROJECT_BASE_DIRECTORY + projectName);
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git fetch", null, project));
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git stash", null, project));
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git checkout master", null, project));
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git pull origin master", null, project));
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git checkout " + releaseBranchName, null, project));
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git checkout -b " + releaseBranchName, null, project));
        TataPlayUtil.printResults(Runtime.getRuntime().exec("git push origin " + releaseBranchName, null, project));
    }


}
