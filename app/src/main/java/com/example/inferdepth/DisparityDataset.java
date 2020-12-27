package com.example.inferdepth;

import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DisparityDataset {
    public static final String TAG = "DisparityDataset";

    private final List<DatasetEntry> entries;

    public DisparityDataset(DocumentFile datasetBaseDir) {
        DocumentFile lViewBase = datasetBaseDir.findFile("clean_left").listFiles()[0];
        DocumentFile rViewBase  = datasetBaseDir.findFile("clean_right").listFiles()[0];
        DocumentFile disparityBase  = datasetBaseDir.findFile("disparities").listFiles()[0];
        List<String> frames = getFrames(lViewBase);
        entries = new ArrayList<>();
        for(String frame: frames){
            entries.add(new DatasetEntry(lViewBase.findFile(frame), rViewBase.findFile(frame),disparityBase.findFile(frame)));
        }
        Log.d(TAG,"# entries: "+entries.size());
    }

    private List<String> getFrames(DocumentFile viewBase){
        List<String> frames = new ArrayList<>();
        for(DocumentFile frame: viewBase.listFiles()){
            frames.add(frame.getName());
        }
        Collections.sort(frames);
        return frames;
    }
    public List<DatasetEntry> getEntries() {
        return entries;
    }
}
