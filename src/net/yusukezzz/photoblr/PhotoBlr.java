package net.yusukezzz.photoblr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

public class PhotoBlr extends Activity implements AdapterView.OnItemClickListener {
    private final static String TUMBLR_API_BASE_URL  = "http://www.tumblr.com/api/";
    private final static String TUMBLR_API_DASHBOARD = TUMBLR_API_BASE_URL + "dashboard";
    private static final int    REQUEST_PHOTO_VIEW   = 0;
    private static final int    IMAGE_SIZE_LARGE     = 1280;
    private static final int    IMAGE_SIZE_MIDDLE    = 500;
    private static final int    IMAGE_SIZE_SMALL     = 250;
    private float               scaledDensity;
    private int                 page                 = 0;
    private GridView            gridview;
    private ImageAdapter        adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        String id = Setting.getId(getApplicationContext());
        String pass = Setting.getPass(getApplicationContext());

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        scaledDensity = metrics.scaledDensity;
        // view
        adapter = new ImageAdapter();
        gridview = (GridView) findViewById(R.id.GridView01);
        gridview.setOnItemClickListener(this);
        gridview.setAdapter(adapter);
        // 接続
        this.readDashboard(id, pass, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter.getCount() == 0) {
            String id = Setting.getId(getApplicationContext());
            String pass = Setting.getPass(getApplicationContext());
            this.readDashboard(id, pass, 0);
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

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        String url = ((ImageAdapter) this.gridview.getAdapter()).getPreviewUrl(position);
        // コールバック付きで遷移
        Intent intent = new Intent(this, PhotoView.class);
        intent.putExtra("url", url);
        startActivityForResult(intent, REQUEST_PHOTO_VIEW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PHOTO_VIEW) {
            if (requestCode == RESULT_OK) {
                return;
            }
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "ダウンロード失敗", Toast.LENGTH_SHORT).show();
            }
        }
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
                        getThumbnail = !adapter.contains(url);
                        if (getThumbnail) {
                            adapter.addOrigin(url);
                        }
                    }
                    // サムネイル取得フラグがあったら
                    if ("photo-url".equals(parser.getName()) && getThumbnail) {
                        int width = Integer.parseInt(parser.getAttributeValue(null, "max-width"));
                        if (width == IMAGE_SIZE_LARGE) {
                            adapter.addPreview(parser.nextText());
                        }
                        if (width == 75) {
                            adapter.addThumbnail(parser.nextText());
                            getThumbnail = false;
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readDashboard(String id, String pass, int start) {
        if (id.equals("") || pass.equals("")) {
            return;
        }
        HttpClient http = new DefaultHttpClient();
        HttpPost post = new HttpPost(PhotoBlr.TUMBLR_API_DASHBOARD);
        List<NameValuePair> params = new ArrayList<NameValuePair>(4);
        params.add(new BasicNameValuePair("email", id));
        params.add(new BasicNameValuePair("password", pass));
        params.add(new BasicNameValuePair("type", "photo"));
        params.add(new BasicNameValuePair("start", "" + start));
        params.add(new BasicNameValuePair("num", "25"));
        try {
            post.setEntity(new UrlEncodedFormEntity(params));
            HttpResponse res = http.execute(post);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            res.getEntity().writeTo(os);
            this.setImages(os.toString());
            // debug("recieve xml: " + os.toString());
            try {
                OutputStream out = openFileOutput("a.txt", MODE_PRIVATE);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
                writer.append(os.toString());
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            debug("recieve xml: " + e.toString());
        }
    }

    public class ImageAdapter extends BaseAdapter {
        private final Downloader  downloader = new Downloader();
        private ArrayList<String> origins    = new ArrayList<String>();
        private ArrayList<String> previews   = new ArrayList<String>();
        private ArrayList<String> thumbnails = new ArrayList<String>();

        public boolean contains(String url) {
            return origins.contains(url);
        }

        public void addOrigin(String url) {
            origins.add(url);
        }

        public void addPreview(String url) {
            previews.add(url);
        }

        public void addThumbnail(String url) {
            thumbnails.add(url);
            this.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return previews.size();
        }

        public String getPreviewUrl(int position) {
            return previews.get(position);
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView iv = null;
            if (convertView == null) {
                float size = 75 * scaledDensity;
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

    public void debug(String msg) {
        Log.d("photoblr", msg);
    }
}