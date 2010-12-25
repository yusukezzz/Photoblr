package net.yusukezzz.photoblr;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.widget.ImageView;

public class Downloader {
    public void download(String url, ImageView iv) {
        resetPurgeTimer();
        Bitmap bm = getBitmapFromCache(url);

        if (bm == null) {
            forceDownload(url, iv);
        } else {
            cancelPotentialDownload(url, iv);
            iv.setImageBitmap(bm);
        }
    }

    private void forceDownload(String url, ImageView iv) {
        if (url == null) {
            iv.setImageBitmap(null);
            return;
        }

        if (cancelPotentialDownload(url, iv)) {
            DownloaderTask task = new DownloaderTask(iv);
            DownloadedDrawable drawable = new DownloadedDrawable(task);
            iv.setImageDrawable(drawable);
            task.execute(url);
        }
    }

    private static boolean cancelPotentialDownload(String url, ImageView iv) {
        DownloaderTask task = getDownloaderTask(iv);
        if (task != null) {
            String bitmapUrl = task.url;
            if (bitmapUrl == null || !bitmapUrl.equals(url)) {
                task.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static DownloaderTask getDownloaderTask(ImageView iv) {
        if (iv != null) {
            Drawable drawable = iv.getDrawable();
            if (drawable instanceof DownloadedDrawable) {
                DownloadedDrawable downloadedDrawable = (DownloadedDrawable) drawable;
                return downloadedDrawable.getDownloaderTask();
            }
        }
        return null;
    }

    public Bitmap downloadBitmap(String url) {
        final HttpClient client = new DefaultHttpClient();
        final HttpGet getRequest = new HttpGet(url);

        try {
            HttpResponse response = client.execute(getRequest);
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                return null;
            }

            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream is = null;
                try {
                    is = entity.getContent();
                    return BitmapFactory.decodeStream(new FlushedInputStream(is));
                } finally {
                    if (is != null) {
                        is.close();
                    }
                    entity.consumeContent();
                }
            }
        } catch (IOException e) {
            // TODO: handle exception
        } catch (IllegalStateException e) {
            // TODO: handle exception
        } catch (Exception e) {
            // TODO: handle exception
        }
        return null;
    }

    static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break; // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }

    class DownloaderTask extends AsyncTask<String, Void, Bitmap> {
        private String                         url;
        private final WeakReference<ImageView> ivReference;

        public DownloaderTask(ImageView iv) {
            ivReference = new WeakReference<ImageView>(iv);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            url = params[0];
            return downloadBitmap(url);
        }

        @Override
        protected void onPostExecute(Bitmap bm) {
            if (isCancelled()) {
                bm = null;
            }
            addBitmapToCache(url, bm);
            if (ivReference != null) {
                ImageView iv = ivReference.get();
                DownloaderTask task = getDownloaderTask(iv);
                if (this == task) {
                    iv.setImageBitmap(bm);
                }
            }
        }
    }

    static class DownloadedDrawable extends ColorDrawable {
        private final WeakReference<DownloaderTask> dTaskReference;

        public DownloadedDrawable(DownloaderTask task) {
            super(Color.BLACK);
            dTaskReference = new WeakReference<Downloader.DownloaderTask>(task);
        }

        public DownloaderTask getDownloaderTask() {
            return dTaskReference.get();
        }
    }

    private static final int    HARD_CACHE_CAPACITY = 100;
    private static final int    DELAY_BEFORE_PURGE  = 60 * 1000;

    @SuppressWarnings("serial")
    private final HashMap<String, Bitmap> sHardBitmapCache =
        new LinkedHashMap<String, Bitmap>(HARD_CACHE_CAPACITY / 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                    LinkedHashMap.Entry<String, Bitmap> eldest) {
                if (size() > HARD_CACHE_CAPACITY) {
                    sSoftBitmapCache.put(eldest.getKey(),
                            new SoftReference<Bitmap>(
                                    eldest.getValue()));
                    return true;
                } else {
                    return false;
                }
            }
        };

    private final static ConcurrentHashMap<String, SoftReference<Bitmap>> sSoftBitmapCache =
        new ConcurrentHashMap<String, SoftReference<Bitmap>>(HARD_CACHE_CAPACITY / 2);

    private final Handler   purgeHandler    = new Handler();
    private final Runnable  purger          = new Runnable() {
        public void run() {
            clearCache();
        }
    };

    private void addBitmapToCache(String url, Bitmap bm) {
        if (bm != null) {
            synchronized (sHardBitmapCache) {
                sHardBitmapCache.put(url, bm);
            }
        }
    }

    private Bitmap getBitmapFromCache(String url) {
        // First try the hard reference cache
        synchronized (sHardBitmapCache) {
            final Bitmap bitmap = sHardBitmapCache.get(url);
            if (bitmap != null) {
                // Bitmap found in hard cache
                // Move element to first position, so that it is removed last
                sHardBitmapCache.remove(url);
                sHardBitmapCache.put(url, bitmap);
                return bitmap;
            }
        }

        // Then try the soft reference cache
        SoftReference<Bitmap> bitmapReference = sSoftBitmapCache.get(url);
        if (bitmapReference != null) {
            final Bitmap bitmap = bitmapReference.get();
            if (bitmap != null) {
                // Bitmap found in soft cache
                return bitmap;
            } else {
                // Soft reference has been Garbage Collected
                sSoftBitmapCache.remove(url);
            }
        }

        return null;
    }

    public void clearCache() {
        sHardBitmapCache.clear();
        sSoftBitmapCache.clear();
    }

    /**
     * Allow a new delay before the automatic cache clear is done.
     */
    private void resetPurgeTimer() {
        purgeHandler.removeCallbacks(purger);
        purgeHandler.postDelayed(purger, DELAY_BEFORE_PURGE);
    }

}
