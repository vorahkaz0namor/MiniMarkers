package ru.sign.conditional.minimarkers.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MapViewModel : ViewModel() {
    // Variable to hold place name of the placemark
    private val _placeName = MutableLiveData("")
    val placeName: LiveData<String>
        get() = _placeName
    private var _editName = ""
    val editName: String
        get() = _editName
    private val _shouldRemove = MutableLiveData(false)
    val shouldRemove: LiveData<Boolean>
        get() = _shouldRemove

    fun setPlaceName(placeName: String) {
        _placeName.value = placeName
    }

    fun setEditName(placeName: String) {
        _editName = placeName
    }

    fun shouldRemove() {
        _shouldRemove.value = true
    }

    fun clear() {
        _placeName.value = ""
        _editName = ""
        _shouldRemove.value = false
    }
}