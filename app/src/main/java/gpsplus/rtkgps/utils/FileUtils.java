
package gpsplus.rtkgps.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;

import gpsplus.rtkgps.InternalReceiverToRtklib;

public class FileUtils {

    public static final String LOG_SUFFIX = ".txt";

    /**
     * 创建文件写入内容
     * 自定义传入Debug
     *
     * @param args args[0] filePath 文件存储路径
     *             args[1]  prefix 文件前缀
     *             args[2] fileContext 文件写入内容
     * @throws IOException
     */
    public static void dumpToSDCard(boolean isDebug, String... args) {
        if (!isDebug) {
            return;
        }

        try {
            FileUtils.clearCache(new File(args[0]), LOG_SUFFIX, 5);
            // 如果SD卡不存在或无法使用，则无法把异常信息写入SD卡
            dumpExceptionToSDCard(args);
        } catch (IOException e) {
            e.printStackTrace();
//            ioToSDCard(InternalReceiverToRtklib.FILE_PATH_LOG, "eIO_", "写入日志异常:" + e.getMessage());
        }
    }

    /**
     * 创建文件写入内容
     * Debug模式
     *
     * @param args args[0] filePath 文件存储路径
     *             args[1]  prefix 文件前缀
     *             args[2] fileContext 文件写入内容
     * @throws IOException
     */
    public static void dumpToSDCard(String... args) {
        try {
            // 如果SD卡不存在或无法使用，则无法把异常信息写入SD卡
            dumpExceptionToSDCard(args);
        } catch (IOException e) {
            e.printStackTrace();
//            ioToSDCard(InternalReceiverToRtklib.FILE_PATH_LOG, "eIO_", "写入日志异常:" + e.getMessage());
        }
    }

    /**
     * 写入日志异常
     *
     * @param args
     */
    public static void ioToSDCard(String... args) {
        // 如果SD卡不存在或无法使用，则无法把异常信息写入SD卡
        try {
            dumpExceptionToSDCard(args);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建文件写入内容
     *
     * @param args args[0] filePath 文件存储路径
     *             args[1]  prefix 文件前缀
     *             args[2] fileContext 文件写入内容
     * @throws IOException
     */
    private static void dumpExceptionToSDCard(String... args) throws IOException {
        // 如果SD卡不存在或无法使用，则无法把异常信息写入SD卡
        if (!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            return;
        }

        File dir = new File(args[0]);
        if (!dir.exists()) {
            dir.mkdirs();
        }
//        String time = DateUtil.getCurrentDate(DateUtil.dateFormatYMDHMS);
        // 以当前时间创建log文件
        File file = new File(dir.getPath(), args[1] + LOG_SUFFIX);
        try {
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(
                    file)));
//            pw.println(time);
            pw.println(args[2]);
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建根缓存目录
     *
     * @return
     */
    public static String createRootPath(Context context) {
        String cacheRootPath = "";
        if (isSdCardAvailable()) {
            // /sdcard/Android/data/<application package>/cache
            cacheRootPath = context.getExternalCacheDir().getPath();
        } else {
            // /data/data/<application package>/cache
            cacheRootPath = context.getCacheDir().getPath();
        }
        return cacheRootPath;
    }

    /**
     * 判断是否有SD卡
     *
     * @return
     */
    public static boolean isSdCardAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 递归删除文件夹
     *
     * @param path
     */
    public static void deleteAllFilesOfDir(File path) {
        if (!path.exists())
            return;
        if (path.isFile()) {
            path.delete();
            return;
        }
        File[] files = path.listFiles();
        for (int i = 0; i < files.length; i++) {
            deleteAllFilesOfDir(files[i]);
        }
        path.delete();
    }

    /**
     * 递归创建文件夹
     *
     * @param dirPath
     * @return 创建失败返回""
     */
    public static String createDir(String dirPath) {
        try {
            File file = new File(dirPath);
            if (file.getParentFile().exists()) {
                file.mkdir();
                return file.getAbsolutePath();
            } else {
                createDir(file.getParentFile().getAbsolutePath());
                file.mkdir();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dirPath;
    }

    /**
     * 递归创建文件夹
     *
     * @param file
     * @return 创建失败返回""
     */
    public static String createFile(File file) {
        try {
            if (file.getParentFile().exists()) {
                file.createNewFile();
                return file.getAbsolutePath();
            } else {
                createDir(file.getParentFile().getAbsolutePath());
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static void writeFile(String filePathAndName, String fileContent) {
        try {
            OutputStream outstream = new FileOutputStream(filePathAndName);
            OutputStreamWriter out = new OutputStreamWriter(outstream);
            out.write(fileContent);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 文件拷贝
     *
     * @param src  源文件
     * @param desc 目的文件
     */
    public static void fileChannelCopy(File src, File desc) {
        FileInputStream fi = null;
        FileOutputStream fo = null;
        try {
            fi = new FileInputStream(src);
            fo = new FileOutputStream(desc);
            FileChannel in = fi.getChannel();//得到对应的文件通道
            FileChannel out = fo.getChannel();//得到对应的文件通道
            in.transferTo(0, in.size(), out);//连接两个通道，并且从in通道读取，然后写入out通道
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fo != null) fo.close();
                if (fi != null) fi.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 打开Asset下的文件
     *
     * @param context
     * @param fileName
     * @return
     */
    public static InputStream openAssetFile(Context context, String fileName) {
        AssetManager am = context.getAssets();
        InputStream is = null;
        try {
            is = am.open(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return is;
    }

    /**
     * 取得文件大小
     *
     * @param f
     * @return
     * @throws Exception
     */
    public static long getFileSizes(File f) throws Exception {
        long s = 0;
        if (f.exists()) {
            FileInputStream fis = null;
            fis = new FileInputStream(f);
            s = fis.available();
        } else {
            f.createNewFile();
            System.out.println("文件不存在");
        }
        return s;
    }

    /**
     * 取得文件夹大小
     *
     * @param f
     * @return
     * @throws Exception
     */
    public static long getFileSize(File f) throws Exception {
        long size = 0;
        File flist[] = f.listFiles();
        for (int i = 0; i < flist.length; i++) {
            if (flist[i].isDirectory()) {
                size = size + getFileSize(flist[i]);
            } else {
                size = size + flist[i].length();
            }
        }
        return size;
    }

    /**
     * 转换文件大小
     *
     * @param fileS
     * @return
     */
    public static String FormetFileSize(long fileS) {//
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        if (fileS < 1024) {
            fileSizeString = df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            fileSizeString = df.format((double) fileS / 1024) + "K";
        } else if (fileS < 1073741824) {
            fileSizeString = df.format((double) fileS / 1048576) + "M";
        } else {
            fileSizeString = df.format((double) fileS / 1073741824) + "G";
        }
        return fileSizeString;
    }

    /**
     * 递归求取目录文件个数
     *
     * @param f
     * @return
     */
    public static long getlist(File f) {
        long size = 0;
        File flist[] = f.listFiles();
        size = flist.length;
        for (int i = 0; i < flist.length; i++) {
            if (flist[i].isDirectory()) {
                size = size + getlist(flist[i]);
                size--;
            }
        }
        return size;
    }

    /**
     * 删除缓存文件夹,文件夹内容不为空,先删除文件夹内的文件
     *
     * @param file
     */
    public static void deleteFile(File file) {
        if (file.isFile()) {
            file.delete();
            return;
        }

        if (file.isDirectory()) {
            File[] childFiles = file.listFiles();
            if (childFiles == null || childFiles.length == 0) {
                file.delete();
                return;
            }

            for (int i = 0; i < childFiles.length; i++) {
                deleteFile(childFiles[i]);
            }
            file.delete();
        }
    }

    /**
     * 获取草稿箱内容
     *
     * @param filePath
     */
    public static String getFileDraft(String filePath) {
        File file = new File(filePath);
        if (!file.isFile()) {
            file.delete();
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try {
//            LogUtils.e(FormetFileSize(file.length()));
            InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader br = new BufferedReader(isr);

            String mimeTypeLine = null;
            while ((mimeTypeLine = br.readLine()) != null) {
                sb.append(mimeTypeLine);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * 清理缓存
     *
     * @param file
     * @param suffix     文件后缀
     * @param cacheCount 缓存文件数量
     */
    public static void clearCache(File file, final String suffix, final int cacheCount) {
        if (file.isDirectory()) {
            File[] files = file.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(suffix);
                }
            });
            File temp;
            int count = cacheCount;//缓存文件个数
            if (files.length > count) {
                for (int i = 2; i < files.length; i++) {
                    temp = files[i - 2].lastModified() > files[i - 1].lastModified() ? files[i - 1] : files[i - 2];
                    temp = files[i].lastModified() > temp.lastModified() ? temp : files[i];
                    temp.delete();
                }
            }
        }
    }
}