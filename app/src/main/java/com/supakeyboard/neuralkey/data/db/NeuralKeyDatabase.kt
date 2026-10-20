package com.supakeyboard.neuralkey.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.supakeyboard.neuralkey.data.db.dao.ModelDao
import com.supakeyboard.neuralkey.data.db.dao.UserPromptDao
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelEntity
import com.supakeyboard.neuralkey.data.db.entity.OpenRouterModelFtsEntity
import com.supakeyboard.neuralkey.data.db.entity.UserPromptEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        OpenRouterModelEntity::class,
        OpenRouterModelFtsEntity::class,
        UserPromptEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NeuralKeyDatabase : RoomDatabase() {

    abstract fun modelDao(): ModelDao
    abstract fun userPromptDao(): UserPromptDao

    companion object {
        private const val DATABASE_NAME = "neuralkey_database"

        @Volatile
        private var instance: NeuralKeyDatabase? = null

        fun getInstance(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): NeuralKeyDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context, scope).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context, scope: CoroutineScope): NeuralKeyDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NeuralKeyDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch {
                        getInstance(context).userPromptDao().insertPrompts(UserPromptEntity.defaultPrompts())
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    scope.launch {
                        val dao = getInstance(context).userPromptDao()
                        if (dao.getPromptCount() < UserPromptEntity.defaultPrompts().size) {
                            dao.insertPrompts(UserPromptEntity.defaultPrompts())
                        }
                    }
                }
            }).build()
        }
    }
}
