package ch.hepia.lovino.balldroid.models;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Platform extends StaticGameObject {
    private final static int PLATFORM_COLOR = Color.GREEN;

    public Platform(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        paint.setColor(PLATFORM_COLOR);
        canvas.drawRoundRect(this.boundingRect, 50, 50, paint);
    }
}
