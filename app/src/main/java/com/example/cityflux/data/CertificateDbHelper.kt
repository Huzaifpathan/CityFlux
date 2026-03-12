package com.example.cityflux.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CertificateEntry(
    val id: Long = 0,
    val name: String = "",
    val status: String = "Pending",
    val progress: Float = 0f,
    val issuedDate: String = "",
    val icon: String = "verified"
)

class CertificateDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "cityflux_police.db"
        private const val DB_VERSION = 1
        private const val TABLE = "certificates"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'Pending',
                progress REAL NOT NULL DEFAULT 0.0,
                issued_date TEXT NOT NULL DEFAULT '',
                icon TEXT NOT NULL DEFAULT 'verified'
            )
        """)
        insertDefault(db, "Traffic Management", "Certified", 1.0f, "2024-01-15", "verified")
        insertDefault(db, "First Aid & CPR", "Certified", 1.0f, "2024-03-20", "health")
        insertDefault(db, "Crowd Control", "In Progress", 0.65f, "", "groups")
        insertDefault(db, "Cyber Crime Basics", "Pending", 0.1f, "", "computer")
    }

    private fun insertDefault(db: SQLiteDatabase, name: String, status: String, progress: Float, issuedDate: String, icon: String) {
        val values = ContentValues().apply {
            put("name", name)
            put("status", status)
            put("progress", progress)
            put("issued_date", issuedDate)
            put("icon", icon)
        }
        db.insert(TABLE, null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun getAll(): List<CertificateEntry> {
        val list = mutableListOf<CertificateEntry>()
        val db = readableDatabase
        val cursor = db.query(TABLE, null, null, null, null, null, "id ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CertificateEntry(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        status = it.getString(it.getColumnIndexOrThrow("status")),
                        progress = it.getFloat(it.getColumnIndexOrThrow("progress")),
                        issuedDate = it.getString(it.getColumnIndexOrThrow("issued_date")),
                        icon = it.getString(it.getColumnIndexOrThrow("icon"))
                    )
                )
            }
        }
        return list
    }

    fun insert(entry: CertificateEntry): Long {
        val values = ContentValues().apply {
            put("name", entry.name)
            put("status", entry.status)
            put("progress", entry.progress)
            put("issued_date", entry.issuedDate)
            put("icon", entry.icon)
        }
        return writableDatabase.insert(TABLE, null, values)
    }

    fun update(entry: CertificateEntry) {
        val values = ContentValues().apply {
            put("name", entry.name)
            put("status", entry.status)
            put("progress", entry.progress)
            put("issued_date", entry.issuedDate)
            put("icon", entry.icon)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(entry.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }
}
