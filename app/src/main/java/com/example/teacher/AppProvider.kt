package com.example.teacher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri
import android.util.Log
import java.lang.IllegalArgumentException

/**
 * Provider for the Teacher app. This is the only class that knows about [AppDatabase]
 * */

private const val TAG="AppProvider"

//Content authority is the symbolic name of the entire provider
 const val CONTENT_AUTHORITY="com.example.teacher.provider"

//An integer value for each URI
private const val EVENTS=100
private const val EVENTS_ID=101

private const val ATTENDEES=200
private const val ATTENDEES_ID=201

val CONTENT_AUTHORITY_URI: Uri =Uri.parse("content://$CONTENT_AUTHORITY")

// we also registered the appProvider in the manifest
class AppProvider:ContentProvider() {
    private val uriMatcher by lazy { buildUriMatcher() }

    private fun buildUriMatcher(): UriMatcher {
        //this functions adds all our recognised URIs to a uriMatcher object
        //hence the Content Provider can return a diff integer value for each URI

        Log.d(TAG, "buildUriMatcher starts")
        val matcher= UriMatcher(UriMatcher.NO_MATCH)

        //add URIs for Events table and Events id // addUri(authority, path, code)

        //e.g. content://com.example.teacher.provider/Events
        matcher.addURI(CONTENT_AUTHORITY, EventsContract.TABLE_NAME, EVENTS)
        //e.g. content://com.example.teacher.provider/Events/8
        matcher.addURI(CONTENT_AUTHORITY,"${EventsContract.TABLE_NAME}/#", EVENTS_ID)

        //add URIs for Attendees table and Attendees id
        matcher.addURI(CONTENT_AUTHORITY,AttendeesContract.TABLE_NAME, ATTENDEES)
        matcher.addURI(CONTENT_AUTHORITY,"${AttendeesContract.TABLE_NAME}/#", ATTENDEES_ID)

        return matcher
    }

    override fun onCreate(): Boolean {
        Log.d(TAG," onCreate: starts")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.d(TAG,"query: called with uri $uri")

        //we could use the value of match to know which table uri was passed and know which table
        //we should be using
        val match=uriMatcher.match(uri)
        Log.d(TAG,"query: match is $match")

        val queryBuilder = SQLiteQueryBuilder()

        when(match){
            EVENTS-> queryBuilder.tables=EventsContract.TABLE_NAME

            EVENTS_ID->{
                queryBuilder.tables=EventsContract.TABLE_NAME
                val eventId=EventsContract.getId(uri)
                queryBuilder.appendWhere("${EventsContract.Columns.ID} = ")
                queryBuilder.appendWhereEscapeString("$eventId")
            }
            ATTENDEES->queryBuilder.tables=AttendeesContract.TABLE_NAME

            ATTENDEES_ID->{
                queryBuilder.tables=AttendeesContract.TABLE_NAME
                val attendeeId=AttendeesContract.getId(uri)
                queryBuilder.appendWhere("${AttendeesContract.Columns.ID} = ")
                queryBuilder.appendWhereEscapeString("$attendeeId")
            }
            else -> throw IllegalArgumentException("Unknown Uri: uri")
        }

        //Open Database to perform the query and return the cursor
        val db = AppDatabase.getInstance(context!!).readableDatabase
        val cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder)
        Log.d(TAG, "query: rows in returned cursor = ${cursor.count}") // TODO remove this line

        return cursor
    }

    override fun getType(uri: Uri): String {

        return " "
        //return when (uriMatcher.match(uri)){
           // EVENTS->EventsContract.CONTENT_TYPE

          //  EVENTS_ID->EventsContract.CONTENT_ITEM_TYPE

          //  ATTENDEES->AttendeesContract.CONTENT_TYPE

          //  ATTENDEES_ID->AttendeesContract.CONTENT_ITEM_TYPE

         //   else-> throw IllegalArgumentException("unknown Uri: $uri")

    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.d(TAG,"insert: called with uri $uri")
        val match=uriMatcher.match(uri)
        Log.d(TAG,"insert: match is $match")

        val recordId: Long
        val returnUri: Uri

        when(match){
            EVENTS->{
                val db=AppDatabase.getInstance(context!!).writableDatabase
                recordId=db.insert(EventsContract.TABLE_NAME,null,values)
                if(recordId!=-1L){
                    returnUri = EventsContract.buildUriFromId(recordId)
                }else{
                    throw SQLException("Failed to insert, Uri was $uri")
                }
            }
            ATTENDEES->{
                val db=AppDatabase.getInstance(context!!).writableDatabase
                recordId=db.insert(AttendeesContract.TABLE_NAME,null,values)
                if(recordId!=-1L){
                    returnUri=AttendeesContract.buildUriFromId(recordId)
                }else{
                    throw SQLException("Failed to insert, Uri was $uri")
                }
            }
            else-> throw IllegalArgumentException("Unknown Uri: $uri")
        }

        if (recordId > 0) {
            // something was inserted
            Log.d(TAG, "insert: Setting notifyChange with $uri")
            context?.contentResolver?.notifyChange(uri, null)
        }

        Log.d(TAG,"Exiting insert, returning $returnUri")
        return returnUri
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.d(TAG, "update: called with uri $uri")
        val match = uriMatcher.match(uri)
        Log.d(TAG, "update: match is $match")

        //count is the number of rows that has been updated
        val count: Int
        var selectionCriteria: String

        when(match){
            EVENTS->{
                val db=AppDatabase.getInstance(context!!).writableDatabase
                count=db.update(EventsContract.TABLE_NAME,values,selection,selectionArgs)
            }

            EVENTS_ID->{
                val db=AppDatabase.getInstance(context!!).writableDatabase
                val id=EventsContract.getId(uri)
                //selectionCriteria in WHERE clause (which row to update)
                selectionCriteria="${EventsContract.Columns.ID}=$id"
                if(selection!=null && selection.isNotEmpty()){
                    selectionCriteria +=" AND ($selection)"
                }

                count=db.update(EventsContract.TABLE_NAME,values,selectionCriteria,selectionArgs)
            }

            ATTENDEES->{
                val db=AppDatabase.getInstance(context!!).writableDatabase
                count=db.update(AttendeesContract.TABLE_NAME,values,selection,selectionArgs)
            }

            ATTENDEES_ID->{
                val db=AppDatabase.getInstance(context!!).writableDatabase
                val id=AttendeesContract.getId(uri)
                selectionCriteria="${AttendeesContract.Columns.ID}=$id"
                if(selection!=null && selection.isNotEmpty()){
                    selectionCriteria +=" AND ($selection)"
                }
                count=db.update(AttendeesContract.TABLE_NAME,values,selectionCriteria,selectionArgs)
            }

            else-> throw IllegalArgumentException("Unknown uri: $uri")
        }

        if (count > 0) {
            // something was updated
            Log.d(TAG, "update: Setting notifyChange with $uri")
            context?.contentResolver?.notifyChange(uri, null)
        }

        Log.d(TAG, "Exiting update, returning $count")
        return count
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.d(TAG,"delete: called with uri $uri")
        val match = uriMatcher.match(uri)
        Log.d(TAG, "delete: match is $match")

        val count: Int
        var selectionCriteria: String

        when(match){
            EVENTS->{
                val db = AppDatabase.getInstance(context!!).writableDatabase
                count=db.delete(EventsContract.TABLE_NAME,selection,selectionArgs)
            }

            EVENTS_ID->{
                val db = AppDatabase.getInstance(context!!).writableDatabase
                val id =EventsContract.getId(uri)
                selectionCriteria = "${EventsContract.Columns.ID}=$id"

                if(selection != null && selection.isNotEmpty()) {
                    selectionCriteria += " AND ($selection)"
                }

                count=db.delete(EventsContract.TABLE_NAME,selectionCriteria,selectionArgs)
            }

            ATTENDEES->{
                val db = AppDatabase.getInstance(context!!).writableDatabase
                count=db.delete(AttendeesContract.TABLE_NAME,selection,selectionArgs)
            }

            ATTENDEES_ID->{
                val db = AppDatabase.getInstance(context!!).writableDatabase
                val id =AttendeesContract.getId(uri)
                selectionCriteria = "${AttendeesContract.Columns.ID}=$id"

                if(selection != null && selection.isNotEmpty()) {
                    selectionCriteria += " AND ($selection)"
                }

                count=db.delete(AttendeesContract.TABLE_NAME,selectionCriteria,selectionArgs)
            }
            else-> throw IllegalArgumentException("Unknown uri: $uri")
        }

        if (count > 0) {
            // something was deleted
            Log.d(TAG, "delete: Setting notifyChange with $uri")
            context?.contentResolver?.notifyChange(uri, null)
        }

        Log.d(TAG, "Exiting delete, returning $count")
        return count

    }


}