package net.yusukezzz.photoblr;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.widget.ImageView;

public class PhotoView extends Activity implements Runnable {
    private Intent         intent;
    private ImageView      img;
    private ProgressDialog pDialog;
    private Handler        handler = new Handler() {
        public void handleMessage(Message msg) {
            pDialog.dismiss();
        }
    };
    private String         url;
    private Downloader downloader = new Downloader();;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photo_view);

        // Intentで渡された値を取得
        intent = getIntent();
        url = intent.getStringExtra("url");

        img = (ImageView) findViewById(R.id.ImageView01);
        pDialog = new ProgressDialog(this);
        pDialog.setTitle("downloading...");
        pDialog.setMessage("お待ち下さい");
        pDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pDialog.show();

        // 画像表示
        Thread t = new Thread(this);
        t.start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // RESULT_CODE付けて前のActivityに戻る
            setResult(RESULT_OK);
            finish();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void run() {
        final Bitmap bm = downloader.downloadBitmap(url);
        if (bm != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    img.setImageBitmap(bm);
                }
            });
        }
        handler.sendEmptyMessage(0);
    }
}
