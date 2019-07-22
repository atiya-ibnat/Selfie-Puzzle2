package com.bubt.selfipuzzle;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RelativeLayout;

import com.bubt.selfipuzzle.camera.MarshMallowPermission;
import com.bubt.selfipuzzle.ui.GameBoardView;
import com.bubt.selfipuzzle.uitil.ImageUtils;
import com.bubt.selfipuzzle.uitil.Logger;
import com.glidebitmappool.GlideBitmapPool;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private static final String PHOTO_FILE_NAME_1 = "SELFIE_PUZZLE.jpg";
    private Logger log = Logger.getLogger(MainActivity.class);

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_IMAGE_CAPTURE = 117;
    public static final String IMAGE_PATH = "ImagePath";
    private Bitmap bitmap;
    private MarshMallowPermission marshMallowPermission = new MarshMallowPermission(this);

    private String currentPhotoPath;
    private GameBoardView gameBoard;
    private RelativeLayout contentMain;
    private static final int SOLVED_STATUS = 199;
    private static final int REQ_CAPTURE_PHOTO = 0;

    private static final int IMAGE_WIDTH_ACTUAL = 768;
    private static final int IMAGE_HEIGHT_ACTUAL = 1024;

    private File photoFile;
    private FloatingActionButton floatingActionButton;

    private Handler winSolver = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == SOLVED_STATUS) {
                startSolvedActivity();
            }
        }
    };

    private boolean floatButtonHide = false;

    private void startSolvedActivity() {
        Intent intent = new Intent(MainActivity.this, SolvedActivity.class);
        intent.putExtra(IMAGE_PATH, currentPhotoPath);
        MainActivity.this.startActivity(intent);

        contentMain.removeView(gameBoard);
        gameBoard = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        GlideBitmapPool.initialize(6 * 1024 * 1024); // 10mb max memory size

        contentMain = (RelativeLayout) findViewById(R.id.content_main);

        askForCameraPermission();

        floatingActionButton = (FloatingActionButton) findViewById(R.id.fab);
        floatingActionButton.setOnClickListener(view -> {
            dispatchTakePictureIntent();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (floatButtonHide) {
            showFloatingButton();
        }
    }

    private void hideFloatingButton() {
        if (floatingActionButton != null) {
            floatingActionButton.animate().cancel();
            floatingActionButton.animate().translationYBy(-350);
            floatButtonHide = true;
        }
    }

    private void showFloatingButton() {
        if (floatingActionButton != null) {
            floatingActionButton.animate().cancel();
            floatingActionButton.animate().translationYBy(350);
            floatButtonHide = false;
        }
    }

    private void askForCameraPermission() {
        if (!marshMallowPermission.checkPermissionForCamera()) {
            Log.i(TAG, "Turns out marshmallow hasn't given the permission to use camera, gotta request it");
            marshMallowPermission.requestPermissionForCamera();
        }
        if (!marshMallowPermission.checkPermissionForExternalStorage()) {
            Log.i(TAG, "Turns out marshmallow hasn't given the permission to use external storage, gotta request it");
            marshMallowPermission.requestPermissionForExternalStorage();
        }
    }

    protected int sizeOf(Bitmap data) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            return data.getRowBytes() * data.getHeight();
        } else {
            return data.getByteCount();
        }
    }

    private void dispatchTakePictureIntent() {
        Log.i(TAG, "dispatching to take picture");
        askForCameraPermission();


        if(Build.VERSION.SDK_INT>=24){
            try{
                Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                m.invoke(null);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        //if (marshMallowPermission.checkPermissionForCamera() && marshMallowPermission.checkPermissionForExternalStorage()) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                photoFile = createImageFile();


                if (photoFile != null) {

                    Uri uriForFile = Uri.fromFile(photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriForFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } catch (IOException e) {
                log.error("error: {}", e);
            }
        }
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            try {
                Display display = getWindowManager().getDefaultDisplay();
                Point outSize = new Point();
                display.getSize(outSize);

                int x = outSize.x;
                int y = outSize.y;

                log.info("x:{}, y:{}", x, y);
                Uri uriForFile = Uri.fromFile(photoFile);

                this.bitmap = ImageUtils.convertToMutable(ImageUtils.getBitmapWithOrientationFix(this, uriForFile, x - 20, y - 40));
                log.info("Size Of actual bitmap: {}", sizeOf(this.bitmap));

                setupGameBoard();

                hideFloatingButton();
            } catch (Exception e) {
                Log.e(TAG, "exceptions : ", e);
            }
        }
    }

    private void setupGameBoard() {
        if (gameBoard != null) {
            contentMain.removeView(gameBoard);
        }

        gameBoard = new GameBoardView(this, this.bitmap);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        gameBoard.setLayoutParams(layoutParams);

        contentMain.addView(gameBoard);
        contentMain.requestLayout();

        Thread solverThread = new Thread(new Solver(gameBoard, winSolver));
        solverThread.start();
    }

    private File createImageFile() throws IOException {
        String imageFileName = "SELFIE_PUZZLE";
        File storeDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(imageFileName, ".jpg", storeDir);
        currentPhotoPath = image.getAbsolutePath();

        Log.i(TAG, "currentPhotoPath: =" + currentPhotoPath);

        return image;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static class Solver implements Runnable {
        private GameBoardView gameBoardView;
        private Handler handler;

        public Solver(GameBoardView gameBoardView, Handler handler) {
            this.gameBoardView = gameBoardView;
            this.handler = handler;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(TAG, "sleep interrupted", e);
                }
                if (gameBoardView.getMoveCount() > 1 && gameBoardView.isSolved()) {

                    Message message = Message.obtain(handler, SOLVED_STATUS);
                    message.sendToTarget();

                    break;
                }
            }
        }
    }
}
