package core.vehicle;

import sound.SoundType;
import java.awt.Color;

/**
 * <b>Cấu hình bất biến (Immutable) của một loại phương tiện.</b>
 *
 * <p>Tách biệt dữ liệu "type-level" (áp dụng cho mọi xe cùng loại)
 * khỏi dữ liệu "instance-level" (vị trí, vận tốc… của từng xe riêng lẻ).
 *
 * <p>Để thêm loại xe mới: tạo một {@code VehicleProfile} với
 * {@link Builder} rồi đặt vào constructor lớp con mới — không cần
 * chỉnh sửa bất kỳ class nào khác trong hệ thống.</p>
 */
public final class VehicleProfile {

    // ── Nhận dạng ────────────────────────────────────────────────────
    /** Khóa duy nhất cho loại xe (dùng để tra sprite, âm thanh, thống kê). */
    private final String typeKey;
    /** Tên hiển thị đầy đủ, ví dụ "Ô tô cá nhân". */
    private final String displayName;
    /** Nhãn ngắn cho chế độ Basic, ví dụ "Car". */
    private final String basicLabel;

    // ── Đồ họa ───────────────────────────────────────────────────────
    /** Màu thân xe (Basic mode và Graphics mode). */
    private final Color  bodyColor;
    /** Màu mái / cabin xe. */
    private final Color  roofColor;
    /** Đường dẫn tương đối tới file sprite (Graphics mode). */
    private final String spritePath;

    // ── Vật lý ───────────────────────────────────────────────────────
    /** Tốc độ tối đa mặc định (px/s ở zoom 1×). */
    private final double defaultMaxSpeed;
    /** Gia tốc (px/s²). */
    private final double defaultAcceleration;
    /** Chiều dài xe (px). */
    private final double defaultLength;
    /** Chiều rộng xe (px). */
    private final double defaultWidth;

    // ── Âm thanh ─────────────────────────────────────────────────────
    /** Âm thanh động cơ khi xe chạy. */
    private final SoundType engineSound;
    /** Âm thanh còi xe. */
    private final SoundType hornSound;
    /** Âm thanh còi ưu tiên (null nếu không có). */
    private final SoundType sirenSound;

    private VehicleProfile(Builder b) {
        this.typeKey             = b.typeKey;
        this.displayName         = b.displayName;
        this.basicLabel          = b.basicLabel;
        this.bodyColor           = b.bodyColor;
        this.roofColor           = b.roofColor;
        this.spritePath          = b.spritePath;
        this.defaultMaxSpeed     = b.defaultMaxSpeed;
        this.defaultAcceleration = b.defaultAcceleration;
        this.defaultLength       = b.defaultLength;
        this.defaultWidth        = b.defaultWidth;
        this.engineSound         = b.engineSound;
        this.hornSound           = b.hornSound;
        this.sirenSound          = b.sirenSound;
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String  getTypeKey()             { return typeKey;             }
    public String  getDisplayName()         { return displayName;         }
    public String  getBasicLabel()          { return basicLabel;          }
    public Color   getBodyColor()           { return bodyColor;           }
    public Color   getRoofColor()           { return roofColor;           }
    public String  getSpritePath()          { return spritePath;          }
    public double  getDefaultMaxSpeed()     { return defaultMaxSpeed;     }
    public double  getDefaultAcceleration() { return defaultAcceleration; }
    public double  getDefaultLength()       { return defaultLength;       }
    public double  getDefaultWidth()        { return defaultWidth;        }
    public SoundType getEngineSound()       { return engineSound;         }
    public SoundType getHornSound()         { return hornSound;           }
    public SoundType getSirenSound()        { return sirenSound;          }
    public boolean hasSiren()               { return sirenSound != null;  }

    @Override
    public String toString() {
        return "VehicleProfile[" + displayName + "]";
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static Builder builder(String typeKey) {
        return new Builder(typeKey);
    }

    public static final class Builder {
        private final String typeKey;
        private String   displayName         = "Unknown";
        private String   basicLabel          = "?";
        private Color    bodyColor           = Color.GRAY;
        private Color    roofColor           = Color.DARK_GRAY;
        private String   spritePath          = "";
        private double   defaultMaxSpeed     = 80;
        private double   defaultAcceleration = 40;
        private double   defaultLength       = 40;
        private double   defaultWidth        = 20;
        private SoundType engineSound        = null;
        private SoundType hornSound          = null;
        private SoundType sirenSound         = null;

        private Builder(String typeKey) { this.typeKey = typeKey; }

        public Builder displayName(String v)         { displayName         = v; return this; }
        public Builder basicLabel(String v)          { basicLabel          = v; return this; }
        public Builder bodyColor(Color v)            { bodyColor           = v; return this; }
        public Builder roofColor(Color v)            { roofColor           = v; return this; }
        public Builder spritePath(String v)          { spritePath          = v; return this; }
        public Builder defaultMaxSpeed(double v)     { defaultMaxSpeed     = v; return this; }
        public Builder defaultAcceleration(double v) { defaultAcceleration = v; return this; }
        public Builder defaultLength(double v)       { defaultLength       = v; return this; }
        public Builder defaultWidth(double v)        { defaultWidth        = v; return this; }
        public Builder engineSound(SoundType v)      { engineSound         = v; return this; }
        public Builder hornSound(SoundType v)        { hornSound           = v; return this; }
        public Builder sirenSound(SoundType v)       { sirenSound          = v; return this; }

        public VehicleProfile build() { return new VehicleProfile(this); }
    }
}
