package core.vehicle;

public class VehicleProfile {
    private final String typeKey;
    private final String displayName;
    private final String basicLabel;
    private final String spritePath;
    private final VehicleSoundProfile soundProfile;
    private final String statisticCategory;

    public VehicleProfile(
            String typeKey,
            String displayName,
            String basicLabel,
            String spritePath,
            VehicleSoundProfile soundProfile,
            String statisticCategory
    ) {
        this.typeKey = typeKey;
        this.displayName = displayName;
        this.basicLabel = basicLabel;
        this.spritePath = spritePath;
        this.soundProfile = soundProfile;
        this.statisticCategory = statisticCategory;
    }

    public String getTypeKey() {
        return typeKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBasicLabel() {
        return basicLabel;
    }

    public String getSpritePath() {
        return spritePath;
    }

    public VehicleSoundProfile getSoundProfile() {
        return soundProfile;
    }

    public String getStatisticCategory() {
        return statisticCategory;
    }
}
