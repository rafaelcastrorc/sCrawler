package com.rc.crawler;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;

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
    public String call() {
        String result = "";
        try {
            PDDocument doc = PDDocument.load(file);
            doc.getNumberOfPages();
            COSDocument cosDoc = doc.getDocument();
            cosDoc.close();
            doc.close();
            //Catch any type of exception caused for parsing the document
        } catch (Exception e) {
            result = "Invalid File";
        }
        return result;
    }
}
