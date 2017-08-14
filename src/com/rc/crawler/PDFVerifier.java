package com.rc.crawler;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
import org.apache.pdfbox.pdfparser.PDFParser;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Class to verify if a given PDF is not corrupted
 */
class PDFVerifier implements Callable<String> {

    private final File file;

    PDFVerifier(File file) {
        this.file = file;
    }

    @Override
    public String call() throws Exception {
        String result = "";
        try {
            PDFParser parser = new PDFParser(new RandomAccessBufferedFileInputStream(file));
            parser.parse();
            COSDocument cosDoc = parser.getDocument();
            cosDoc.close();
        } catch (Exception e) {
            result = "Invalid File";
        }
        return result;
    }
}
