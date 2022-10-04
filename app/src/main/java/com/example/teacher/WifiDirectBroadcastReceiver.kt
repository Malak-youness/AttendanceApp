package com.example.teacher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

class WiFiDirectBroadcastReceive(private val mManager: WifiP2pManager,
                                 private val mChanel: WifiP2pManager.Channel,
                                 private val mActivity: MainActivity): BroadcastReceiver() {

    private val TAG="WiFiDirBrodR"

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {

        Log.d(TAG,"onReceive called")

        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                Log.d(TAG,"action: Wifi State changed")

                when (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                        Toast.makeText(context, "WiFi is ON", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(context,"WiFi is OFF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            //This intent is broadcast when discoverPeers succeeds and detects peers
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        // Call WifiP2pManager.requestPeers() to get a list of current peers

                        // Request available peers from the wifi p2p manager. This is an
                        // asynchronous call and the calling activity is notified with a
                        // callback on PeerListListener.onPeersAvailable()

                        // suppressed permission since to discover we should have been given permission
                        mManager.requestPeers(mChanel,mActivity.peerListListener)
                        Log.d(TAG,"action: P2P peers changed")
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        // Respond to new connection or disconnections
                        Log.d(TAG,"action: Wifi p2p connection changed ")
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        // Respond to this device's wifi state changing
                        Log.d(TAG,"action: Wifi p2p device changed" )
                    }

                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION ->{
                        when(intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE,1000)){
                            WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED ->{
                                Log.d(TAG,"discovery started")
                                mActivity.textConStat.text="Discovery started"
                            }
                            WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED ->{
                                Log.d(TAG,"discovery stopped")
                                mActivity.textConStat.text="Discovery stopped"
                            }
                        }
                    }
        }


    }
}