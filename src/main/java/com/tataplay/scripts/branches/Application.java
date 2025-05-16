package com.tataplay.scripts.branches;

public class Application {
    private String parentService;
    private String applicationName;
    private String productionBranchName;
    private String uatBranchName;
    private String type;

    public Application() {
    }

    public Application(String parentService, String applicationName, String productionBranchName, String uatBranchName, String type) {
        this.parentService = parentService;
        this.applicationName = applicationName;
        this.productionBranchName = productionBranchName;
        this.uatBranchName = uatBranchName;
        this.type = type;
    }

    public String getParentService() {
        return parentService;
    }

    public void setParentService(String parentService) {
        this.parentService = parentService;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getProductionBranchName() {
        return productionBranchName;
    }

    public void setProductionBranchName(String productionBranchName) {
        this.productionBranchName = productionBranchName;
    }

    public String getUatBranchName() {
        return uatBranchName;
    }

    public void setUatBranchName(String uatBranchName) {
        this.uatBranchName = uatBranchName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return applicationName;
    }
}
