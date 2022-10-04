package com.example.teacher

import android.content.ContentUris
import android.net.Uri


//object provides a single instance of something
object AttendeesContract {
    internal const val TABLE_NAME="Attendees"

    /**
     * The URI to access the Attendees table.
     */
    val CONTENT_URI: Uri = Uri.withAppendedPath(CONTENT_AUTHORITY_URI, TABLE_NAME)

    //const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$CONTENT_AUTHORITY.$TABLE_NAME"
  //  const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$CONTENT_AUTHORITY.$TABLE_NAME"

    //Attendees fields
    object Columns{
        const val ID="_id"
        //event Id (since 1 Event to N Attendees)
        const val ATTENDEES_EVENT_ID="EventId"
        const val MAC="MacAddress"
    }

    fun getId(uri:Uri): Long{
        return ContentUris.parseId(uri)
    }
    fun buildUriFromId(id: Long): Uri{
        return ContentUris.withAppendedId(CONTENT_URI,id)
    }
}