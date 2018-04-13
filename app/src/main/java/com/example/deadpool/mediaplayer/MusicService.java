package com.example.deadpool.mediaplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Random;
import android.app.Notification;
import android.app.PendingIntent;

import android.content.ContentUris;
import android.media.AudioManager;
import android.net.Uri;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;

import static com.example.deadpool.mediaplayer.MainActivity.*;
import static com.example.deadpool.mediaplayer.PlaySongActivity.*;

public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    // Media player
    private static MediaPlayer player;
    // Song list
    protected ArrayList<Song> songs;
    // current position
    static int currSong;
    int currPlayPosition = 0;
    private boolean shuffleOn = false;
    private boolean repeatOn = false;
    private Random rand;
    private String songTitle = "";
    private String songArtist = "";
    private Bitmap albumArt;
    private long songDuration;
    private static final String CHANNEL_ID = "MusicNotifChannel";
    NotificationManager nManager;
    NotificationCompat.Builder nBuilder;
    RemoteViews nRemoteView;
    RemoteViews nRemoteViewExpanded;
    final int nCode = 101;
    private static final String ButtonPrev = "prevOnClickTag";
    private static final String ButtonPlay = "playOnClickTag";
    private static final String ButtonNext = "nextOnClickTag";
    private static final String ButtonClose = "closeOnClickTag";
    private ButtonActionReceiver bReceiver = new ButtonActionReceiver();

    private final IBinder musicBind = new MusicBinder();

    private AudioManager audioManager;

    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pausePlayer();
        }
    };

    //Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    @Override
    public void onCreate(){
        // create the service
        super.onCreate();
        // initialize position
        currSong = 0;
        // create player
        player = new MediaPlayer();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        initMusicPlayer();
        rand = new Random();

        // Creeate notification UI
        nRemoteView = new RemoteViews(getPackageName(), R.layout.notif_layout);
        nRemoteView.setImageViewResource(R.id.imb_prev, R.drawable.back);
        nRemoteView.setImageViewResource(R.id.imb_play, R.drawable.play_button);
        nRemoteView.setImageViewResource(R.id.imb_next, R.drawable.next);
        nRemoteView.setImageViewResource(R.id.btn_close, R.drawable.close);
        nRemoteViewExpanded = new RemoteViews(getPackageName(), R.layout.expanded_notif_layout);
        nRemoteViewExpanded.setImageViewResource(R.id.imb_prev, R.drawable.back);
        nRemoteViewExpanded.setImageViewResource(R.id.imb_play, R.drawable.play_button);
        nRemoteViewExpanded.setImageViewResource(R.id.imb_next, R.drawable.next);
        nRemoteViewExpanded.setImageViewResource(R.id.btn_close, R.drawable.close);

        // Add click listeners to buttons of notification
        nRemoteView.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(this, ButtonClose));
        nRemoteView.setOnClickPendingIntent(R.id.imb_prev, getPendingSelfIntent(this, ButtonPrev));
        nRemoteView.setOnClickPendingIntent(R.id.imb_next, getPendingSelfIntent(this, ButtonNext));
        nRemoteView.setOnClickPendingIntent(R.id.imb_play, getPendingSelfIntent(this, ButtonPlay));
        nRemoteViewExpanded.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(this, ButtonClose));
        nRemoteViewExpanded.setOnClickPendingIntent(R.id.imb_prev, getPendingSelfIntent(this, ButtonPrev));
        nRemoteViewExpanded.setOnClickPendingIntent(R.id.imb_next, getPendingSelfIntent(this, ButtonNext));
        nRemoteViewExpanded.setOnClickPendingIntent(R.id.imb_play, getPendingSelfIntent(this, ButtonPlay));

        nManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system
            nManager.createNotificationChannel(channel);
        }
        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call, resume on hangup.
        callStateListener();
        registerButtonActionReceiver();
        registerBecomingNoisyReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Request audio focus
        if (requestAudioFocus() == false) {
            //Could not gain focus
            stopSelf();
        }
        // Let it continue running until it is stopped.
        Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();
        return START_STICKY;
    }

    public void initMusicPlayer(){
        // set player properties
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    public void setList(ArrayList<Song> theSongs){
        songs = theSongs;
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        //Invoked when the audio focus of the system is updated.
        Log.d("INFO", "System audio focus changed");
        boolean autoPaused = false;
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                Log.d("INFO", "Gain audio focus");
                if (player == null) {
                    player = new MediaPlayer();
                    initMusicPlayer();
                } else if (!isPlaying() && canAutoResume) go();
                player.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                Log.d("INFO", "Loss audio focus");
                if (isPlaying()) {
                    pausePlayer();
                    canAutoResume = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback is likely to resume
                Log.d("INFO", "Loss audio focus for a while");
                if (isPlaying()) {
                    pausePlayer();
                    canAutoResume = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                Log.d("INFO", "Loss audio focus for a while but still can play music");
                if (player.isPlaying()) player.setVolume(0.1f, 0.1f);
                break;
        }
    }

    private boolean requestAudioFocus() {
        Log.d("INFO", "Requesting audio focus...");
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            Log.d("INFO", "Audio focus granted");
            return true;
        }
        //Could not gain focus
        Log.d("INFO", "Audio focus refused");
        return false;
    }

    private boolean removeAudioFocus() {
        Log.d("INFO", "Removing audio focus");
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    private void registerButtonActionReceiver() {
        // Set a BroadcastReceiver to handle all button clicked events
        IntentFilter filter = new IntentFilter();
        filter.addAction(ButtonClose);
        filter.addAction(ButtonPrev);
        filter.addAction(ButtonPlay);
        filter.addAction(ButtonNext);
        registerReceiver(bReceiver, filter);
    }

    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (!repeatOn) {
            goToNext();
        }
        currPlayPosition = 0;
        lvSongs.smoothScrollToPosition(currSong);
        playSong();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        mediaPlayer.reset();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        //start playback
        mediaPlayer.start();

        Intent notIntent = new Intent(this, MainActivity.class);
        notIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0, notIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        nBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setTicker(songTitle)
                .setOngoing(true)
                .setContentIntent(pendInt)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            nBuilder = nBuilder.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                               .setCustomContentView(nRemoteView)
                               .setCustomBigContentView(nRemoteViewExpanded);
        } else {
            // Build a simpler notification, without buttons
            nBuilder = nBuilder.setContentTitle("Playing")
                               .setContentText(songTitle + " - " + songArtist);
        }
        updateUI();
        Log.d("INFO", "Playing " + songTitle);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(player != null) {
            if(player.isPlaying())
                player.stop();
            player.reset();
            player.release();
            player = null;
        }
        Log.d("INFO","onUnbind");
        return false;
    }

    @Override
    public void onDestroy() {
        Log.d("INFO", "go to onDestroy() of MusicService");
        nManager.cancel(nCode);
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        try {
            unregisterReceiver(bReceiver);
            unregisterReceiver(becomingNoisyReceiver);
        }
        catch (IllegalArgumentException e) {
            Log.d("INFO", "Receivers not registered or already unregistered !");
        }
        removeAudioFocus();
        super.onDestroy();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d("INFO", "go to onTaskRemoved() of MusicService");
        nManager.cancelAll();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        try {
            unregisterReceiver(bReceiver);
            unregisterReceiver(becomingNoisyReceiver);
        }
        catch (IllegalArgumentException e) {
            Log.d("INFO", "Receivers not registered or already unregistered !");
        }
        removeAudioFocus();
        super.onTaskRemoved(rootIntent);
        Toast.makeText(this, "Service removed by user", Toast.LENGTH_LONG).show();
    }

    public void playSong() {
        // play a song
        player.reset();
        //get song
        Song playSong = songs.get(currSong);
        //get id
        long currSong = playSong.getID();
        //set uri
        Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currSong);
        Log.d("INFO", trackUri.getPath());
        try{
            player.setDataSource(getApplicationContext(), trackUri);
        }
        catch(Exception e){
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
        player.prepareAsync();
    }

    public void setSong(int songIndex){
        currSong = songIndex;
        currPlayPosition = 0;
        updateUI();
    }
    
    public int getCurrSong() {
        return currSong;
    }

    public int getCurrentPlayPosition(){
        return player.getCurrentPosition();
    }

    public int getDuration(){
        return player.getDuration();
    }

    public static boolean isPlaying(){
        return player.isPlaying();
    }

    public void pausePlayer(){
        player.pause();
        currPlayPosition = getCurrentPlayPosition();
        Log.d("INFO", "Paused " + songTitle);
        updateUI();
}

    public void seekTo(int position){
        player.seekTo(position);
    }

    public void go(){
        seekTo(currPlayPosition);
        player.start();
        Log.d("INFO", "Continue playing " + songTitle);
        updateUI();
    }

    public void goToPrev(){
        if (shuffleOn) {
            // shuffle is on - go to a random song
            int newSong = currSong;
            while (newSong == currSong){
                newSong = rand.nextInt(songs.size());
            }
            setSong(newSong);
        } else {
            // no repeat or shuffle ON - go to previous song
            currSong --;
            if (currSong < 0) currSong = songs.size() - 1;
        }
        currPlayPosition = 0;
        seekTo(currPlayPosition);
        updateUI();
    }

    //skip to next
    public void goToNext(){
        if (shuffleOn) {
            // shuffle is on - go to a random song
            int newSong = currSong;
            while (newSong == currSong){
                newSong = rand.nextInt(songs.size());
            }
            setSong(newSong);
        } else {
            // no repeat or shuffle ON - play next song
            currSong ++;
            if (currSong >= songs.size()) currSong = 0;
        }
        currPlayPosition = 0;
        seekTo(currPlayPosition);
        updateUI();
    }

    public void switchShuffle(){
        shuffleOn ^= true;
    }

    public void switchRepeat(){
        repeatOn ^= true;
    }

    public boolean getShuffleOn() {
        return shuffleOn;
    }

    public boolean getRepeatOn() {
        return repeatOn;
    }

    public void updateUI() {
        ((SongAdapter)lvSongs.getAdapter()).notifyDataSetChanged();
        Song s;
        try {
            s = songs.get(currSong);
            songTitle = s.getTitle();
            songArtist = s.getArtist();
            songDuration = s.getDuration();
            albumArt = (s.albumArt != null && !s.albumArt.equals("")) ? BitmapFactory.decodeFile(s.albumArt) : BitmapFactory.decodeResource(getResources(), R.drawable.default_art);
            // Update the notification widgets
            nRemoteView.setTextViewText(R.id.tv_title, songTitle + " - " + songArtist);
            nRemoteView.setImageViewBitmap(R.id.iv_albumArt, albumArt);
            nRemoteViewExpanded.setTextViewText(R.id.tv_title, songTitle + " - " + songArtist);
            nRemoteViewExpanded.setImageViewBitmap(R.id.iv_albumArt, albumArt);
            // Update the media playback
            currentSongName.setText(songTitle);
            currentSinger.setText(songArtist);
            // Update UI for the PlaySongActivity
            currTitle.setText(songTitle);
            currArtist.setText(songArtist);
            if (s.albumArt != null && !s.albumArt.equals(""))
                upLayout.setBackground(new BitmapDrawable(getResources(), BitmapFactory.decodeFile(s.albumArt)));
            currDuration.setText(millisToString(songDuration));
            progressBar.setMax((int) songDuration / 1000);
            updateProgressBar();
        }
        catch (IndexOutOfBoundsException e) {
            Toast.makeText(this, "Should not control media playback on searching !", Toast.LENGTH_SHORT).show();
            Log.d("INFO", "IndexOutOfBoundsException due to control at search moment");
        }
        catch (NullPointerException e) {
            Log.d("INFO", "Null pointer on Song s !");
        }
        finally {
            if (isPlaying()) {
                // state playing
                nRemoteView.setImageViewResource(R.id.imb_play, R.drawable.pause);
                nRemoteViewExpanded.setImageViewResource(R.id.imb_play, R.drawable.pause);
                if (btnPlay != null) btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                if (btnPlay2 != null) btnPlay2.setImageResource(R.drawable.pause);
                if (nBuilder != null) {
                    nBuilder.setSmallIcon(android.R.drawable.ic_media_play);
                    nManager.notify(nCode, nBuilder.build());
                }
            } else {
                // state paused
                nRemoteView.setImageViewResource(R.id.imb_play, R.drawable.play_button);
                nRemoteViewExpanded.setImageViewResource(R.id.imb_play, R.drawable.play_button);
                if (btnPlay != null) btnPlay.setImageResource(android.R.drawable.ic_media_play);
                if (btnPlay2 != null) btnPlay2.setImageResource(R.drawable.play_button);
                if (nBuilder != null) {
                    nBuilder.setSmallIcon(android.R.drawable.ic_media_pause);
                    nManager.notify(nCode, nBuilder.build());
                }
            }
            Log.d("INFO", "UI updated");
        }
    }

    protected PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    // Handle incoming phone calls
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (player != null) {
                            pausePlayer();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (player != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                go();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    class ButtonActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ButtonClose.equals(intent.getAction())) {
                LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(MusicService.this);
                localBroadcastManager.sendBroadcast(new Intent("action.close"));
                Toast.makeText(context, "MusicPlayer stopped", Toast.LENGTH_SHORT).show();
            }
            else if (ButtonPrev.equals(intent.getAction())) {
                goToPrev();
                int curSong = getCurrSong();
                lvSongs.smoothScrollToPosition(curSong);

                if (isPlaying()) {
                    playSong();
                }
            }
            else if (ButtonPlay.equals(intent.getAction())) {
                if (isPlaying()) { // playing -> paused
                    pausePlayer();
                    canAutoResume = false;
                }
                else { // paused -> playing
                    if (currPlayPosition > 0)
                        go(); // resume
                    else {
                        playSong();
                    }
                }
            }
            else if (ButtonNext.equals(intent.getAction())) {
                goToNext();
                int curSong = getCurrSong();
                lvSongs.smoothScrollToPosition(curSong);
                if (isPlaying()) {
                    playSong();
                }
            }
            else {
                Toast.makeText(context, "Undefined action !", Toast.LENGTH_SHORT).show();
            }
        }
    }
}