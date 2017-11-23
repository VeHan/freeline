package com.veyhey.freeline.processor;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;

/**
 * @author hanweiwei @Hangzhou Youzan Technology Co.Ltd
 * @date 17/11/8
 */

public class FreelineUtils {

    public static <T> T getJson(String filePath, Class<T> clazz) {
        try {
            String content = readFileToString(filePath);
            return new Gson().fromJson(content, clazz);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean saveJson(String json, String fileName, boolean override) {
        File pending = new File(fileName);
        if (pending.exists() && pending.isFile()) {
            if (override) {
                System.out.println(String.format("Old file %s removed.", pending.getAbsolutePath()));
                pending.delete();
            } else {
                System.out.println(String.format("File %s exists.", pending.getAbsolutePath()));
                return false;
            }
        }
        try {
            pending.getParentFile().mkdirs();
            pending.createNewFile();
            writeFile(pending, new StringReader(json));
            System.out.println(String.format("Save to %s", pending.getAbsolutePath()));
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void writeFile(File file, Reader input) {
        Writer output = null;
        try {
            output = new FileWriter(file);
            char buffer[] = new char[4 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                output.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void writeFile(File file, InputStream input) {
        OutputStream output = null;
        try {
            output = new FileOutputStream(file);
            byte buffer[] = new byte[4 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                output.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String readFileToString(String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        fin.close();
        return ret;
    }
}
