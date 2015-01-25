package com.kabouzeid.materialmusic.ui.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.kabouzeid.materialmusic.R;
import com.kabouzeid.materialmusic.helper.PlayingQueueDialogHelper;
import com.kabouzeid.materialmusic.helper.SongDetailDialogHelper;
import com.kabouzeid.materialmusic.interfaces.OnMusicRemoteEventListener;
import com.kabouzeid.materialmusic.lastfm.artist.LastFMArtistImageLoader;
import com.kabouzeid.materialmusic.loader.SongFileLoader;
import com.kabouzeid.materialmusic.misc.AppKeys;
import com.kabouzeid.materialmusic.model.MusicRemoteEvent;
import com.kabouzeid.materialmusic.model.Song;
import com.kabouzeid.materialmusic.service.MusicService;
import com.kabouzeid.materialmusic.ui.activities.base.AbsFabActivity;
import com.kabouzeid.materialmusic.ui.activities.tageditor.SongTagEditorActivity;
import com.kabouzeid.materialmusic.util.MusicUtil;
import com.kabouzeid.materialmusic.util.Util;
import com.kabouzeid.materialmusic.util.ViewUtil;
import com.nineoldandroids.view.ViewPropertyAnimator;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.io.File;

public class MusicControllerActivity extends AbsFabActivity implements OnMusicRemoteEventListener {
    public static final String TAG = MusicControllerActivity.class.getSimpleName();

    private static final int DEFAULT_DELAY = 350;
    private static final int DEFAULT_ANIMATION_TIME = 1000;

    private Song song;
    private ImageView albumArt;
    private ImageView artistArt;
    private TextView songTitle;
    private TextView songArtist;
    private TextView currentSongProgress;
    private TextView totalSongDuration;
    private View footer;
    private SeekBar progressSlider;
    private ImageButton nextButton;
    private ImageButton prevButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;

    private int lastFooterColor = -1;

    private boolean killThreads = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setUpTranslucence(true, false);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_music_controller);

        initViews();

        moveSeekBarIntoPlace();

        updateCurrentSong();

        setUpMusicControllers();

        prepareViewsForOpenAnimation();

        setUpToolBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_title_playing, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.action_playing_queue:
                final MaterialDialog materialDialog = PlayingQueueDialogHelper.getDialog(this);
                materialDialog.show();
                return true;
            case R.id.action_tag_editor:
                Intent intent = new Intent(this, SongTagEditorActivity.class);
                intent.putExtra(AppKeys.E_ID, song.id);
                startActivity(intent);
                return true;
            case R.id.action_details:
                String songFilePath = SongFileLoader.getSongFile(this, song.id);
                File songFile = new File(songFilePath);
                SongDetailDialogHelper.getDialog(this, songFile).show();
                return true;
            case R.id.action_go_to_album:
                goToAlbumDetailsActivity(song.albumId, null);
                return true;
            case R.id.action_go_to_artist:
                goToArtistDetailsActivity(song.artistId, null);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMusicControllerStateUpdateThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        killThreads = true;
    }

    private void updateCurrentSong() {
        getCurrentSongAndQueue();
        setHeadersText();
        setUpArtistArt();
        setUpAlbumArtAndApplyPalette();
        totalSongDuration.setText(MusicUtil.getReadableDurationString(song.duration));
        currentSongProgress.setText(MusicUtil.getReadableDurationString(-1));
    }

    private void moveSeekBarIntoPlace() {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) progressSlider.getLayoutParams();
        progressSlider.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        lp.setMargins(0, 0, 0, -(progressSlider.getMeasuredHeight() / 2));
        progressSlider.setLayoutParams(lp);
    }

    private void setHeadersText() {
        songTitle.setText(song.title);
        songArtist.setText(song.artistName);
    }

    private void setUpAlbumArtAndApplyPalette() {
        ImageLoader.getInstance().displayImage(MusicUtil.getAlbumArtUri(song.albumId).toString(), albumArt, new ImageLoadingListener() {
            @Override
            public void onLoadingStarted(String imageUri, View view) {
                albumArt.setImageResource(R.drawable.default_album_art);
            }

            @Override
            public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                albumArt.setImageResource(R.drawable.default_album_art);
                setStandardColors();
            }

            @Override
            public void onLoadingComplete(String imageUri, final View view, Bitmap loadedImage) {
                applyPalette(loadedImage);
            }

            @Override
            public void onLoadingCancelled(String imageUri, View view) {
                albumArt.setImageResource(R.drawable.default_album_art);
                setStandardColors();
            }
        });
    }

    private void setUpArtistArt() {
        if (artistArt != null) {
            artistArt.setImageResource(R.drawable.default_artist_image);
            LastFMArtistImageLoader.loadArtistImage(this, song.artistName, new LastFMArtistImageLoader.ArtistImageLoaderCallback() {
                @Override
                public void onArtistImageLoaded(Bitmap artistImage) {
                    artistArt.setImageBitmap(artistImage);
                }
            });
        }
    }

    private void getCurrentSongAndQueue() {
        if (getApp().getMusicPlayerRemote().getPosition() >= 0) {
            song = getApp().getMusicPlayerRemote().getPlayingQueue().get(getApp().getMusicPlayerRemote().getPosition());
        } else {
            finish();
        }
    }

    private void initViews() {
        nextButton = (ImageButton) findViewById(R.id.next_button);
        prevButton = (ImageButton) findViewById(R.id.prev_button);
        repeatButton = (ImageButton) findViewById(R.id.repeat_button);
        shuffleButton = (ImageButton) findViewById(R.id.shuffle_button);
        albumArt = (ImageView) findViewById(R.id.album_art);
        artistArt = (ImageView) findViewById(R.id.artist_image);
        songTitle = (TextView) findViewById(R.id.song_title);
        songArtist = (TextView) findViewById(R.id.song_artist);
        currentSongProgress = (TextView) findViewById(R.id.song_current_progress);
        totalSongDuration = (TextView) findViewById(R.id.song_total_time);
        footer = findViewById(R.id.footer);
        progressSlider = (SeekBar) findViewById(R.id.progress_slider);
    }

    private void applyPalette(Bitmap bitmap) {
        Palette.generateAsync(bitmap, new Palette.PaletteAsyncListener() {
            @Override
            public void onGenerated(Palette palette) {
                Palette.Swatch swatch = palette.getVibrantSwatch();
                if (swatch != null) {
                    animateColorChange(swatch.getRgb());
                    songTitle.setTextColor(swatch.getTitleTextColor());
                    songArtist.setTextColor(swatch.getBodyTextColor());
                } else {
                    setStandardColors();
                }
            }
        });
    }

    private void setStandardColors() {
        int songTitleTextColor = Util.resolveColor(this, R.attr.title_text_color);
        int artistNameTextColor = Util.resolveColor(this, R.attr.caption_text_color);
        int colorPrimary = Util.resolveColor(MusicControllerActivity.this, R.attr.colorPrimary);

        animateColorChange(colorPrimary);

        songTitle.setTextColor(songTitleTextColor);
        songArtist.setTextColor(artistNameTextColor);
    }

    private void animateColorChange(final int newColor) {
        if (lastFooterColor != -1 && lastFooterColor != newColor) {
            ViewUtil.animateViewColor(footer, lastFooterColor, newColor, 300);
        } else {
            footer.setBackgroundColor(newColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(newColor);
        }
        lastFooterColor = newColor;
    }

    private void setUpMusicControllers() {
        setUpPrevNext();
        setUpRepeatButton();
        setUpShuffleButton();
        setUpProgressSlider();
    }

    private void setUpProgressSlider() {
        progressSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    getApp().getMusicPlayerRemote().seekTo(progress);
                }
                currentSongProgress.setText(MusicUtil.getReadableDurationString(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setUpPrevNext() {
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getApp().getMusicPlayerRemote().playNextSong();
            }
        });
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getApp().getMusicPlayerRemote().back();
            }
        });
    }

    private void setUpShuffleButton() {
        updateShuffleState();
        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getApp().getMusicPlayerRemote().toggleShuffleMode();
            }
        });
    }

    private void setUpRepeatButton() {
        updateRepeatState();
        repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getApp().getMusicPlayerRemote().cycleRepeatMode();
            }
        });
    }

    private void updateRepeatState() {
        switch (getApp().getMusicPlayerRemote().getRepeatMode()) {
            case MusicService.REPEAT_MODE_NONE:
                repeatButton.setImageResource(R.drawable.ic_repeat_grey600_48dp);
                break;
            case MusicService.REPEAT_MODE_ALL:
                repeatButton.setImageResource(R.drawable.ic_repeat_white_48dp);
                break;
            default:
                repeatButton.setImageResource(R.drawable.ic_repeat_one_white_48dp);
                break;
        }
    }

    private void updateShuffleState() {
        switch (getApp().getMusicPlayerRemote().getShuffleMode()) {
            case MusicService.SHUFFLE_MODE_SHUFFLE:
                shuffleButton.setImageResource(R.drawable.ic_shuffle_white_48dp);
                break;
            default:
                shuffleButton.setImageResource(R.drawable.ic_shuffle_grey600_48dp);
                break;
        }
    }

    @Override
    protected void updateControllerState() {
        super.updateControllerState();
        updateRepeatState();
        updateShuffleState();
    }

    private void startMusicControllerStateUpdateThread() {
        killThreads = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int currentPosition = 0;
                int total = 0;
                while (getApp().getMusicPlayerRemote().isMusicBound() && !killThreads) {
                    try {
                        total = getApp().getMusicPlayerRemote().getSongDurationMillis();
                        currentPosition = getApp().getMusicPlayerRemote().getSongProgressMillis();
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception e) {
                    }
                    progressSlider.setMax(total);
                    progressSlider.setProgress(currentPosition);
                }
            }
        }).start();
    }

    @Override
    public void onMusicRemoteEvent(MusicRemoteEvent event) {
        super.onMusicRemoteEvent(event);
        switch (event.getAction()) {
            case MusicRemoteEvent.NEXT:
                updateCurrentSong();
                break;
            case MusicRemoteEvent.PREV:
                updateCurrentSong();
                break;
            case MusicRemoteEvent.REPEAT_MODE_CHANGED:
                updateRepeatState();
                break;
            case MusicRemoteEvent.SHUFFLE_MODE_CHANGED:
                updateShuffleState();
                break;
        }
    }

    private void prepareViewsForOpenAnimation() {
        footer.setPivotY(0);
        footer.setScaleY(0);
    }

    private void animateActivityOpened(int startDelay) {
        ViewPropertyAnimator.animate(footer)
                .scaleX(1)
                .scaleY(1)
                .setInterpolator(new DecelerateInterpolator(4))
                .setDuration(DEFAULT_ANIMATION_TIME)
                .setStartDelay(startDelay)
                .start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            animateActivityOpened(DEFAULT_DELAY);
        }
    }

    private void setUpToolBar() {
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setTitle(null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected boolean openCurrentPlayingIfPossible(Pair[] sharedViews) {
        onBackPressed();
        return true;
    }
}