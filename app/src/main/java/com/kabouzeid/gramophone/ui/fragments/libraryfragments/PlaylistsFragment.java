package com.kabouzeid.gramophone.ui.fragments.libraryfragments;

import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;

import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.adapter.PlaylistAdapter;
import com.kabouzeid.gramophone.loader.PlaylistLoader;
import com.kabouzeid.gramophone.model.Playlist;
import com.kabouzeid.gramophone.model.smartplaylist.HistoryPlaylist;
import com.kabouzeid.gramophone.model.smartplaylist.LastAddedPlaylist;
import com.kabouzeid.gramophone.model.smartplaylist.MyTopTracksPlaylist;

import java.util.ArrayList;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class PlaylistsFragment extends AbsLibraryPagerRecyclerViewFragment<PlaylistAdapter, LinearLayoutManager> {

    public static final String TAG = PlaylistsFragment.class.getSimpleName();

    @NonNull
    @Override
    protected LinearLayoutManager createLayoutManager() {
        return new LinearLayoutManager(getActivity());
    }

    @NonNull
    @Override
    protected PlaylistAdapter createAdapter() {
        return new PlaylistAdapter(getLibraryFragment().getMainActivity(), getAllPlaylists(), R.layout.item_list_single_row, getLibraryFragment());
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.no_playlists;
    }

    @Override
    public void onMediaStoreChanged() {
        getAdapter().swapDataSet(getAllPlaylists());
    }

    private ArrayList<Playlist> getAllPlaylists() {
        ArrayList<Playlist> playlists = new ArrayList<>();

        playlists.add(new LastAddedPlaylist(getActivity()));
        playlists.add(new HistoryPlaylist(getActivity()));
        playlists.add(new MyTopTracksPlaylist(getActivity()));

        playlists.addAll(PlaylistLoader.getAllPlaylists(getActivity()));

        return playlists;
    }
}