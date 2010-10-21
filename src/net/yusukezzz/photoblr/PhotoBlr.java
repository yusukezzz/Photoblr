package net.yusukezzz.photoblr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

public class PhotoBlr extends Activity {
    private final static String TUMBLR_API_BASE_URL  = "http://www.tumblr.com/api/";
    private final static String TUMBLR_API_DASHBOARD = TUMBLR_API_BASE_URL + "dashboard";
    private ArrayList<String>   origins              = new ArrayList<String>();
    private ArrayList<String>   thumbnails           = new ArrayList<String>();
    private float               scaledDensity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        String id = Setting.getId(getApplicationContext());
        String pass = Setting.getPass(getApplicationContext());

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        scaledDensity = metrics.scaledDensity;
        // 接続
        this.readDashboard(id, pass);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (thumbnails.size() == 0) {
            String id = Setting.getId(getApplicationContext());
            String pass = Setting.getPass(getApplicationContext());
            this.readDashboard(id, pass);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, "setting");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                // Settingへ
                Intent intent = new Intent(this, Setting.class);
                startActivity(intent);
                break;
            default:
                break;
        }
        return true;
    }

    private void setImages(String xml) {
        XmlPullParser parser = Xml.newPullParser();
        int eventType;
        Boolean getThumbnail = true;
        try {
            parser.setInput(new StringReader(xml));
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    // 元画像URLが初出だったらサムネイルも取得させる
                    if ("photo-link-url".equals(parser.getName())) {
                        String url = parser.nextText();
                        getThumbnail = !origins.contains(url);
                        if (getThumbnail) {
                            origins.add(url);
                        }
                    }
                    // サムネイル取得フラグがあったら
                    if ("photo-url".equals(parser.getName()) && getThumbnail) {
                        int width = Integer.parseInt(parser.getAttributeValue(null, "max-width"));
                        if (width == 75) {
                            thumbnails.add(parser.nextText());
                            getThumbnail = false;
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
        }
    }

    private void readDashboard(String id, String pass) {
        if (id.equals("") || pass.equals("")) {
            return;
        }
        HttpClient http = new DefaultHttpClient();
        HttpPost post = new HttpPost(PhotoBlr.TUMBLR_API_DASHBOARD);
        List<NameValuePair> params = new ArrayList<NameValuePair>(4);
        params.add(new BasicNameValuePair("email", id));
        params.add(new BasicNameValuePair("password", pass));
        params.add(new BasicNameValuePair("type", "photo"));
        params.add(new BasicNameValuePair("num", "50"));
        try {
            post.setEntity(new UrlEncodedFormEntity(params));
            HttpResponse res = http.execute(post);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            res.getEntity().writeTo(os);
            this.setImages(os.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // view 更新
        GridView gv = (GridView) findViewById(R.id.GridView01);
        gv.setAdapter(new ImageAdapter());

    }

    public class ImageAdapter extends BaseAdapter {
        private final Downloader downloader = new Downloader();

        @Override
        public int getCount() {
            return origins.size();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView iv = null;
            if (convertView == null) {
                float size = 45 * scaledDensity;
                iv = new ImageView(parent.getContext());
                iv.setLayoutParams(new GridView.LayoutParams((int) size, (int) size));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setAdjustViewBounds(true);
            } else {
                iv = (ImageView) convertView;
            }
            downloader.download(thumbnails.get(position), iv);
            return iv;
        }
    }
}