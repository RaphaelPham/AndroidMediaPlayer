package com.example.deadpool.mediaplayer;

import android.os.Parcel;
import android.os.Parcelable;

public class Song implements Parcelable {
    private long id;
    private String title;
    private String artist;
    private long duration;
    private String album;
    public String albumArt;
    private String genre;

    public Song(long songID, String songTitle, String songArtist, long songDuration, String album, String genre) {
        id = songID;
        title = songTitle;
        artist = songArtist;
        duration = songDuration;
        this.album = album;
        this.genre = genre;
    }

    public long getID() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public long getDuration() { return duration; }
    public String getAlbum() { return album; }
    public String getGenre() { return genre; }

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
    }

    public Song(Parcel parcel) {
        id = parcel.readLong();
        title = parcel.readString();
        artist = parcel.readString();
        duration = parcel.readLong();
        album = parcel.readString();
        albumArt = parcel.readString();
        genre = parcel.readString();
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
