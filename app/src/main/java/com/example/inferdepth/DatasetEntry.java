package com.example.inferdepth;

import androidx.documentfile.provider.DocumentFile;

public class DatasetEntry {
    public final DocumentFile leftImage, rightImage, disparityImage;

    public DatasetEntry(DocumentFile leftImage, DocumentFile rightImage, DocumentFile disparityImage) {
        this.leftImage = leftImage;
        this.rightImage = rightImage;
        this.disparityImage = disparityImage;
    }
}
