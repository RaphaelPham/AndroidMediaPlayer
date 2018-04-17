package com.example.deadpool.mediaplayer;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SplashScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread task = new Thread()
        {
            public void run() {
                checkAndRequestPermissions();
                ArrayList<Song> scannedSongs = getSongList();
                Intent i = new Intent(SplashScreenActivity.this, MainActivity.class);
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList("SCANNED_SONGS", scannedSongs);
                i.putExtras(bundle);
                startActivity(i);
                finish();
            }
        };
        task.start();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.MEDIA_CONTENT_CONTROL,
                Manifest.permission.READ_PHONE_STATE
        };
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 1);
        }
    }

    private ArrayList<Song> getSongList() {
        Log.d("INFO", "Start get music contents");
        // Retrieve the URI for external music files, and create a Cursor instance using the ContentResolver instance to query the music files
        ArrayList<Song> result = new ArrayList<>();
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Uri albumUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor musicCursor = musicResolver.query(musicUri, null, selection, null, null);
        // iterate over the results
        if (musicCursor != null && musicCursor.moveToFirst()) {
            Log.d("INFO", "Have songs result on Sdcard");
            //get columns
            int idColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
            int titleColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
            int artistColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST);
            int durationColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int albumColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int albumIdColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                long thisDuration = musicCursor.getLong(durationColumn);
                String thisAlbum = musicCursor.getString(albumColumn);
                MediaMetadataRetriever mr = new MediaMetadataRetriever();
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, thisId);
                mr.setDataSource(this, trackUri);
                String thisGenre = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                Song thisSong = new Song(this, thisId, thisTitle, thisArtist, thisDuration, thisAlbum, thisGenre);
                long idAlbum = musicCursor.getLong(albumIdColumn);
                String selectAlbumArt = MediaStore.Audio.Albums._ID + "==" + idAlbum;
                Cursor albumCursor = musicResolver.query(albumUri, null, selectAlbumArt, null, null);
                if (albumCursor != null && albumCursor.moveToFirst()) {
                    int x = albumCursor.getColumnIndex(android.provider.MediaStore.Audio.Albums.ALBUM_ART);
                    thisSong.albumArt = albumCursor.getString(x);
                    albumCursor.close();
                }
                result.add(thisSong);
            }
            while (musicCursor.moveToNext());
            musicCursor.close();
        }
        Log.d("INFO", "Loaded " + result.size() + " songs");
        // sort the songs list by title
        Collections.sort(result, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
        return result;
    }
}