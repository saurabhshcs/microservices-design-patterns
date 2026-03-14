package com.saurabhshcs.adtech.microservices.designpattern.strangler.banking.config;

/**
 * Controls which features have been migrated to the modern service.
 * In production, back this with a feature-flag system (LaunchDarkly, Unleash).
 */
public class FeatureToggle {

    private boolean useModernForCreate;
    private boolean useModernForRead;
    private boolean useModernForUpdate;

    public FeatureToggle(boolean useModernForCreate, boolean useModernForRead,
                         boolean useModernForUpdate) {
        this.useModernForCreate = useModernForCreate;
        this.useModernForRead = useModernForRead;
        this.useModernForUpdate = useModernForUpdate;
    }

    /** All traffic routed to legacy (migration not started). */
    public static FeatureToggle allLegacy() {
        return new FeatureToggle(false, false, false);
    }

    /** All traffic routed to modern (migration complete). */
    public static FeatureToggle allModern() {
        return new FeatureToggle(true, true, true);
    }

    public boolean isUseModernForCreate() { return useModernForCreate; }
    public boolean isUseModernForRead()   { return useModernForRead; }
    public boolean isUseModernForUpdate() { return useModernForUpdate; }

    public void setUseModernForCreate(boolean v) { this.useModernForCreate = v; }
    public void setUseModernForRead(boolean v)   { this.useModernForRead = v; }
    public void setUseModernForUpdate(boolean v) { this.useModernForUpdate = v; }
}
