package remix.myplayer.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import remix.myplayer.R;
import remix.myplayer.util.thumb.SearchCover;

/**
 * Created by Remix on 2015/11/30.
 */

/**
 * 通用工具类
 */
public class CommonUtil {
    public static CommonUtil mInstace = null;
    private static Context mContext;

    public static void setContext(Context context) {
        mContext = context;
    }


    /**
     * 压缩图片用于分享
     * @param bmp 原始图片
     * @param needRecycle 是否需要回收
     * @return 压缩后的byte数组
     */
    public static byte[] bmpToByteArray(final Bitmap bmp, final boolean needRecycle) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, output);
        if (needRecycle) {
            bmp.recycle();
        }

        byte[] result = output.toByteArray();
        try {
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static Bitmap doBlur(Bitmap sentBitmap,int radius){
        Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);

        final RenderScript rs = RenderScript.create(mContext);
        final Allocation input = Allocation.createFromBitmap(rs, sentBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT);
        final Allocation output = Allocation.createTyped(rs, input.getType());
        final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        script.setRadius(radius /* e.g. 3.f */);
        script.setInput(input);
        script.forEach(output);
        output.copyTo(bitmap);
        return bitmap;
    }

    //高斯模糊
    /**
     *
     * @param sentBitmap 需要模糊的bitmap
     * @param radius 模糊半径 数值越大，模糊程度越高
     * @param canReuseInBitmap
     * @return 高斯模糊后的bitmap
     */
    public static Bitmap doBlur(Bitmap sentBitmap, int radius, boolean canReuseInBitmap) {
        Bitmap bitmap;
        if (canReuseInBitmap) {
            bitmap = sentBitmap;
        } else {
            bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
        }
        if (radius < 1) {
            return (null);
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16)
                        | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return (bitmap);
    }


    //获得歌曲时长，分：秒

    /**
     * 根据时间戳，解析时间
     * @param duration 时间戳
     * @return 00:00格式的时间
     */
    public static String getTime(long duration) {
        int minute = (int)duration / 1000 / 60;
        int second = ((int)duration - minute * 60000) / 1000;
        //如果分钟数小于10
        if(minute < 10) {
            if(second < 10)
                return "0" + minute + ":0" + second;
            else
                return "0" + minute + ":" + second;
        } else {
            if(second < 10)
                return minute + ":0" + second;
            else
                return minute + ":" + second;
        }
    }

    /**
     * 判断网路是否连接
     * @return
     */
    public static boolean isNetWorkConnected() {
        if(mContext != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetWorkInfo = mConnectivityManager.getActiveNetworkInfo();
            if(mNetWorkInfo != null)
                return mNetWorkInfo.isAvailable() && mNetWorkInfo.isConnected();
        }
        return false;
    }

    /**
     * 删除歌曲
     * @param path 歌曲路径
     * @return 是否删除成功
     */
    public static boolean deleteFile(String path){
        File file = new File(path);
        if(file.exists())
            return file.delete();
        return false;
    }

    /**
     * 处理歌曲名、歌手名或者专辑名
     * @param origin 原始数据
     * @param type 处理类型 0:歌曲名 1:歌手名 2:专辑名
     * @return
     */
    public static final int SONGTYPE = 0;
    public static final int ARTISTTYPE = 1;
    public static final int ALBUMTYPE = 2;
    public static String processInfo(String origin,int type){
        if(type == SONGTYPE){
            if(origin == null || origin.equals("") || origin.contains("unknown") ){
                return mContext.getString(R.string.unknow_song);
            } else {
                return origin.lastIndexOf(".") > 0 ? origin.substring(0, origin.lastIndexOf(".")) : origin;
            }
        } else{
            if(origin == null || origin.equals("") || origin.contains("unknown") ){
                return mContext.getString(type == ARTISTTYPE ? R.string.unknow_artist : R.string.unknow_album);
            } else {
                return origin;
            }
        }
    }

    /**
     * 返回关键词的MD值
     * @param key
     * @return
     */
    public static String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 根据专辑id返回封面的url
     * @param albumId
     * @return
     */
    public static JSONObject getCoverJsonObject(int albumId){
        BufferedReader br = null;
        StringBuffer strBuffer = new StringBuffer();
        String s;
        try {
            URL albumUrl = new URL("http://geci.me/api/cover/"  + albumId);
            HttpURLConnection httpURLConnection = (HttpURLConnection)albumUrl.openConnection();
            httpURLConnection.connect();;
            InputStreamReader inReader = new InputStreamReader(httpURLConnection.getInputStream());
            br = new BufferedReader(inReader);
            if(br == null)
                return null;
            while((s = br.readLine()) != null){
                strBuffer.append(s);
            }
            return new JSONObject(strBuffer.toString());

        }  catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                if(br != null) {
                    br.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * 根据歌曲名和歌手请求歌曲信息
     * @param songname
     * @param artistname
     * @return
     */
    public static JSONObject getSongJsonObject(String songname, String artistname){
        URL lrcIdUrl = null;
        try {
            lrcIdUrl = new URL("http://geci.me/api/lyric/" + songname + "/" + artistname);
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        BufferedReader br = null;
        String s;
        StringBuilder strBuffer = new StringBuilder();
        try {
            HttpURLConnection httpConn = (HttpURLConnection) lrcIdUrl.openConnection();
            httpConn.connect();
            InputStreamReader inReader = new InputStreamReader(httpConn.getInputStream());
            br = new BufferedReader(inReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if(br == null)
                return null;
            while((s = br.readLine()) != null){
                strBuffer.append(s);
            }
            return new JSONObject(strBuffer.toString());
        }
        catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            try {
                if(br != null) {
                    br.close();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 判断某个专辑在本地数据库是否有封面
     * @param uri
     * @return
     */
    public static boolean isAlbumThumbExistInDB(Uri uri){
        boolean exist = false;
        InputStream stream = null;
        try {
            stream = mContext.getContentResolver().openInputStream(uri);
            exist = true;
        } catch (Exception e) {
            exist = false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return exist;
    }

    /**
     * 下载专辑封面并根据专辑id保存
     * @param songname
     * @param artist
     * @param albumid
     */
    public static String downAlbumCover(String songname,String artist,long albumid){
        String urlstr = new SearchCover(songname,artist,SearchCover.COVER).getImgUrl();
        URL url = null;
        BufferedReader br = null;
        StringBuffer strBuffer = new StringBuffer();
        FileOutputStream fos = null;
        InputStream in = null;
        String s;
        File img = null;
        try {
            url = new URL(urlstr);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.connect();
            in = httpURLConnection.getInputStream();

            if(in == null || !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                return "";

            File dir = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + mContext.getPackageName() + "/cache/cover");
            if(!dir.exists())
                dir.mkdir();
            img = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + mContext.getPackageName() + "/cache/cover/" + albumid  + ".jpg");
            fos = new FileOutputStream(img);

            byte[] bs = new byte[1024];
            int len = 0;
            // 开始读取
            while ((len = in.read(bs)) != -1) {
                fos.write(bs);
            }
            if(fos != null)
                fos.flush();

        } catch (Exception e){
            e.printStackTrace();
        } finally {
            try {
                if(fos != null)
                    fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if(in != null)
                    in.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        return img == null ? "" : img.getAbsolutePath();
    }

    public static String getCoverInCache(long albumId){
        File coverCacheDir = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + mContext.getPackageName() + "/cache/cover");
        if(coverCacheDir.isDirectory()){
            File files[] = coverCacheDir.listFiles();
            if(files != null){
                for (File f : files){
                    if(f.getName().equals(albumId + "")){
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        return "";
    }
}