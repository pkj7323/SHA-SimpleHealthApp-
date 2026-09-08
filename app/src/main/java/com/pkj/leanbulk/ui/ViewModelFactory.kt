package com.pkj.leanbulk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pkj.leanbulk.data.Repository
import com.pkj.leanbulk.notify.NoRestAlarms
import com.pkj.leanbulk.notify.RestAlarms

class ViewModelFactory(
    private val repository: Repository,
    private val restAlarms: RestAlarms = NoRestAlarms
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, restAlarms) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
