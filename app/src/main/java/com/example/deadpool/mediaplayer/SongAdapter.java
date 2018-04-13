package com.example.deadpool.mediaplayer;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.BaseAdapter;
import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SongAdapter extends BaseAdapter implements Filterable {

    private ArrayList<Song> songs;
    private LayoutInflater inflater;
    private ArrayList<Song> dataBackup;
    static final RotateAnimation anim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

    public SongAdapter(Context context, ArrayList<Song> theSongs){
        songs = theSongs;
        inflater = LayoutInflater.from(context);
        dataBackup = theSongs;
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(Animation.INFINITE);
        anim.setDuration(8000);
    }

    @Override
    public int getCount() {
        return songs == null ? 0 : songs.size();
    }

    @Override
    public Song getItem(int i) {
        return songs.get(i);
    }

    @Override
    public long getItemId(int i) {
        return songs.get(i).getID();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if(convertView == null){
            viewHolder = new ViewHolder();
            convertView = inflater.inflate(R.layout.song_item_layout, parent, false);
            viewHolder.imv = (ImageView) convertView.findViewById(R.id.iv_song);
            viewHolder.tvTitle = (TextView) convertView.findViewById(R.id.tv_title);
            viewHolder.tvArtist = (TextView) convertView.findViewById(R.id.tv_artist);
            viewHolder.tvDuration = (TextView) convertView.findViewById(R.id.tv_duration);
            convertView.setTag(viewHolder);
        }else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        Song currSong = getItem(position);
        long playingSongId = dataBackup.get(MusicService.currSong).getID();
        if (MusicService.isPlaying() && currSong.getID() == playingSongId) {
            viewHolder.imv.startAnimation(anim);
        }
        else {
            viewHolder.imv.setAnimation(null);
        }
        viewHolder.tvTitle.setText(currSong.getTitle());
        viewHolder.tvArtist.setText(currSong.getArtist());
        viewHolder.tvDuration.setText(MainActivity.millisToString(currSong.getDuration()));
        return convertView;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            ArrayList<Song> newdata;
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                FilterResults fr = new FilterResults();
                if (charSequence == null || charSequence.length() == 0) {
                    fr.values = dataBackup;
                    fr.count = dataBackup.size();
                    if (MainActivity.itemClicked) {
                        MusicService.currSong = findRealPosBySongId(songs.get(MusicService.currSong).getID());
                        MainActivity.itemClicked = false;
                    }
                }
                else {
                    newdata = new ArrayList<>();
                    for (Song c : dataBackup) {
                        if (c.getTitle().toLowerCase().contains(charSequence.toString().toLowerCase()) ||
                                c.getArtist().toLowerCase().contains(charSequence.toString().toLowerCase()))
                            newdata.add(c);
                    }
                    fr.values = newdata;
                    fr.count = newdata.size();
                }
                return fr;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                songs = (ArrayList<Song>) filterResults.values;
                Log.d("INFO", "DataBackup null: "+(dataBackup == null));
                Log.d("INFO", "songs count = " + songs.size());
                notifyDataSetChanged();
                MainActivity.musicSrv.setList(songs);
            }
        };
    }

    private int findRealPosBySongId(long id) {
        Log.d("INFO", "on FindRealPosBySongId");
        for (Song s : dataBackup) {
            if (s.getID() == id) return dataBackup.indexOf(s);
        }
        return -1;
    }

    public class ViewHolder{
        ImageView imv;
        TextView tvTitle;
        TextView tvArtist;
        TextView tvDuration;
    }
}
