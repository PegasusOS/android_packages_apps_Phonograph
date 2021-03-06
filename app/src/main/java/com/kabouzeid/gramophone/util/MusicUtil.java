package com.kabouzeid.gramophone.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.kabouzeid.gramophone.helper.MusicPlayerRemote;
import com.kabouzeid.gramophone.loader.PlaylistLoader;
import com.kabouzeid.gramophone.loader.SongLoader;
import com.kabouzeid.gramophone.model.Artist;
import com.kabouzeid.gramophone.model.Playlist;
import com.kabouzeid.gramophone.model.Song;

import org.omnirom.gramophone.R;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class MusicUtil {
    public static final String TAG = MusicUtil.class.getSimpleName();

    public static Uri getMediaStoreAlbumCoverUri(long albumId) {
        final Uri sArtworkUri = Uri
                .parse("content://media/external/audio/albumart");

        return ContentUris.withAppendedId(sArtworkUri, albumId);
    }

    public static Uri getSongFileUri(long songId) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId);
    }

    public static Intent createShareSongFileIntent(final Song song, Context context) {
        try {

            return new Intent()
                    .setAction(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName(), new File(song.data)))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setType("audio/*");
        } catch (IllegalArgumentException e) {
            // TODO the path is most likely not like /storage/emulated/0/... but something like /storage/28C7-75B0/...
            e.printStackTrace();
            Toast.makeText(context, "Could not share this file, I'm aware of the issue.", Toast.LENGTH_SHORT).show();
            return new Intent();
        }
    }

    public static void setRingtone(final Context context, final long id) {
        final ContentResolver resolver = context.getContentResolver();
        final Uri uri = getSongFileUri(id);
        try {
            final ContentValues values = new ContentValues(2);
            values.put(MediaStore.Audio.AudioColumns.IS_RINGTONE, "1");
            values.put(MediaStore.Audio.AudioColumns.IS_ALARM, "1");
            resolver.update(uri, values, null, null);
        } catch (final UnsupportedOperationException ignored) {
            return;
        }

        try {
            Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns.TITLE},
                    BaseColumns._ID + "=?",
                    new String[]{String.valueOf(id)},
                    null);
            try {
                if (cursor != null && cursor.getCount() == 1) {
                    cursor.moveToFirst();
                    Settings.System.putString(resolver, Settings.System.RINGTONE, uri.toString());
                    final String message = context.getString(R.string.x_has_been_set_as_ringtone, cursor.getString(0));
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    public static String getArtistInfoString(final Context context, final Artist artist) {
        int albumCount = artist.getAlbumCount();
        int songCount = artist.getSongCount();
        String albumString = albumCount == 1 ? context.getResources().getString(R.string.album) : context.getResources().getString(R.string.albums);
        String songString = songCount == 1 ? context.getResources().getString(R.string.song) : context.getResources().getString(R.string.songs);
        return albumCount + " " + albumString + " ??? " + songCount + " " + songString;
    }

    public static String getPlaylistInfoString(final Context context, List<Song> songs) {
        final int songCount = songs.size();
        final String songString = songCount == 1 ? context.getResources().getString(R.string.song) : context.getResources().getString(R.string.songs);

        long duration = 0;
        for (int i = 0; i < songs.size(); i++) {
            duration += songs.get(i).duration;
        }

        return songCount + " " + songString + " ??? " + MusicUtil.getReadableDurationString(duration);
    }

    public static String getReadableDurationString(long songDurationMillis) {
        long minutes = (songDurationMillis / 1000) / 60;
        long seconds = (songDurationMillis / 1000) % 60;
        if (minutes < 60) {
            return String.format("%01d:%02d", minutes, seconds);
        } else {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
    }

    //iTunes uses for example 1002 for track 2 CD1 or 3011 for track 11 CD3.
    //this method converts those values to normal tracknumbers
    public static int getFixedTrackNumber(int trackNumberToFix) {
        return trackNumberToFix % 1000;
    }

    public static void insertAlbumArt(Context context, long albumId, String path) {
        ContentResolver contentResolver = context.getContentResolver();

        Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
        contentResolver.delete(ContentUris.withAppendedId(artworkUri, albumId), null, null);

        ContentValues values = new ContentValues();
        values.put("album_id", albumId);
        values.put("_data", path);

        contentResolver.insert(artworkUri, values);
    }

    public static void deleteAlbumArt(Context context, long albumId) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri localUri = Uri.parse("content://media/external/audio/albumart");
        contentResolver.delete(ContentUris.withAppendedId(localUri, albumId), null, null);
    }

    public static File createAlbumArtFile(Context context) {
        return new File(createAlbumArtDir(context), String.valueOf(System.currentTimeMillis()) + ".png");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File createAlbumArtDir(Context context) {
        File albumArtDir = new File(context.getExternalFilesDir(null), "/albumthumbs/");
        if (!albumArtDir.exists()) {
            albumArtDir.mkdirs();
            try {
                new File(albumArtDir, ".nomedia").createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return albumArtDir;
    }

    public static void deleteTracks(final Context context, final List<Song> songs) {
        final String[] projection = new String[]{
                BaseColumns._ID, MediaStore.MediaColumns.DATA
        };
        final StringBuilder selection = new StringBuilder();
        selection.append(BaseColumns._ID + " IN (");
        for (int i = 0; i < songs.size(); i++) {
            selection.append(songs.get(i).id);
            if (i < songs.size() - 1) {
                selection.append(",");
            }
        }
        selection.append(")");

        try {
            final Cursor cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection.toString(),
                    null, null);
            if (cursor != null) {
                // Step 1: Remove selected tracks from the current playlist, as well
                // as from the album art cache
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    final int id = cursor.getInt(0);
                    final Song song = SongLoader.getSong(context, id);
                    MusicPlayerRemote.removeFromQueue(song);
                    cursor.moveToNext();
                }

                // Step 2: Remove selected tracks from the database
                context.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        selection.toString(), null);

                // Step 3: Remove files from card
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    final String name = cursor.getString(1);
                    try { // File.delete can throw a security exception
                        final File f = new File(name);
                        if (!f.delete()) {
                            // I'm not sure if we'd ever get here (deletion would
                            // have to fail, but no exception thrown)
                            Log.e("MusicUtils", "Failed to delete file " + name);
                        }
                        cursor.moveToNext();
                    } catch (final SecurityException ex) {
                        cursor.moveToNext();
                    } catch (NullPointerException e) {
                        Log.e("MusicUtils", "Failed to find file " + name);
                    }
                }
                cursor.close();
            }
            context.getContentResolver().notifyChange(Uri.parse("content://media"), null);
            Toast.makeText(context, context.getString(R.string.deleted_x_songs, songs.size()), Toast.LENGTH_SHORT).show();
        } catch (SecurityException ignored) {
        }
    }

    public static boolean isFavoritePlaylist(final Context context, final Playlist playlist) {
        return playlist.name != null && playlist.name.equals(context.getString(R.string.favorites));
    }

    public static Playlist getFavoritesPlaylist(final Context context) {
        return PlaylistLoader.getPlaylist(context, context.getString(R.string.favorites));
    }

    private static Playlist getOrCreateFavoritesPlaylist(final Context context) {
        return PlaylistLoader.getPlaylist(context, PlaylistsUtil.createPlaylist(context, context.getString(R.string.favorites)));
    }

    public static boolean isFavorite(final Context context, final Song song) {
        return PlaylistsUtil.doPlaylistContains(context, getFavoritesPlaylist(context).id, song.id);
    }

    public static void toggleFavorite(final Context context, final Song song) {
        if (isFavorite(context, song)) {
            PlaylistsUtil.removeFromPlaylist(context, song, getFavoritesPlaylist(context).id);
        } else {
            PlaylistsUtil.addToPlaylist(context, song, getOrCreateFavoritesPlaylist(context).id, false);
        }
    }

    public static boolean isArtistNameUnknown(@Nullable String artistName) {
        if (TextUtils.isEmpty(artistName)) return false;
        artistName = artistName.trim().toLowerCase();
        return artistName.equals("unknown") || artistName.equals("<unknown>");
    }

    public static String getSectionName(@Nullable String musicMediaTitle) {
        if (TextUtils.isEmpty(musicMediaTitle)) return "";
        musicMediaTitle = musicMediaTitle.trim().toLowerCase();
        if (musicMediaTitle.startsWith("the ")) {
            musicMediaTitle = musicMediaTitle.substring(4);
        } else if (musicMediaTitle.startsWith("a ")) {
            musicMediaTitle = musicMediaTitle.substring(2);
        }
        if (musicMediaTitle.isEmpty()) return "";
        return String.valueOf(musicMediaTitle.charAt(0)).toUpperCase();
    }
}
