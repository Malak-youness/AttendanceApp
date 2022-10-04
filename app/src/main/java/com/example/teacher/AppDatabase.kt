package com.example.teacher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.lang.IllegalStateException

/**
 * Basic database class for the application
 * The only class that should use this is [AppProvider]
 * */

private const val TAG="AppDatabase"

private const val DATABASE_NAME="Attendances.db"
private const val DATABASE_VERSION=1

//extends sqliteopenhelper which manages database creation and version management
internal class AppDatabase private constructor(context: Context): SQLiteOpenHelper(context, DATABASE_NAME,null,
    DATABASE_VERSION){

    override fun onCreate(db: SQLiteDatabase) {
        //onCreate will be called if the db doesn't already exists
        //CREATE TABLE EVENTS (_id_ INTEGER PRIMARY KEY NOT NULL, Name TEXT NOT NULL, Date DATE NOT NULL, Time INTEGER NOT NULL)
        Log.d(TAG,"AppDatabase.onCreate: starts")
        val sSQL="""CREATE TABLE ${EventsContract.TABLE_NAME} (
            ${EventsContract.Columns.ID} INTEGER PRIMARY KEY NOT NULL,
            ${EventsContract.Columns.EVENT_NAME} TEXT NOT NULL,
            ${EventsContract.Columns.EVENT_DATE} TEXT NOT NULL,
            ${EventsContract.Columns.EVENT_TIME} TEXT NOT NULL);""".replaceIndent(" ")
        Log.d(TAG,sSQL)
        db.execSQL(sSQL)

        val sSQLAttendees="""CREATE TABLE ${AttendeesContract.TABLE_NAME} (
            ${AttendeesContract.Columns.ID} INTEGER PRIMARY KEY NOT NULL,
            ${AttendeesContract.Columns.ATTENDEES_EVENT_ID} INTEGER NOT NULL,
            ${AttendeesContract.Columns.MAC} TEXT NOT NULL);""".replaceIndent(" ")
        Log.d(TAG,sSQLAttendees)
        db.execSQL(sSQLAttendees)

        val sSQLTrigger="""CREATE TRIGGER Remove_Event
            AFTER DELETE ON ${EventsContract.TABLE_NAME}
            FOR EACH ROW
            BEGIN
            DELETE FROM ${AttendeesContract.TABLE_NAME}
            WHERE ${AttendeesContract.Columns.ID} = OLD.${EventsContract.Columns.ID};
            END;""".replaceIndent(" ")
        Log.d(TAG,sSQLTrigger)
        db.execSQL(sSQLTrigger)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //Called when the database needs to be upgraded
        Log.d(TAG,"AppDatabase.onUpgrade: starts")
        when(oldVersion){
            1-> {
                // Upgrade logic from version 1
                }
            else-> throw IllegalStateException("onUpgrade() with unknown newVersion: $newVersion")
        }
    }

    //makes sure that we have a single database being accessed at anytime
    companion object : SingletonHolder<AppDatabase, Context>(::AppDatabase)
}