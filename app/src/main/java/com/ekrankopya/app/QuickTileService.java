package com.ekrankopya.app;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Hızlı ayarlar kutucuğu: ana anahtarla aynı işi yapar (logo düğmesini göster/gizle). */
public class QuickTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean now = Prefs.get(this).getBoolean(Prefs.ENABLED, true);
        Prefs.get(this).edit().putBoolean(Prefs.ENABLED, !now).apply();
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) {
            return;
        }
        boolean on = Prefs.get(this).getBoolean(Prefs.ENABLED, true);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
