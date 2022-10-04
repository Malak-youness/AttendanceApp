package com.example.teacher

import android.content.ContentUris
import android.net.Uri

//object provides a single instance of something
object EventsContract {

    internal const val TABLE_NAME="Events"

    /**
     * The URI to access the Events table
     * */
    val CONTENT_URI: Uri=Uri.withAppendedPath(CONTENT_AUTHORITY_URI, TABLE_NAME)

    //Constants that are used in AppProvider.getType
    //const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$CONTENT_AUTHORITY.$TABLE_NAME"
   // const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.$CONTENT_AUTHORITY.$TABLE_NAME"


    //Events fields
    object Columns{
        const val ID="_id"
        const val EVENT_NAME="Name"
        const val EVENT_DATE="Date"
        const val EVENT_TIME="Time"
    }

    //Extracts id from Uri
    fun getId(uri:Uri):Long{
        return ContentUris.parseId(uri)
    }

    //builds Uri from id
    fun buildUriFromId(id:Long):Uri{
        return ContentUris.withAppendedId(CONTENT_URI,id)
    }

}