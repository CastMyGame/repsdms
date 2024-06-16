package com.reps.demogcloud.models.punishment;


public class ResourceUpdateRequest {
    private ThreadEvent event;
    private String[] urls;

    // Getters and Setters
    public ThreadEvent getEvent() {
        return event;
    }

    public void setEvent(ThreadEvent event) {
        this.event = event;
    }

    public String[] getUrls() {
        return urls;
    }

    public void setUrls(String[] urls) {
        this.urls = urls;
    }
}
