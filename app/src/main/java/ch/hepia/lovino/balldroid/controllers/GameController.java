package ch.hepia.lovino.balldroid.controllers;


import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import java.util.ArrayList;
import java.util.Timer;

import ch.hepia.lovino.balldroid.GameActivity;
import ch.hepia.lovino.balldroid.models.Ball;
import ch.hepia.lovino.balldroid.models.BallDirection;
import ch.hepia.lovino.balldroid.models.Bonus;
import ch.hepia.lovino.balldroid.models.Car;
import ch.hepia.lovino.balldroid.models.DifficultyLevel;
import ch.hepia.lovino.balldroid.models.Game;
import ch.hepia.lovino.balldroid.models.Platform;
import ch.hepia.lovino.balldroid.models.PointArea;
import ch.hepia.lovino.balldroid.models.Score;
import ch.hepia.lovino.balldroid.models.Time;
import ch.hepia.lovino.balldroid.models.db.DBHelper;
import ch.hepia.lovino.balldroid.views.GameSurfaceView;

import static ch.hepia.lovino.balldroid.models.db.DBContract.ScoreEntry;

public class GameController {
    private static final int TIMER_SECONDS = 60;
    private GameActivity context;
    private DifficultyLevel difficulty;
    private GameSurfaceView view;
    private float xAccel = 0;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Game game;
    private ch.hepia.lovino.balldroid.models.Car Car;
    private Score score;
    private Time time;
    private boolean paused = true;
    private TimerThread timer;
    private ArrayList<Bonus> bonusesToRemove;
    int audioSource = MediaRecorder.AudioSource.MIC;
    int sampleRateInHz = 44100;
    int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;

    int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,channelConfig,audioFormat);

    AudioRecord recorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig,
            audioFormat, minBufferSize);
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            xAccel = -event.values[0];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Do nothing for now
        }
    };

    public GameController(GameActivity context, DifficultyLevel difficulty) {
        this.context = context;
        this.difficulty = difficulty;
        this.view = new GameSurfaceView(context, this);
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        }
        this.bonusesToRemove = new ArrayList<>();
        this.timer = new TimerThread(10 * 1000, this);
        Log.i("message","ooooooohhhuuuu");
    }

    public void updateOnVoice(){
        float[] audioData = new float[512];

        recorder.read(audioData,0,512,AudioRecord.READ_BLOCKING);
        double sum = 0;
        for(int i = 0; i < audioData.length; i++){
            sum += Math.abs(audioData[i]);
            //Log.i("message",""+audioData[i]);
        }
        float mean = (float) (sum/audioData.length);
        System.out.println(mean);
        if(mean < 0.03) {
            xAccel = -15;
        }
        if(mean > 0.03) {
            xAccel = 10;
        }
        if(mean > 0.06){
            xAccel = 20;
        }
        if(mean > 0.15) {
            xAccel = 30;
            //System.out.println("Das ist zu laut");

            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog alert;
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Zu laut");
                    builder.setMessage("Bitte nicht Stressen");
                    builder.setCancelable(false);
                    alert = builder.create();
                    alert.show();
                    CountDownTimer countDownTimer = new CountDownTimer(3 * 1000, 1000) {
                        public void onTick(long millisUntilFinished) {
                        }

                        public void onFinish() {
                            alert.dismiss();
                        }
                    };
                    countDownTimer.start();
                }
            });
        }
    }

    public void update() {
        if (paused) return;
        //this.time.setTimeRemaining((int) timer.getRemainingTime() / 1000);
        updateOnVoice();
        this.Car.incrementSpeedX(xAccel);
        this.Car.incrementSpeedY();
        this.Car.updatePosition();
        if (this.Car.getX() > (this.view.getSurfaceWidth() - this.Car.getRadius())) {
            this.Car.setX(this.view.getSurfaceWidth() - this.Car.getRadius());
            this.Car.reboundX();
        }
        if (this.Car.getX() < this.Car.getRadius()) {
            this.Car.setX(this.Car.getRadius());
            this.Car.reboundX();
        }
        BallDirection direction = Car.getDirection();
        for (Platform p : this.game.getPlatforms()) {
            if (Car.getBoundingRect().intersect(p.getBoundingRect())) {
                switch (direction) {
                    case N:
                        reboundBottom(Car, p);
                        break;
                    case NE:
                        reboundBottom(Car, p);
                        reboundLeft(Car, p);
                        break;
                    case E:
                        reboundLeft(Car, p);
                        break;
                    case SE:
                        reboundTop(Car, p);
                        reboundLeft(Car, p);
                        break;
                    case S:
                        reboundTop(Car, p);
                        break;
                    case SW:
                        reboundTop(Car, p);
                        reboundRight(Car, p);
                        break;
                    case W:
                        reboundRight(Car, p);
                        break;
                    case NW:
                        reboundBottom(Car, p);
                        reboundRight(Car, p);
                        break;
                    case STILL:
                        break;
                }
            }
        }

        for (PointArea pointArea : this.game.getPointsAreas()) {
            if (Car.getBoundingRect().intersect(pointArea.getBoundingRect())) {
                this.Car.putToStart();
            }
        }

        this.bonusesToRemove.forEach(game::removeBonus);
        this.bonusesToRemove.clear();

        for (Bonus bonus : this.game.getBonuses()) {
            if (Car.getBoundingRect().intersect(bonus.getBoundingRect())) {
                Log.v("BONUS", "Hit a bonus of " + bonus.getSeconds());
                bonusesToRemove.add(bonus);
                timer.addToTime(bonus.getSeconds());
            }
        }
    }

    private void reboundTop(Car ball, Platform p) {
        if (Math.abs(this.Car.getY() - p.getBoundingRect().top) < ball.getRadius()) {
            this.Car.reboundY();
            this.Car.setY(p.getBoundingRect().top - ball.getRadius());
        }
    }

    private void reboundBottom(Car ball, Platform p) {
        if (Math.abs(this.Car.getY() - p.getBoundingRect().bottom) < ball.getRadius()) {
            this.Car.reboundY();
            this.Car.setY(p.getBoundingRect().bottom + ball.getRadius());
        }
    }

    private void reboundLeft(Car ball, Platform p) {
        if (Math.abs(this.Car.getX() - p.getBoundingRect().left) < ball.getRadius()) {
            this.Car.reboundX();
            this.Car.setX(p.getBoundingRect().left - ball.getRadius());
        }
    }

    private void reboundRight(Car ball, Platform p) {
        if (Math.abs(this.Car.getX() - p.getBoundingRect().right) < ball.getRadius()) {
            this.Car.reboundX();
            this.Car.setX(p.getBoundingRect().right + ball.getRadius());
        }
    }

    public void start() {
        this.game = new Game(this.difficulty, 0, TIMER_SECONDS, this.view.getSurfaceWidth(), this.view.getSurfaceHeight());
        this.Car = game.getCar();
        //this.score = game.getScore();
        //this.time = game.getTime();
        //this.timer = new TimerThread(this.time.getTimeRemaining() * 1000, this);
        //this.timer.start();
        recorder.startRecording();
    }

    public Game getGame() {
        return game;
    }

    public void resumeGame() {
        //this.sensorManager.registerListener(this.sensorListener, this.accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        paused = false;
        if (this.time != null) {
            this.timer = new TimerThread(this.time.getTimeRemaining() * 1000, this);
            this.timer.start();
        }
    }

    public void pauseGame() {
        this.sensorManager.unregisterListener(this.sensorListener, this.accelerometer);
        paused = true;
        this.timer.stopTimer();
        try {
            this.timer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public GameSurfaceView getView() {
        return view;
    }

    public boolean isPaused() {
        return paused;
    }

    public void endGame() {
        Log.w("GAME", "Game is over");
        pauseGame();
        try {
            this.timer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (score.getScore() > 0)
            saveScore();
        context.showEndOfGame(score.getScore());
        recorder.stop();
    }

    private void saveScore() {
        DBHelper dbHelper = new DBHelper(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(ScoreEntry.COLUMN_DIFFICULTY, difficulty.ordinal());
        values.put(ScoreEntry.COLUMN_SCORE, score.getScore());
        db.insert(ScoreEntry.TABLE_NAME, null, values);
    }
}
