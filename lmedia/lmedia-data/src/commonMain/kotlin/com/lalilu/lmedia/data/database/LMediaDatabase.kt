package com.lalilu.lmedia.data.database

interface ILMediaDatabase {
    fun audioDao(): LAudioDao
    fun artistDao(): LArtistDao
    fun albumDao(): LAlbumDao
    fun genreDao(): LGenreDao
    fun folderDao(): LFolderDao
    fun mediaDao(): LMediaDao
    fun historyDao(): LHistoryDao
}