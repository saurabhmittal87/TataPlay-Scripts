package com.tataplay.scripts.release;

public class Data {
    private String MRState;
    private String MRLink;

    public Data(String MRState, String MRLink) {
        this.MRState = MRState;
        this.MRLink = MRLink;
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
}
