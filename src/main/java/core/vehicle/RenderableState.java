package core.vehicle;

import java.awt.Color;

import util.Vector2D;

/**
 * <b>Snapshot bất biến</b> của trạng thái hiển thị một phương tiện.
 *
 * <p>Renderer nhận {@code RenderableState} thay vì trực tiếp truy cập
 * {@link Vehicle} — đảm bảo <em>tách hoàn toàn logic tọa độ ra khỏi logic vẽ</em>.
 * Renderer chỉ đọc dữ liệu, không bao giờ thay đổi trạng thái xe.</p>
 */
public final class RenderableState {

    private final String    id;
    private final Vector2D  position;      // tọa độ tâm xe (world space)
    private final double    rotation;      // góc xoay (radian)
    private final double    length;        // chiều dài (px)
    private final double    width;         // chiều rộng (px)
    private final String    basicLabel;    // nhãn chế độ Basic ("Car", "Ambu"…)
    private final Color     bodyColor;
    private final Color     roofColor;
    private final String    spritePath;    // đường dẫn sprite (chế độ Graphics)
    private final boolean   isPriority;    // cần vẽ đèn nháy?
    private final boolean   sirenFlash;    // trạng thái nháy đèn hiện tại
    private final boolean   isStopped;
    private final boolean   isYielding;
    private final String    driverStyle;   // "Normal" / "Aggressive" / "Emergency"

    public RenderableState(Builder b) {
        this.id          = b.id;
        this.position    = b.position;
        this.rotation    = b.rotation;
        this.length      = b.length;
        this.width       = b.width;
        this.basicLabel  = b.basicLabel;
        this.bodyColor   = b.bodyColor;
        this.roofColor   = b.roofColor;
        this.spritePath  = b.spritePath;
        this.isPriority  = b.isPriority;
        this.sirenFlash  = b.sirenFlash;
        this.isStopped   = b.isStopped;
        this.isYielding  = b.isYielding;
        this.driverStyle = b.driverStyle;
    }

    public String   getId()          { return id;          }
    public Vector2D getPosition()    { return position;    }
    public double   getRotation()    { return rotation;    }
    public double   getLength()      { return length;      }
    public double   getWidth()       { return width;       }
    public String   getBasicLabel()  { return basicLabel;  }
    public Color    getBodyColor()   { return bodyColor;   }
    public Color    getRoofColor()   { return roofColor;   }
    public String   getSpritePath()  { return spritePath;  }
    public boolean  isPriority()     { return isPriority;  }
    public boolean  isSirenFlash()   { return sirenFlash;  }
    public boolean  isStopped()      { return isStopped;   }
    public boolean  isYielding()     { return isYielding;  }
    public String   getDriverStyle() { return driverStyle; }

    // ── Builder ──────────────────────────────────────────────────────

    public static Builder builder(String id) { return new Builder(id); }

    public static final class Builder {
        private final String id;
        private Vector2D position   = Vector2D.ZERO;
        private double   rotation   = 0;
        private double   length     = 40;
        private double   width      = 20;
        private String   basicLabel = "?";
        private Color    bodyColor  = java.awt.Color.GRAY;
        private Color    roofColor  = java.awt.Color.DARK_GRAY;
        private String   spritePath = "";
        private boolean  isPriority = false;
        private boolean  sirenFlash = false;
        private boolean  isStopped  = false;
        private boolean  isYielding = false;
        private String   driverStyle = "";

        private Builder(String id) { this.id = id; }

        public Builder position(Vector2D v)    { position    = v; return this; }
        public Builder rotation(double v)      { rotation    = v; return this; }
        public Builder length(double v)        { length      = v; return this; }
        public Builder width(double v)         { width       = v; return this; }
        public Builder basicLabel(String v)    { basicLabel  = v; return this; }
        public Builder bodyColor(Color v)      { bodyColor   = v; return this; }
        public Builder roofColor(Color v)      { roofColor   = v; return this; }
        public Builder spritePath(String v)    { spritePath  = v; return this; }
        public Builder isPriority(boolean v)   { isPriority  = v; return this; }
        public Builder sirenFlash(boolean v)   { sirenFlash  = v; return this; }
        public Builder isStopped(boolean v)    { isStopped   = v; return this; }
        public Builder isYielding(boolean v)   { isYielding  = v; return this; }
        public Builder driverStyle(String v)   { driverStyle = v; return this; }

        public RenderableState build() { return new RenderableState(this); }
    }
}
