package com.pkj.leanbulk

import android.app.Application
import com.pkj.leanbulk.data.AppDatabase
import com.pkj.leanbulk.data.Repository

class SimpleHealthApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.get(this))
    }
}
