package com.rc.crawler;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provides different methods to handle files
 */
class FileUtilities {

    /**
     * Unzip a file
     *
     * @param zipFilePath   zip location
     * @param destDirectory destination
     * @return name of the unzipped file
     * @throws IOException Error writing to file
     */
    static String unzip(String zipFilePath, String destDirectory) throws IOException {
        String mainFile = "";
        boolean first = true;
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                if (first) {
                    first = false;
                    mainFile = entry.getName();
                }
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdir();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
        //Delete the zip file
        new File(zipFilePath).delete();
        return mainFile;
    }

    /**
     * Extracts a zip entry (file entry)
     *
     * @throws IOException Unable to write file
     */
    static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    /**
     * Copy file from one location to another
     *
     * @param input  InputStream
     * @param output OutputStream
     * @throws IOException Error copying file
     */
    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int n = input.read(buf);
        while (n >= 0) {
            output.write(buf, 0, n);
            n = input.read(buf);
        }
        output.flush();
    }
}
