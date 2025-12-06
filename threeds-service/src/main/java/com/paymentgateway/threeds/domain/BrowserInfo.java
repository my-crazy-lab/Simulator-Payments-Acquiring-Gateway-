package com.paymentgateway.threeds.domain;

import java.io.Serializable;

public class BrowserInfo implements Serializable {
    private String userAgent;
    private String acceptHeader;
    private String language;
    private int screenWidth;
    private int screenHeight;
    private int colorDepth;
    private String timezoneOffset;
    private boolean javaEnabled;
    private boolean javascriptEnabled;
    private String ipAddress;

    public BrowserInfo() {
    }

    public BrowserInfo(String userAgent, String acceptHeader, String language,
                      int screenWidth, int screenHeight, int colorDepth,
                      String timezoneOffset, boolean javaEnabled, 
                      boolean javascriptEnabled, String ipAddress) {
        this.userAgent = userAgent;
        this.acceptHeader = acceptHeader;
        this.language = language;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.colorDepth = colorDepth;
        this.timezoneOffset = timezoneOffset;
        this.javaEnabled = javaEnabled;
        this.javascriptEnabled = javascriptEnabled;
        this.ipAddress = ipAddress;
    }

    // Getters and setters
    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public void setAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(int screenWidth) {
        this.screenWidth = screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public int getColorDepth() {
        return colorDepth;
    }

    public void setColorDepth(int colorDepth) {
        this.colorDepth = colorDepth;
    }

    public String getTimezoneOffset() {
        return timezoneOffset;
    }

    public void setTimezoneOffset(String timezoneOffset) {
        this.timezoneOffset = timezoneOffset;
    }

    public boolean isJavaEnabled() {
        return javaEnabled;
    }

    public void setJavaEnabled(boolean javaEnabled) {
        this.javaEnabled = javaEnabled;
    }

    public boolean isJavascriptEnabled() {
        return javascriptEnabled;
    }

    public void setJavascriptEnabled(boolean javascriptEnabled) {
        this.javascriptEnabled = javascriptEnabled;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
