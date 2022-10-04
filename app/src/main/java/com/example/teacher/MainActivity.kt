package com.example.teacher

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ajts.androidmads.library.SQLiteToExcel
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.Exception
import kotlin.collections.ArrayList

private const val TAG = "MainActivity"

//WifiP2p variables
private const val DISCOVER_PERMISSION_REQUEST_CODE=1001
private const val REQUEST_CODE_LOCATION_SETTINGS=1617
private const val PEER_LIST_KEY="PeerList"
private const val DB_LIST_KEY="DatabaseList"
private const val EVENT_NAME_KEY="EventName"
private const val EVENT_DATE_KEY="EventDate"
private const val EVENT_TIME_KEY="EventTime"


//SQLtoExcel variables
private const val WRITE_EXTERNAL_REQUEST_CODE=3000
private var ExcelName="AttendanceList"
private var Count:Int=0


class MainActivity : AppCompatActivity() {

    //WifiDirect (WifiP2p) requirements
    private lateinit var wifiManager: WifiManager
    private lateinit var mManager: WifiP2pManager
    private lateinit var mChannel: WifiP2pManager.Channel
    private lateinit var mReceiver: BroadcastReceiver

    private val mIntentFilter= IntentFilter()

    private var peers= ArrayList<WifiP2pDevice>()
    private var dbPeers= ArrayList<WifiP2pDevice>()

    lateinit var peerListListener:WifiP2pManager.PeerListListener
    private var event=Event("","","") // initialized event here

    private val timer=Timer()
    //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG,".onCreate called")

       ////WifiDirect (WifiP2p) requirements

        //initialize wifiManger, wifiP2pManager, wifiP2pManager channel
        wifiManager=this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        mManager=getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        mChannel=mManager.initialize(this, Looper.getMainLooper(),null)

        //initialize broadcast receiver using constructor of WiFiDirectBroadcastReceive class
        //passing the previous as argument
        mReceiver=WiFiDirectBroadcastReceive(mManager,mChannel,this)

        mIntentFilter.apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }

        //save the peer list before destruction of activity
        //retrieve the saved peer list and assign it to variable
        if(savedInstanceState!=null) {
            peers= savedInstanceState.getParcelableArrayList<WifiP2pDevice>(PEER_LIST_KEY) as ArrayList<WifiP2pDevice>
            dbPeers= savedInstanceState.getParcelableArrayList<WifiP2pDevice>(DB_LIST_KEY) as ArrayList<WifiP2pDevice>
            event.name=savedInstanceState.getString(EVENT_NAME_KEY) as String
            event.date=savedInstanceState.getString(EVENT_DATE_KEY) as String
            event.time=savedInstanceState.getString(EVENT_TIME_KEY) as String
        }

        val listener= View.OnClickListener {
            when(it.id){

                R.id.btnAttendance ->{
                    Log.d(TAG,"Attendance call is initiated")

                    //Turn Wifi On
                    if(!wifiManager.isWifiEnabled){
                        //API Q (29)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ) {
                            //wifi can be turned off/on pragmatically
                            Toast.makeText(this,"The wifi will be turned On", Toast.LENGTH_LONG).show()
                            wifiManager.isWifiEnabled=true
                        }else{
                            //guide user to the settings panel for wifi
                            Toast.makeText(this,"Turn on wifi from settings", Toast.LENGTH_LONG).show()
                            val settingsIntent= Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                            startActivity(settingsIntent)
                        }
                    }

                    timer.schedule(object: TimerTask(){
                        override fun run() {
                            setUpLocationService()
                        }
                    },3000)

                    //set up location services
//                    setUpLocationService()

                   ////Discover Peers
                    //runtime request permission although permissions were added in Manifest file
                    //security purposes added after API 22
                    if(ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){

                        //Permission is granted
                        mManager.discoverPeers(mChannel, object : WifiP2pManager.ActionListener{
                            override fun onSuccess() {
                                Log.d(TAG,"discovery request successful ")
                            }

                            override fun onFailure(reasonCode: Int) {
                                Log.d(TAG,"discovery request failed with reason code $reasonCode")
                            }
                        })
                    }else{
                        //permission is denied
                        //we need to make snackbar (like toast) to grant permission
                        //checks if the user chose don't ask again and denied or only deny
                        //if only deny we request permissions from user
                        //if don't ask again, we guide the user to the app settings to allow permission
                        Snackbar.make(btnAttendance,"Please grant access to your location,then re-click Attendance Call button",
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction("Grant Access") {
                                Log.d(TAG, "Snackbar onClick: starts")
                                if (ActivityCompat.shouldShowRequestPermissionRationale(
                                        this,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                ) {
                                    //condition returns true if the user has previously denied the request
                                    Log.d(TAG, "Snackbar onclick: calling requestPermissions")
                                    ActivityCompat.requestPermissions(
                                        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                                        DISCOVER_PERMISSION_REQUEST_CODE
                                    )
                                } else {
                                    //condition returns false if first time request or the user chose don't ask again
                                    //the user has permanently denied the permission or first time permission, take them directly to the settings
                                    Log.d(TAG, "Snackbar onClick: launching settings")
                                    val intent = Intent()
                                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    val uri = Uri.fromParts("package", this.packageName, null)
                                    Log.d(TAG, "Snackbar onClick: Uri is $uri")
                                    intent.data = uri
                                    this.startActivity(intent)

                                    // try adding if ( Context == PERMISSION_GRANTED)

                                }
                                Log.d(TAG, "Snackbar onClick: ends")
                            }.show()
                    }
                }

                R.id.btnStopSave->{
                    Log.d(TAG,"Stop and Save initiated")

                    //Stop discovering
                    mManager.stopPeerDiscovery(mChannel,object:WifiP2pManager.ActionListener{
                        //the callbacks represent the success or failure of stop discovery request
                        //to the framework
                        override fun onSuccess() {
                            Log.d(TAG,"Stop discovering request: Successful ")
                            peers.clear()
                        }

                        override fun onFailure(p0: Int) {
                            Log.d(TAG,"Stop discovering request: Failure")
                        }
                    })

                    saveToDatabase(event)
//                    testDatabase()

                 }

                R.id.btnCreateEvent->{
                    Log.d(TAG,"Create Event initiated")
                    val eventName=txtEventName.text.toString()
                    event=Event(eventName, getDate(), getTime())
                    txtEventName.text.clear()
                    txtEventName.append("Event was created")
                }

                R.id.btnToExcel->{
                    Log.d(TAG,"ToExcel has been triggered")
                    if (checkPermission()) {
                        Log.d(TAG, "btnToExcel: Permissions already granted, converting to excel")
                        sqlToExcel()
                    } else {
                        Log.d(TAG, "btnToExcel: Permissions were not granted, request...")
                        requestPermission()
                    }

                }
            }
        }

        btnAttendance.setOnClickListener(listener)
        btnStopSave.setOnClickListener(listener)
        btnCreateEvent.setOnClickListener(listener)
        btnToExcel.setOnClickListener(listener)

        //create a peer listener for when p2p peers changed
        //Everytime peerList changes, we need to check if the devices where added to the DatabaseList
        peerListListener=WifiP2pManager.PeerListListener { peerList ->
            val refreshedPeers = peerList.deviceList
            if (refreshedPeers != peers) {
                if (peers.isEmpty()) {

                    Log.d(TAG, "refreshed Peers!= peers, peers are empty")
                    //First time
                    //add all Peers into Database and in PeersList
                     dbPeers.addAll(refreshedPeers)
                     peers.addAll(refreshedPeers)
                } else {
                    //peers is not empty (not first time)
                    Log.d(TAG, "refreshed Peers!= peers, peers not empty")
                    for (item in refreshedPeers) {
                        if (!dbPeers.contains(item)) {
                            val dbAddRef = dbPeers.add(item)
                            if (dbAddRef) {
                                Log.d(TAG, "$item has been added to database")
                            }
                        }
                    }
                }

                var value = 0
                while(peers.size >value){
                    Log.d(TAG,"peers list item is ${peers[value]}")
                    value++
                }
                value=0
                while(dbPeers.size>value){
                    Log.d(TAG,"db peers List item is ${dbPeers[value]}")
                    value++
                }
                value=0

                peers.clear()
                peers.addAll(refreshedPeers)
                //perform any other updates needed based on the new list
                //of peers connected to the WiFi P2P network

//                if(peers.isEmpty()){
//                    //this is activated when we stop discovering
//                    Log.d(TAG,"no devices found")
//                    Toast.makeText(this,"No Devices found",Toast.LENGTH_LONG).show()
//                    return@PeerListListener
//                }
            }
         }
  }
    private fun sqlToExcel(){
        Log.d(TAG,"sqlToExcel is initiated")

        //create a string for where you want to save the excel file
        //file name
        val savePath="/attendanceExcel"
        val file= File(Environment.getExternalStorageDirectory(),savePath)
        if(!file.exists()){
            file.mkdirs()
        }
        //create the sqliteToExcel object
        val sqliteToExcel= SQLiteToExcel(this,"Attendances.db",file.absolutePath)

        //use the sqliteToExcel object to create the excel sheet
        sqliteToExcel.exportAllTables("$ExcelName$Count.xls",object:SQLiteToExcel.ExportListener{
            override fun onStart() {
                Log.d(TAG,"exportAllTables .onStart called")
            }

            override fun onCompleted(filePath: String?) {
                Log.d(TAG,"exportAllTables .onCompleted called")
                Toast.makeText(this@MainActivity,"The excel sheet $ExcelName$Count.xls is located in file location $filePath",Toast.LENGTH_LONG).show()
                Count+=1
            }

            override fun onError(e: java.lang.Exception?) {
                Log.d(TAG,"exportAllTables .onError called")
                Toast.makeText(this@MainActivity,"Failed to export Data with error $e",Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun requestPermission(){
        Log.d(TAG,"requestPermission is called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is (11) R or above
            try{
                Log.d(TAG,"requestPermission: try")
                val intent=Intent()
                intent.action=Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri=Uri.fromParts("package",this.packageName,null)
                intent.data=uri
                storageActivityResultLauncher.launch(intent)
            }catch(e:Exception){
                Log.e(TAG,"requestPermission: catch",e)
                val intent=Intent()
                intent.action=Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                storageActivityResultLauncher.launch(intent)
            }
        }else{
            Log.d(TAG,"requestPermission below 11 ")
            //Android is below (11) R
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE),
                WRITE_EXTERNAL_REQUEST_CODE)
        }
    }

    private fun checkPermission():Boolean{
        Log.d(TAG,"checkPermission is called")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //Android is 11(R) or above
            Environment.isExternalStorageManager()
        }else{
            //Android is below 11(R)
            val write=ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read=ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)

            write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
        }
    }

    private val storageActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), object:
            ActivityResultCallback<ActivityResult> {
            override fun onActivityResult(result: ActivityResult?) {
                Log.d(TAG,"onActivityResult: ")
                //here we will handle the result of our intent
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                    //Android is 11(R) or above
                    if(Environment.isExternalStorageManager()){
                        //Manage External Storage Permission is granted
                        Log.d(TAG,"onActivityResult: Manage External Storage Permission is granted")
                        sqlToExcel()
                    }else{
                        //Manage External Storage Permission is denied
                        Log.d(TAG,"onActivityResult: Manage External Storage Permission is denied")
                        Toast.makeText(this@MainActivity,"Manage External Storage Permission is denied",Toast.LENGTH_SHORT).show()
                    }
                }else{
                    //Android is below 11(R)
                }
            }
        }
    )


    private fun saveToDatabase(ev: Event){
        Log.d(TAG,"saveToDatabase: called")

        //event name not empty
        //ie event name is created
        if(ev.name.isNotEmpty()){
            Log.d(TAG,"saveToDatabase, event not empty")
            if(dbPeers.isNotEmpty()){
                Log.d(TAG,"saveToDatabase, dbPeers not empty")

                //if dbPeers exist, we have attendance
                //hence we add the event to the db
                val values=ContentValues().apply {
                    put(EventsContract.Columns.EVENT_NAME,ev.name)
                    put(EventsContract.Columns.EVENT_DATE,ev.date)
                    put(EventsContract.Columns.EVENT_TIME,ev.time)
                }
                val eventUri=contentResolver.insert(EventsContract.CONTENT_URI,values)
                if(eventUri!=null) {

                    val eventId = EventsContract.getId(eventUri)
                    Log.d(TAG,"event of Id $eventId is added to database")

                    var value=0
                    while(dbPeers.size>value){
                        Log.d(TAG,"Start adding attendees to database process")
                        val values1=ContentValues().apply {
                            put(AttendeesContract.Columns.ATTENDEES_EVENT_ID,eventId)
                            put(AttendeesContract.Columns.MAC,dbPeers[value].deviceAddress)
                        }
                        val attendeeUri=contentResolver.insert(AttendeesContract.CONTENT_URI,values1)
                        if(attendeeUri!=null) {
                            val attendeeId = AttendeesContract.getId(attendeeUri)
                            Log.d(TAG,"attendee of device ${dbPeers[value].deviceName} and id $attendeeId has been added to database")
                        }
                        value++
                    }
                }
            }
        }
        dbPeers.clear()
        Log.d(TAG,"dbPeers is cleared")
    }

    private fun testDatabase(){
        Log.d(TAG,"testDatabase initiated")
        val cursor= contentResolver.query(EventsContract.CONTENT_URI,
            null,
            null,
            null,
            null)

        cursor.use {
            if (it != null) {
                while (it.moveToNext()) {
                    //Cycle through all records
                    with(it) {
                        val id = getLong(0)
                        val name = getString(1)
                        val date = getString(2)
                        val time = getString(3)
                        val result = "ID: $id. Name: $name Date: $date Time: $time"
                        Log.d(TAG, "onTestDatabase: reading data from Event $result")
                    }
                }
            }
        }
         val cursor1=contentResolver.query(AttendeesContract.CONTENT_URI,
             null,
             null,
             null,
             null)
        cursor1.use{
            if (it!=null){
                while (it.moveToNext()){
                    //cycle through all records of attendees
                    with(it){
                        val id=getLong(0)
                        val eventId=getLong(1)
                        val mac=getString(2)
                        val result = "ID: $id. EventId: $eventId Mac: $mac "
                        Log.d(TAG,"onTestDatabase: reading data from Attendee $result ")
                    }
                }
            }
        }
    }


    //suppressed permission since the discoverPeers will only be called if the user granted Permission
    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG,"onRequestPermissionResult called")

        when(requestCode) {
            DISCOVER_PERMISSION_REQUEST_CODE ->{
                //If request is cancelled, the result arrays are empty
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    //Permission is granted
                    //continue the action in your app
                    mManager.discoverPeers(mChannel, object : WifiP2pManager.ActionListener{
                        override fun onSuccess() {
                            Log.d(TAG,"discovery request successful ")
                        }

                        override fun onFailure(reasonCode: Int) {
                            Log.d(TAG,"discovery request failed with reason code $reasonCode")
                        }
                    })
                }else{
                    //explain to the user that the feature is unavailable
                    //permissions denied
                    //disable the functionality that depends on this permission
                }
            }
            WRITE_EXTERNAL_REQUEST_CODE ->{
                if(grantResults.isNotEmpty()){
                    //check each permission if granted
                    val write:Boolean= grantResults[0]==PackageManager.PERMISSION_GRANTED
                    val read:Boolean= grantResults[1]==PackageManager.PERMISSION_GRANTED

                    if(write && read){
                        //External Storage permissions granted
                        Log.d(TAG,"onRequestPermissionsResult: External Storage permissions granted")
                        sqlToExcel()
                    }else{
                        //External Storage permission denied
                        Log.d(TAG,"onRequestPermissionsResult: External Storage permission denied")
                        Toast.makeText(this,"External Storage permission denied",Toast.LENGTH_SHORT).show()
                    }
                }
            }

            //add other 'when' lines to check for other permissions this
            //app might request
            else ->{
                //Ignore all other results
            }
        }
    }

    //function created to set up location services
    private fun setUpLocationService(){

        Log.d(TAG,"setting up location services")

        //Location Request
        val locationRequest= LocationRequest.create()
            .setPriority(android.location.LocationRequest.QUALITY_LOW_POWER)
            .setInterval(10 * 1000) // 10 seconds in milliseconds

        //builder of the request + adding of the request
        val builder=LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        //specifies the client of this location service
        //use of google API SettingsClient
        //which is the main entry point for interacting with the location settings enabler API
        val client= LocationServices.getSettingsClient(this) as SettingsClient

        //task created to check if the location settings are satisfied
        //to carry out the desired location requests
        val task: Task<LocationSettingsResponse> =client.checkLocationSettings(builder.build())

        //listener for when the task is successful
        task.addOnSuccessListener(this, object: OnSuccessListener<LocationSettingsResponse> {
            override fun onSuccess(locationSettingsResponse: LocationSettingsResponse?) {
                Log.d(TAG,"Location settings satisfied")
            }
        })

        //listener for when the task fails
        task.addOnFailureListener(this,object: OnFailureListener {
            override fun onFailure(e: Exception) {
                val statusCode=(e as ApiException).statusCode
                when(statusCode){
                    CommonStatusCodes.RESOLUTION_REQUIRED ->{
                        Log.w(TAG,"Location Settings not satisfied, attempting resolution intent")
                        try{
                            val resolvable=e as ResolvableApiException
                            resolvable.startResolutionForResult(this@MainActivity,REQUEST_CODE_LOCATION_SETTINGS)
                            //from me
                            Log.d(TAG,"resolution intent successful")
                        }catch(sendIntentException:IntentSender.SendIntentException){
                            Log.e(TAG,"Unable to start resolution intent")
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE ->{
                        Log.w(TAG,"Location settings not satisfied and can't be changed")
                    }
                }
            }
        })

    }

    //when rotating it saves the peer list right before the activity is destroyed
    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(TAG,"onSveInstanceState: called")
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(PEER_LIST_KEY, peers)
        outState.putParcelableArrayList(DB_LIST_KEY,dbPeers)
        outState.putString(EVENT_NAME_KEY,event.name)
        outState.putString(EVENT_DATE_KEY,event.date)
        outState.putString(EVENT_TIME_KEY,event.time)
    }

    //register receiver in onResume callback of Activity lifecycle
    override fun onResume() {
        super.onResume()
        Log.d(TAG,"onResume called")
        registerReceiver(mReceiver, mIntentFilter)
    }

    //unregister the receiver
    override fun onPause() {
        super.onPause()
        Log.d(TAG,"onPause called")
        unregisterReceiver(mReceiver)
    }

    // Testing Insert and Update
    private fun testInsert(){
        val values=ContentValues().apply {
            put(EventsContract.Columns.EVENT_NAME,"class2")
            put(EventsContract.Columns.EVENT_DATE,getDate())
            put(EventsContract.Columns.EVENT_TIME,getTime())
        }
        val uri =contentResolver.insert(EventsContract.CONTENT_URI,values)
        Log.d(TAG,"New row id (in uri) is $uri")
        Log.d(TAG,"id (in uri) is ${EventsContract.getId(uri!!)}")
    }

    private fun getDate():String{
        val current=LocalDate.now()
        val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formatted=current.format(formatter)
        return formatted
    }

    private fun getTime():String{
        val current=LocalTime.now()
        val formatter=DateTimeFormatter.ofPattern("HH:mm:ss")
        val formatted=current.format(formatter)
        return formatted
    }

    private fun testUpdate(){
        val values= ContentValues().apply {
            put(EventsContract.Columns.EVENT_DATE, getDate())
            put(EventsContract.Columns.EVENT_TIME,getTime())
        }
        val selection=EventsContract.Columns.ID + "= 2"

        val rowsAffected=contentResolver.update(EventsContract.CONTENT_URI,
        values,
        selection,
        null)

        Log.d(TAG," Number of rows updated is $rowsAffected")
    }

    private fun testDelete(){
        val eventUri=EventsContract.CONTENT_URI
        val rowsAffected=contentResolver.delete(eventUri,null,null)
        Log.d(TAG,"Number of rows deleted is $rowsAffected")
    }
}