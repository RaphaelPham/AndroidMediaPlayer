package com.example.deadpool.mediaplayer;

public class Song {
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
}
