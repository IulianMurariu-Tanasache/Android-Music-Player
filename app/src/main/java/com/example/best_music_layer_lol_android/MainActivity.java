package com.example.best_music_layer_lol_android;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    //to do:
    //ceva = controls, careva aduce fragmentul de controale / playlisturi, unde sunt controalele(puse pe un fragment) va fi si fragmentul de playlisturi
    //playlist adapter similar cu cel de butoane
    //serialize + deseriale: adauga playlist + varianta de PC & server

    public class State
    {
        final Client client = new Client();
        String path;
        String currentsong = "";
        int index = -1;
        int previndex = -1;
        float cTime = 0;
        float songlength = -1;
        boolean paused = false;
        int repeat = 0;
        boolean shuffle = false;
        int playlistindex = -1;
        int prevplaylist = -1;
        List<String> songs;
        boolean toadd = false;
        SeekBar time;
        SeekBar volume;
        TextView current;
        View prevSong  = null;
        List<playlist> playlists;
        boolean allsongs = true;

        public String serialize()
        {
            String data = "currentSong: " + state.currentsong + "\nindex: " + state.index + "\nprevIndex: " + state.previndex + "\ncTime: " + state.cTime + "\nsongLength: " + state.songlength + "\npaused: " + state.paused + "\nrepeat: " + state.repeat + "\nshuffle: " + state.shuffle + "\nplaylistIndex: " + state.playlistindex;
            return data;
        }

        public void update() {
            try {
                mp.stop();
                mp.reset();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                time.setProgress((int)((cTime * 100) / songlength));
                Log.d("Data",path + currentsong);
                if(mp.isPlaying() == false)
                    mp.setDataSource(path + currentsong);
                mp.prepare();
                mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mp.start();
                        mp.seekTo((int)(state.cTime * 1000));
                    }
                });
                final Button pause = findViewById(R.id.pausebutton);
                if (paused && mp.isPlaying()) {
                    mp.pause();
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            pause.setBackground(getResources().getDrawable(R.drawable.play));
                        }
                    });

                }
                else
                {
                    //mp.start();
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            pause.setBackground(getResources().getDrawable(R.drawable.pause));
                        }
                    });
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void deserialize(String Data) {

            String[] line = Data.split("\n",-1);
            //Log.d("Data",line);
            int j = 0;
            while(j < line.length)
            {
                int i = line[j].indexOf(" ");
                String data = line[j].substring(i+1);
                Log.d("Data",data);
                switch(j)
                {
                    case 0:
                        currentsong = data;
                        break;
                    case 1:
                        index = Integer.parseInt(data);
                        break;
                    case 2:
                        previndex = Integer.parseInt(data);
                        break;
                    case 3:
                        cTime = Float.parseFloat(data);
                        break;
                    case 4:
                        songlength = Float.parseFloat(data);
                        break;
                    case 5:
                        paused = Boolean.parseBoolean(data);
                        break;
                    case 6:
                        repeat = Integer.parseInt(data);
                        break;
                    case 7:
                        shuffle = Boolean.parseBoolean(data);
                        break;
                    case 8:
                        playlistindex = Integer.parseInt(data);
                        break;
                }
                j++;
            }
            update();
        }
    }

    private class Client {
        final private int header = 32;
        final private String FORMAT = "UTF8";
        final private int PORT = 5050;
        private Socket socket = null;
        private DataInputStream input = null;
        private DataOutputStream output = null;
        //  SERVER = socket.gethostbyname(socket.gethostname());
        //   ADDR = (SERVER, PORT);
        final private String disconnect = "out!";
        private boolean offline = true;

        public void connect() {
            try {
                socket = new Socket("192.168.0.103", PORT);
                Log.d("Serer", "connected!");
                output = new DataOutputStream(socket.getOutputStream());
                input = new DataInputStream(socket.getInputStream());
                offline = false;
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Serer", "offline!");
            }
        }

        public void send(String message) {
            try {
                byte[] arr = message.getBytes(FORMAT);
                output.write(arr);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void send_state()
        {
            if(offline == true)
                return;
            new Thread() {
                public void run() {
                        String msg = state.serialize();
                        send_protocol(msg);
                    }
            }.start();
        }

        public String read() {
            byte[] len = new byte[header];
            String message = null;
            try {
                input.read(len);
                ByteBuffer b = ByteBuffer.wrap(len);
                String s = StandardCharsets.UTF_8.decode(b).toString();
                s = s.replaceAll(" ", "");
                int size = Integer.parseInt(s);
                Log.d("Server", String.valueOf(size));
                if (size > 0) {
                    byte[] msg = new byte[size];
                    input.read(msg);
                    b = ByteBuffer.wrap(msg);
                    message = StandardCharsets.UTF_8.decode(b).toString();
                    Log.d("Server", "Primit: " + message);
                    return message;
                }
            } catch (IOException e) {
                offline = true;
                e.printStackTrace();
                return "Eror";
            }
            return "nuj";
        }

        public void send_protocol(final String message) {
            Log.d("Server", "Sending: " + message);
            new Thread() {
                public void run() {
                    try {
                        byte[] arr = message.getBytes(FORMAT);
                        int l = message.length();
                        byte[] length = new byte[header];
                        String len = String.valueOf(l);
                        while (l < header) {
                            len = len + ' ';
                            l++;
                        }
                        length = len.getBytes(FORMAT);
                        output.write(length);
                        output.write(arr);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

        public void respond(ButtonAdapter ba) {
            byte[] len = new byte[header];
            String message = null;
            if (offline == true)
                return;
            while (!offline) {
                message = read();
                if (message.equals("check")) {
                    try {
                        send("here");
                    } catch (Exception e) {
                        offline = true;
                    }
                }
                else {
                    state.deserialize(message);
                    if(state.index >= 0)
                        ba.setCurrent(state.index);
                    Log.d("Server","Read data!");
                }

            }
        }
    }

    public class playlist {
        private List<String> songs;
        private  int index;
        private String name;

        public playlist()
        {
            songs = new ArrayList<>();
            index = state.playlists.size();
            name = "";
        }

        public void addSong(String name)
        {
            songs.add(name);
        }

        /*public void rename()
        {

        }*/

        public void removeSong(String n)
        {
            songs.remove(n);
        }
    };

    State state = new State();
    MediaPlayer mp = new MediaPlayer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private static final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private static final int numberOfPermissions = 1;

    //@SuppressLint("NewAPI")
    private boolean isPermitted(){
        for(int i = 0; i < numberOfPermissions; i++)
            if(checkSelfPermission(PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED)
                return false;
            return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(!isPermitted()){
            ((ActivityManager) (this.getSystemService(ACTIVITY_SERVICE))).clearApplicationUserData();
            recreate();
        }
        else{
            onResume();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                state.current = findViewById(R.id.songname);

                final Button ceva = findViewById(R.id.ceva);
                ceva.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v) {
                        new Thread() {
                            public void run() {
                                state.client.connect();
                                Log.d("Server",String.valueOf(state.client.offline));
                            }
                        }.start();
                    }
                });

                state.time = findViewById(R.id.timeBar);
                state.time.setMax(100);
                state.time.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        state.cTime = (state.songlength * progress) / 100;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        mp.seekTo((int)(state.cTime * 1000));
                    }
                });

                state.volume = findViewById(R.id.volume);
                state.volume.setMax(100);
                state.volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        float log1 = 1.0f - (float)(Math.log(100 - progress)/Math.log(100));
                        mp.setVolume(log1,log1);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });

                final Button pause = findViewById(R.id.pausebutton);
                pause.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(mp.isPlaying()){
                            mp.pause();
                            state.paused = true;
                            pause.setBackground(getResources().getDrawable(R.drawable.play));
                        }
                        else{
                            mp.start();
                            state.paused = false;
                            pause.setBackground(getResources().getDrawable(R.drawable.pause));
                        }
                    }
                });

                final Button repeat = findViewById(R.id.repeatbutton);
                repeat.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v) {
                        state.repeat  = (state.repeat + 1) % 3;
                        switch (state.repeat)
                        {
                            case 0:
                                repeat.setText("");
                                repeat.setBackground(getResources().getDrawable(R.drawable.repeat));
                                break;
                            case 1:
                                repeat.setText("1");
                                repeat.setBackground(getResources().getDrawable(R.drawable.repeatgri));
                                break;
                            case 2:
                                repeat.setText("∞");
                                //repeat.setBackground(getResources().getDrawable(R.drawable.play));
                                break;
                        }
                    }
                });

                final Button next = findViewById(R.id.nextbutton);
                next.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v) {
                        if(state.index + 1 < state.songs.size())
                            play(state.songs.get(state.index  + 1),state.index + 1);
                        else
                            play(state.songs.get(0),0);
                    }
                });

                final Button prev = findViewById(R.id.previousbutton);
                prev.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v) {
                        if(state.index - 1 >= 0)
                            play(state.songs.get(state.index  - 1),state.index - 1);
                        else
                            play(state.songs.get(state.songs.size()),state.songs.size());
                    }
                });

                final Button shuffle = findViewById(R.id.shufflebutton);
                shuffle.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v) {
                        state.shuffle = !state.shuffle;
                        if (state.shuffle) {
                            shuffle.setBackground(getResources().getDrawable(R.drawable.shuffle));
                        }
                        else
                            shuffle.setBackground(getResources().getDrawable(R.drawable.shufflegri));
                    }
                });

                final Button newplaylsit = findViewById(R.id.newbutton);
                newplaylsit.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v) {
                        state.playlists.add(new playlist());
                    }
                });

                final Button deleteplaylist = findViewById(R.id.deletebutton);
                deleteplaylist.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        state.playlists.remove(state.playlists.get(state.playlistindex));
                    }
                });

                //final Button

            }
        });

    }

    private boolean isPlayerInit = false;

    private void addMusicFilesFrom(String path) {
        final File songDir = new File(path);
        Log.d("Music", path + "\n");
        if(!songDir.exists())
                return;
        Log.d("Music","Exista director!\n");
        final File[] files = songDir.listFiles();
        state.path = path + '/';
        for(File file : files)
        {
            final String newpath = file.getName();
            if(newpath.endsWith(".mp3")) {
                state.songs.add(newpath);
            }
        }
    }

    private void updateTime()
    {
        if(mp != null && mp.isPlaying()) {
            state.cTime = mp.getCurrentPosition() / 100;
            int progress = (int) ((state.cTime * 10) / state.songlength);
            if (progress >= 100) {
                if (state.repeat > 0) {
                    play(state.songs.get(state.index), state.index);
                    if (state.repeat == 1)
                        state.repeat--;
                } else if (state.shuffle) {
                    Random rand = new Random();
                    int nrrand = rand.nextInt(state.songs.size());
                    play(state.songs.get(nrrand), nrrand);
                } else {
                    if (state.index + 1 < state.songs.size())
                        play(state.songs.get(state.index + 1), state.index + 1);
                    else
                        play(state.songs.get(0), 0);
                }
            } else
                state.time.setProgress(progress);
        }
    }

//"/storage/2AC5-4D2B/Music"
    private void fillSongList(){
        state.songs.clear();
        addMusicFilesFrom(String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) );
        //addMusicFilesFrom("/storage/2AC5-4D2B/Music");
    }

    private void play(final String path, final int i){
        try {
            if(state.paused == true)
                mp.stop();
            mp.reset();
        }
        catch(Exception e) {
            e.printStackTrace();
            Log.d("MP","exceptie la reset/stop!");
        }
        try{
            if(!mp.isPlaying())
                mp.setDataSource(MainActivity.this, Uri.fromFile(new File(state.path + path)));
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.prepareAsync();
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    state.paused = false;
                    state.previndex = state.index;
                    state.index = i;
                    state.cTime = 0;
                    state.currentsong = path;
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            state.current.setText(state.songs.get(i));
                        }
                    });

                    state.time.setProgress(0);
                    state.client.send_state();
                    state.songlength = mp.getDuration() / 1000;
                    Log.d("MP","prepared!");
                }
            });
        }
        catch(Exception e){
            Log.d("MP","exceptie la play!");
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        state.client.send_protocol(state.client.disconnect);
        Log.d("Data","Ded");
        try {
            state.client.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mp.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isPermitted()) {
            requestPermissions(PERMISSIONS, 12345);
            return;
        }

        if (!isPlayerInit) {
            new Thread() {
                public void run() {
                    state.client.connect();
                    }
            }.start();

            isPlayerInit = true;
            final ListView listViewSongs = findViewById(R.id.listViewsongs);
            final ButtonAdapter buttonAdapter = new ButtonAdapter();
            state.songs = new ArrayList<>();
            state.playlists = new ArrayList<>();
            fillSongList();
            buttonAdapter.setData(state.songs);
            listViewSongs.setAdapter(buttonAdapter);
            listViewSongs.setItemsCanFocus(true);
            new Thread() {
                public void run() {
                    while (isPlayerInit) {
                        updateTime();
                    }}
                }.start();

            if(state.client.offline == false)
            {
                new Thread() {
                public void run() {
                    while (isPlayerInit) {
                        state.client.respond(buttonAdapter);
                    }}
                }.start();
            }

            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    for(int i = 0; i  < state.songs.size(); ++i) {
                        Button b =  (Button)buttonAdapter.getView(i,null,listViewSongs);
                        if (b.getText() == state.currentsong)
                            b.setBackground(getResources().getDrawable(R.drawable.playingsong));
                        else
                            b.setBackground(getResources().getDrawable(R.drawable.song));
                    }
                        }
                    });
        }
    }

    class ButtonAdapter extends BaseAdapter {
        public List<String> data = new ArrayList<>();

        void setData(List<String> mdata){
            data.clear();
            data.addAll(mdata);
            notifyDataSetChanged();
        }

        public void setCurrent(int position) {
            ListView v = findViewById(R.id.listViewsongs);
            View b = getView(position, null, v);
            b.setBackground(getResources().getDrawable(R.drawable.playingsong));
            if(state.previndex >= 0 && state.previndex != state.index)
                state.prevSong = (View)v.getItemAtPosition(state.previndex);
            if(state.prevSong!= null)
                state.prevSong.setBackground(getResources().getDrawable(R.drawable.song));
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public String getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @SuppressLint("WrongViewCast")
        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            if(view == null) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item, viewGroup, false);
                view.setTag(new ViewHolder((Button) view.findViewById(R.id.myItem)));
            }
            ViewHolder holder = (ViewHolder) view.getTag();
            final String item = data.get(i);
            holder.info.setText(item.substring(item.lastIndexOf('/')+1));
            view.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v) {
                    final String musicPath = item;
                    play(musicPath,i);
                    state.prevSong = v;
                }
            });
            return view;
        }

        class ViewHolder {
            Button info;

            ViewHolder(Button b){
                info = b;
            }
        }
    }
}