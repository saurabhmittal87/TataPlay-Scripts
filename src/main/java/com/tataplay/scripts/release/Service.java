package com.tataplay.scripts.release;

import com.tataplay.scripts.release.v2.ServiceType;

public class Service {
    private String applicationName;
    private String serviceName;
    private String tag;
    private String MR;
    private boolean skip;
    private ServiceType serviceType;
    private String MRState;
    private String MRLink;
    public Service() {
    }

    public Service(String applicationName, String serviceName, String tag, String MR, boolean skip, ServiceType serviceType) {
        this.applicationName = applicationName;
        this.serviceName = serviceName;
        this.tag = tag;
        this.MR = MR;
        this.skip = skip;
        this.serviceType = serviceType;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getMR() {
        return MR;
    }

    public void setMR(String MR) {
        this.MR = MR;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getMRState() {
        return MRState;
    }

    public void setMRState(String MRState) {
        this.MRState = MRState;
    }

    public String getMRLink() {
        return MRLink;
    }

    public void setMRLink(String MRLink) {
        this.MRLink = MRLink;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Service)) return false;
        Service other = (Service) o;
        return this.serviceName != null && this.serviceName.equals(other.serviceName);
    }

    @Override
    public int hashCode() {
        return this.serviceName != null ? this.serviceName.hashCode() : 0;
    }
}
