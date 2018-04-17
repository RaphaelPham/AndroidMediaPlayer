package com.example.deadpool.mediaplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.KeyNotFoundException;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

import java.io.File;
import java.io.IOException;

public class Song implements Parcelable {
    private Context context;
    private long id;
    private String title;
    private String artist;
    private long duration;
    private String album;
    public String albumArt;
    private String genre;
    private String lyrics;

    public Song(Context context, long songID, String songTitle, String songArtist, long songDuration, String album, String genre) {
        this.context = context;
        id = songID;
        title = songTitle;
        artist = songArtist;
        duration = songDuration;
        this.album = album;
        this.genre = genre;
        this.lyrics = retrieveLyrics();
    }

    public long getID() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getDuration() { return duration; }
    public String getAlbum() { return album; }
    public String getGenre() { return genre; }
    public String getLyrics() { return lyrics; }
    private String getPath() {
        Cursor cursor = null;
        Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        try {
            String[] projection = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(trackUri, projection, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } catch (Exception e) {
            Log.e("INFO", "getRealPathFromURI Exception : " + e.toString());
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String retrieveLyrics() {
        String lyric;
        try {
            AudioFile audioFile = AudioFileIO.read(new File(getPath()));
            if (audioFile == null)
                return "";
            Tag tag = audioFile.getTag();
            if (tag == null)
                return "";
            lyric = tag.getFirst(FieldKey.LYRICS);
        }
        catch (CannotReadException | IOException | TagException | InvalidAudioFrameException | ReadOnlyFileException e) {
            e.printStackTrace();
            return "";
        }
        return lyric;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(id);
        parcel.writeString(title);
        parcel.writeString(artist);
        parcel.writeLong(duration);
        parcel.writeString(album);
        parcel.writeString(albumArt);
        parcel.writeString(genre);
        parcel.writeString(lyrics);
    }

    public Song(Parcel parcel) {
        id = parcel.readLong();
        title = parcel.readString();
        artist = parcel.readString();
        duration = parcel.readLong();
        album = parcel.readString();
        albumArt = parcel.readString();
        genre = parcel.readString();
        lyrics = parcel.readString();
    }

    public static final Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {
        public Song createFromParcel(Parcel parcel) {
            return new Song(parcel);
        }

        public Song[] newArray(int size) {
            return new Song[size];
        }
    };
}
