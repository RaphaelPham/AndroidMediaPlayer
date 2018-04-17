package com.example.deadpool.mediaplayer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import es.dmoral.toasty.Toasty;

import static com.example.deadpool.mediaplayer.MainActivity.*;
import static com.example.deadpool.mediaplayer.MusicService.*;

public class PlaySongActivity extends AppCompatActivity {

    protected static TextView currTitle;
    protected static TextView currArtist;
    protected static ImageButton btnShuffle;
    protected static ImageButton btnPlay2;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnVolume;
    protected static ImageButton btnRepeat;
    protected static TextView currTime;
    protected static TextView currDuration;
    protected static LinearLayout upLayout;
    protected static SeekBar progressBar;
    protected static TextView tvLyrics;

    // Handler to update UI timer
    protected static Handler mHandler = new Handler();

    private LocalBroadcastManager mLocalBroadcastManager;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(("action.close").equals(intent.getAction())){
                Log.d("INFO", "Music service send intent to kill PlaySongActivity");
                finishAffinity();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_song);

        ActionBar actionBar = getSupportActionBar();
        try {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        catch (NullPointerException e) {
            Log.e("INFO", e.getMessage());
        }
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("action.close");
        mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, mIntentFilter);

        upLayout = findViewById(R.id.upLayout);
        upLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tvLyrics.getVisibility() == View.VISIBLE)
                    tvLyrics.setVisibility(View.INVISIBLE);
                else
                    tvLyrics.setVisibility(View.VISIBLE);
            }
        });

        currTitle = findViewById(R.id.currentSongName);
        currTitle.setSelected(true);
        currArtist = findViewById(R.id.currentSingerName);

        btnShuffle = findViewById(R.id.btn_shuffle);
        if (musicSrv.getShuffleOn()) {
            btnShuffle.setImageResource(R.drawable.shuffle_selected);
        }
        else {
            btnShuffle.setImageResource(R.drawable.shuffle);
        }
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (musicSrv.getShuffleOn()) {
                    btnShuffle.setImageResource(R.drawable.shuffle);
                    mOptionsMenu.findItem(R.id.action_shuffle).setIcon(R.drawable.shuffle_unselected);
                    Toasty.info(getApplicationContext(), "Shuffle is OFF", Toast.LENGTH_SHORT, true).show();
                }
                else {
                    btnShuffle.setImageResource(R.drawable.shuffle_selected);
                    mOptionsMenu.findItem(R.id.action_shuffle).setIcon(R.drawable.shuffle_selected);
                    Toasty.info(getApplicationContext(), "Shuffle is ON", Toast.LENGTH_SHORT, true).show();
                }
                musicSrv.switchShuffle();
            }
        });

        btnRepeat = findViewById(R.id.btn_repeat);
        if (musicSrv.getRepeatOn()) {
            btnRepeat.setImageResource(R.drawable.replay1);
        }
        else {
            btnRepeat.setImageResource(R.drawable.replay);
        }
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (musicSrv.getRepeatOn()) {
                    btnRepeat.setImageResource(R.drawable.replay);
                    mOptionsMenu.findItem(R.id.action_repeat).setIcon(R.drawable.rep);
                    Toasty.info(getApplicationContext(), "Repeat is OFF", Toast.LENGTH_SHORT, true).show();
                }
                else {
                    btnRepeat.setImageResource(R.drawable.replay1);
                    mOptionsMenu.findItem(R.id.action_repeat).setIcon(R.drawable.rep_selected);
                    Toasty.info(getApplicationContext(), "Repeat is ON", Toast.LENGTH_SHORT, true).show();
                }
                musicSrv.switchRepeat();
            }
        });

        btnVolume = findViewById(R.id.btn_volume);
        btnVolume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audio != null)
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC , AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
            }
        });

        btnPlay2 = findViewById(R.id.btn_play2);
        btnPlay2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPlaying()) { // playing -> paused
                    musicSrv.pausePlayer();
                    canAutoResume = false; // the service won't be able to auto-resume on audio focus changed
                }
                else { // paused -> playing
                    if (musicSrv.currPlayPosition > 0)
                        musicSrv.go(); // resume
                    else {
                        musicSrv.playSong();
                    }
                }
            }
        });

        btnPrev = findViewById(R.id.btn_prev2);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                musicSrv.goToPrev();
                if (isPlaying()) {
                    musicSrv.playSong();
                }
            }
        });

        btnNext = findViewById(R.id.btn_next2);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                musicSrv.goToNext();
                if (isPlaying()) {
                    musicSrv.playSong();
                }
            }
        });

        currTime = findViewById(R.id.tv_currenttime);
        currDuration = findViewById(R.id.tv_endtime);

        progressBar = findViewById(R.id.durationSeekBar);
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (musicSrv != null && fromUser) {
                    musicSrv.seekTo(progress * 1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        tvLyrics = findViewById(R.id.tvLyrics);
        tvLyrics.setMovementMethod(new ScrollingMovementMethod());
        tvLyrics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view.getVisibility() == View.VISIBLE)
                    view.setVisibility(View.INVISIBLE);
            }
        });
    }

    protected static Runnable updateTimeTask = new Runnable() {
        public void run() {
            try {
                int curPos = musicSrv.getCurrentPlayPosition() / 1000;
                progressBar.setProgress(curPos);
                currTime.setText(millisToString(musicSrv.getCurrentPlayPosition()));
            }
            catch (NullPointerException e) {
                Log.d("INFO", "musicSrv lost ... ?");
            }
            // Running this thread after each second
            mHandler.postDelayed(this, 1000);
        }
    };

    protected static void updateProgressBar() {
        mHandler.postDelayed(updateTimeTask, 1000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d("INFO", "PlaySongActivity calls updateUI()");
        musicSrv.updateUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d("INFO", "go to onDestroy of playSongActivity");
        mHandler.removeCallbacks(updateTimeTask);
        mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.listview_item_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mnuPhat:
                Toasty.info(this, "Play " + songList.get(currSong).getTitle(), Toast.LENGTH_SHORT, true).show();
                musicSrv.setSong(currSong);
                musicSrv.playSong();
                break;
            case R.id.mnuXoa: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getResources().getText(R.string.Delete_request) + " " + musicSrv.songs.get(currSong).getTitle() + " ?");
                // Add the buttons
                builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                        deleteMusic(currSong);
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(R.string.Cancel, null);
                builder.create().show();
                break;
            }
            case R.id.mnuChiaSe: {
                long selectedId = musicSrv.songs.get(currSong).getID();
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, trackUri);
                shareIntent.setType("audio/*");
                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share_with)));
                break;
            }
            case R.id.mnuDatNhacChuong:
                long selectedId = musicSrv.songs.get(currSong).getID();
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(this)) {
                        // change setting here
                        Uri ringtoneUri = Uri.parse("content://media" + trackUri.getPath());
                        setRingtone(ringtoneUri);
                        Toasty.success(this, "Ringtone set successfully !", Toast.LENGTH_SHORT, true).show();
                    } else {
                        //Migrate to Setting write permission screen.
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                }
                break;
            case R.id.mnuThongTin: {
                Dialog dialog = new Dialog(this);
                dialog.setContentView(R.layout.dialog_song_info);
                TextView title = dialog.findViewById(R.id.dlgTitle);
                TextView artist = dialog.findViewById(R.id.dlgArtist);
                TextView album = dialog.findViewById(R.id.dlgAlbum);
                TextView genre = dialog.findViewById(R.id.dlgGenre);
                TextView duration = dialog.findViewById(R.id.dlgDuration);
                TextView location = dialog.findViewById(R.id.dlgPath);
                title.setText(musicSrv.songs.get(currSong).getTitle());
                artist.setText(musicSrv.songs.get(currSong).getArtist());
                album.setText(musicSrv.songs.get(currSong).getAlbum());
                genre.setText(musicSrv.songs.get(currSong).getGenre());
                duration.setText(millisToString(musicSrv.songs.get(currSong).getDuration()));
                location.setText(getRealPathFromURI(this, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, musicSrv.songs.get(currSong).getID())));
                dialog.show();
                break;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] projection = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri, projection, null, null, null);
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

    private void setRingtone(Uri uri){
        // Insert the ring tone to the content provider
        ContentValues value = new ContentValues();
        value.put(MediaStore.Audio.Media.IS_RINGTONE, true); // add song to ringtone list
        getContentResolver().update(uri, value, null, null);
        // Set default ring tone
        RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri);
    }

    private void deleteMusic(int target) {
        long selectedId = musicSrv.songs.get(target).getID();
        if (selectedId == musicSrv.songs.get(musicSrv.getCurrSong()).getID()) {
            musicSrv.pausePlayer();
            musicSrv.goToNext();
        }
        Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId);
        File toDelete = new File(getRealPathFromURI(this, trackUri));
        if (toDelete.exists()) {
            boolean result = toDelete.delete(); // delete file in the files system
            getContentResolver().delete(trackUri, null, null); // remove it from the MediaStore database
            Log.d("INFO", "File deleted successfully ? " + result);
            if (result) {
                musicSrv.songs.remove(target);
                songAdapter.notifyDataSetChanged();
                Toasty.success(this, "File removed successfully !", Toast.LENGTH_SHORT, true).show();
            }
            else {
                Toasty.error(this, "Error on deleting file !", Toast.LENGTH_SHORT, true).show();
            }
        }
    }
}