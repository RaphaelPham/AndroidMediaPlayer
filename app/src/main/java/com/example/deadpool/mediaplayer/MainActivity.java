package com.example.deadpool.mediaplayer;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.MediaMetadataRetriever;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.AdapterView;

import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

import android.net.Uri;
import android.content.ContentResolver;
import android.database.Cursor;
import android.widget.Toast;

import static com.example.deadpool.mediaplayer.MusicService.*;

public class MainActivity extends AppCompatActivity {

    static ArrayList<Song> songList;
    static SongAdapter songAdapter;
    private ListView lvSongs;
    static TextView currentSongName;
    static TextView currentSinger;
    private ImageButton btnPrev;
    static ImageButton btnPlay;
    private ImageButton btnNext;
    private SearchView songSearchView;
    static MusicService musicSrv;
    private Intent playIntent; // Intent de goi MusicService
    private boolean musicBound = false;
    static boolean itemClicked = false;
    static boolean canAutoResume = false;
    private int selectedSong;
    private int checkedItem = 0;
    private boolean setTimeOn = false;
    static Menu mOptionsMenu;
    private ArrayList<Song> toPlay;
    LocalBroadcastManager mLocalBroadcastManager;
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(("action.close").equals(intent.getAction())){
                Log.d("INFO", "Music service send intent to kill MainActivity");
                finishAffinity();
            }
        }
    };
    public static final AlphaAnimation buttonClick = new AlphaAnimation(1F, 0.8F);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("action.close");
        mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, mIntentFilter);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null)
            songList = bundle.getParcelableArrayList("SCANNED_SONGS");

        lvSongs = (ListView) findViewById(R.id.lv_songs);
        songAdapter = new SongAdapter(this, songList);
        lvSongs.setAdapter(songAdapter);
        registerForContextMenu(lvSongs);
        lvSongs.setTextFilterEnabled(true);

        currentSongName = (TextView) findViewById(R.id.tv_current_song);
        currentSongName.setText(songAdapter.getItem(0).getTitle());
        currentSongName.setSelected(true);
        currentSinger = (TextView) findViewById(R.id.tv_current_singer);
        currentSinger.setText(songAdapter.getItem(0).getArtist());

        btnPrev = (ImageButton) findViewById(R.id.btn_prev1);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.startAnimation(buttonClick);
                musicSrv.goToPrev();
                lvSongs.smoothScrollToPosition(currSong);
                if (isPlaying())
                    musicSrv.playSong();
            }
        });

        btnPlay = (ImageButton) findViewById(R.id.btn_play1);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.startAnimation(buttonClick);
                if (isPlaying()) { // playing -> paused
                    musicSrv.pausePlayer();
                    canAutoResume = false; // the service won't be able to auto-resume on audio focus changed
                }
                else { // paused -> playing
                    Log.d("INFO", "currentPlayPosition = " + musicSrv.currPlayPosition);
                    if (musicSrv.currPlayPosition > 0) {
                        musicSrv.go(); // resume
                    }
                    else {
                        musicSrv.playSong();
                    }
                }
            }
        });

        btnNext = (ImageButton) findViewById(R.id.btn_next1);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.startAnimation(buttonClick);
                musicSrv.goToNext();
                lvSongs.smoothScrollToPosition(currSong);
                if (isPlaying())
                    musicSrv.playSong();
            }
        });

        // TEST Activity change
        currentSongName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPlaySongActivity();
            }
        });
        currentSinger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPlaySongActivity();
            }
        });

        lvSongs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                itemClicked = true;
                musicSrv.setSong(i);
                musicSrv.playSong();
                startPlaySongActivity();
            }
        });
        lvSongs.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (toPlay != null && toPlay.size() > 0)
                    selectedSong = findRealPosBySongId(toPlay.get(i).getID());
                else
                    selectedSong = i;
                return false;
            }
        });
    }

    // connect to the service to play music
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder)service;
            // get service
            musicSrv = binder.getService();
            // pass list
            musicSrv.setList(songList);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        songAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(playIntent == null){
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.custom_menu_bar, menu);
        mOptionsMenu = menu;
        songSearchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        // Configure the search info and add any event listeners
        songSearchView.setIconifiedByDefault(false);
        songSearchView.setQueryHint(getResources().getString(R.string.search_view_hint));
        songSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Toast.makeText(MainActivity.this, "Search " + query, Toast.LENGTH_SHORT).show();
                songAdapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d("INFO", "Searching: " + newText);
                songAdapter.getFilter().filter(newText);
                return true;
            }
        });
        MenuItem shuffle = menu.findItem(R.id.action_shuffle);
        if (musicSrv.getShuffleOn()) shuffle.setIcon(R.drawable.shuffle_selected);
        else shuffle.setIcon(R.drawable.shuffle_unselected);

        MenuItem repeat = menu.findItem(R.id.action_repeat);
        if (musicSrv.getRepeatOn()) repeat.setIcon(R.drawable.rep_selected);
        else repeat.setIcon(R.drawable.rep);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.lv_songs) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle(songList.get(info.position).getTitle());
            getMenuInflater().inflate(R.menu.listview_item_menu, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_shuffle:
                if (musicSrv.getShuffleOn()) {
                    item.setIcon(R.drawable.shuffle_unselected);
                    Toast.makeText(getApplicationContext(), "Shuffle is OFF", Toast.LENGTH_SHORT).show();
                }
                else {
                    item.setIcon(R.drawable.shuffle_selected);
                    Toast.makeText(getApplicationContext(), "Shuffle is ON", Toast.LENGTH_SHORT).show();
                }
                musicSrv.switchShuffle();
                return true;

            case R.id.action_repeat:
                if (musicSrv.getRepeatOn()) {
                    item.setIcon(R.drawable.rep);
                    Toast.makeText(getApplicationContext(), "Repeat is OFF", Toast.LENGTH_SHORT).show();
                }
                else {
                    item.setIcon(R.drawable.rep_selected);
                    Toast.makeText(getApplicationContext(), "Repeat is ON", Toast.LENGTH_SHORT).show();
                }
                musicSrv.switchRepeat();
                return true;

            case R.id.action_addToPlay: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.choose_to_play);

                String[] songs = getSongTitlesArray(songList);
                toPlay = new ArrayList<>();
                builder.setMultiChoiceItems(songs, null, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        // user checked or unchecked a box
                        if (isChecked) {
                            toPlay.add(songList.get(which));
                        }
                        else {
                            toPlay.remove(songList.get(which));
                        }
                    }
                });

                builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // user clicked OK
                        if (toPlay.size() >= 1) {
                            musicSrv.setList(toPlay); // set a restricted list of songs to music service
                            songAdapter = new SongAdapter(MainActivity.this, toPlay);
                            lvSongs.setAdapter(songAdapter);
                            // start the first song of playlist
                            musicSrv.setSong(0);
                            musicSrv.playSong();
                            if (mOptionsMenu != null) {
                                mOptionsMenu.findItem(R.id.action_removePlaylist).setVisible(true);
                            }
                            Toast.makeText(MainActivity.this, "Playlist of " + toPlay.size() + " songs set successfully !", Toast.LENGTH_SHORT).show();
                        }
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(R.string.Cancel, null);
                builder.create().show();
                return true;
            }

            case R.id.action_delete: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.choose_to_delete);

                String[] songs = getSongTitlesArray(songList);
                final ArrayList<Integer> toDelete = new ArrayList<>();
                builder.setMultiChoiceItems(songs, null, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        // user checked or unchecked a box
                        if (isChecked) {
                            toDelete.add(which);
                        }
                        else {
                            toDelete.remove(which);
                        }
                    }
                });

                builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (toDelete.size() >= 1) {
                            final AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                            b.setMessage(getResources().getText(R.string.Delete_request) + " " + toDelete.size() + " files ?");
                            b.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    for (Integer target : toDelete) {
                                        deleteMusic(target);
                                    }
                                    dialog.cancel();
                                }
                            });
                            b.setNegativeButton(R.string.Cancel, null);
                            b.create().show();
                        }
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(R.string.Cancel, null);
                builder.create().show();
                return true;
            }

            case R.id.action_setTime: {
                // setup the alert builder
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.setTime);
                // add a radio button list
                final String[] options = {getResources().getString(R.string.turn_off),
                                    getResources().getString(R.string.after5min),
                                    getResources().getString(R.string.after10min),
                                    getResources().getString(R.string.after20min),
                                    getResources().getString(R.string.after30min),
                                    getResources().getString(R.string.after1hour),
                                    getResources().getString(R.string.after2hours)};
                builder.setSingleChoiceItems(options, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // user checked an item
                        checkedItem = which; // luu lai option da pick
                    }
                });
                // add OK and Cancel buttons
                builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (checkedItem) {
                            case 0: // turn off
                                setTimeOn = false;
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_tat, Toast.LENGTH_SHORT).show();
                                break;
                            case 1: // after 5 min
                            {
                                setTimeOn = true;
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        if (setTimeOn) MainActivity.this.finish();
                                    }
                                }, 300000);
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_5p, Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 2: // after 10 min
                            {
                                setTimeOn = true;
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        if (setTimeOn) MainActivity.this.finish();
                                    }
                                }, 600000);
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_10p, Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 3: // after 20 min
                            {
                                setTimeOn = true;
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        if (setTimeOn) MainActivity.this.finish();
                                    }
                                }, 1200000);
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_20p, Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 4: // after 30 min
                            {
                                setTimeOn = true;
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        if (setTimeOn) MainActivity.this.finish();
                                    }
                                }, 1800000);
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_30p, Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 5: // after 1 hour
                            {
                                setTimeOn = true;
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        if (setTimeOn) MainActivity.this.finish();
                                    }
                                }, 3600000);
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_1h, Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 6: // after 2 hours
                            {
                                setTimeOn = true;
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    public void run() {
                                        if (setTimeOn) MainActivity.this.finish();
                                    }
                                }, 7200000);
                                Toast.makeText(MainActivity.this, R.string.tb_hen_gio_2p, Toast.LENGTH_SHORT).show();
                                break;
                            }
                            default:
                                break;
                        }
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(R.string.Cancel, null);
                AlertDialog dialog = builder.create();
                dialog.show();
                return true;
            }

            case R.id.action_info:
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.app_name).setMessage(R.string.app_description);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                builder.create().show();
                return true;

            case R.id.action_removePlaylist:
                musicSrv.setList(songList);
                songAdapter = new SongAdapter(this, songList);
                lvSongs.setAdapter(songAdapter);
                toPlay = null;
                musicSrv.pausePlayer();
                musicSrv.setSong(0);
                if (mOptionsMenu != null) {
                    mOptionsMenu.findItem(R.id.action_removePlaylist).setVisible(false);
                }
                Toast.makeText(MainActivity.this, "Back to the main list", Toast.LENGTH_SHORT).show();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mnuPhat:
                Toast.makeText(this, "Play " + songList.get(selectedSong).getTitle(), Toast.LENGTH_SHORT).show();
                musicSrv.setSong(selectedSong);
                musicSrv.playSong();
                startPlaySongActivity();
                break;
            case R.id.mnuXoa: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getResources().getText(R.string.Delete_request) + " " + songList.get(selectedSong).getTitle() + " ?");
                // Add the buttons
                builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                        deleteMusic(selectedSong);
                        dialog.cancel();
                    }
                });
                builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                        dialog.cancel();
                    }
                });
                builder.create().show();
                break;
            }
            case R.id.mnuChiaSe: {
                long selectedId = songList.get(selectedSong).getID();
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, trackUri);
                shareIntent.setType("audio/*");
                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share_with)));
                break;
            }
            case R.id.mnuDatNhacChuong:
                long selectedId = songList.get(selectedSong).getID();
                Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(this)) {
                        // change setting here
                        Uri ringtoneUri = Uri.parse("content://media" + trackUri.getPath());
                        setRingtone(ringtoneUri);
                        Toast.makeText(this, "Ringtone set successfully !", Toast.LENGTH_SHORT).show();
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
                title.setText(songList.get(selectedSong).getTitle());
                artist.setText(songList.get(selectedSong).getArtist());
                album.setText(songList.get(selectedSong).getAlbum());
                genre.setText(songList.get(selectedSong).getGenre());
                duration.setText(millisToString(songList.get(selectedSong).getDuration()));
                location.setText(getRealPathFromURI(this, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songList.get(selectedSong).getID())));
                dialog.show();
                break;
            }
            default:
                Toast.makeText(this, "Undefined action !", Toast.LENGTH_SHORT).show();
                break;
        }
        return true;
    }

    public static String millisToString(long millis) {
        long second = (millis / 1000) % 60;
        long minute = (millis / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minute, second);
    }

    @Override
    protected void onDestroy() {
        Log.d("INFO", "go to onDestroy() of MainActivity");
        stopService(playIntent);
        unbindService(musicConnection);
        musicSrv = null;
        Toast.makeText(this, "Service unbound !", Toast.LENGTH_LONG).show();
        mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        PlaySongActivity.mHandler.removeCallbacks(PlaySongActivity.updateTimeTask);
        super.onDestroy();
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

    private int findRealPosBySongId(long id) {
        Log.d("INFO", "on FindRealPosBySongId");
        for (Song s : songList) {
            if (s.getID() == id) return songList.indexOf(s);
        }
        return -1;
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
        long selectedId = songList.get(target).getID();
        if (selectedId == songList.get(musicSrv.getCurrSong()).getID()) {
            musicSrv.pausePlayer();
            musicSrv.goToNext();
        }
        Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selectedId);
        File toDelete = new File(getRealPathFromURI(MainActivity.this, trackUri));
        if (toDelete.exists()) {
            boolean result = toDelete.delete(); // delete file in the files system
            getContentResolver().delete(trackUri, null, null); // remove it from the MediaStore database
            Log.d("INFO", "File deleted successfully ? " + result);
            if (result) {
                songList.remove(target);
                songAdapter.notifyDataSetChanged();
                Toast.makeText(this, "File removed successfully !", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(this, "Error on deleting file !", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String[] getSongTitlesArray(ArrayList<Song> l) {
        String[] array = new String[l.size()];
        for (int i=0; i<array.length; i++) {
            array[i] = l.get(i).getTitle();
        }
        return array;
    }

    private void startPlaySongActivity() {
        Intent intent = new Intent(MainActivity.this, PlaySongActivity.class);
        startActivity(intent);
    }
}