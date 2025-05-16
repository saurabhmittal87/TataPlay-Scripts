package com.tataplay.scripts.branches;

import com.tataplay.scripts.branches.enums.Environment;

public class Util {
    public static String getBranchName(Application application, Environment environment) {
        switch (environment) {
            case PRODUCTION:
                return application.getProductionBranchName();
            case UAT:
                return application.getUatBranchName();
            default:
                return "release-21-11-2022-E";
        }
    }
}
