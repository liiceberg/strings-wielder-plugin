package com.liiceberg.utils

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
        name = "LocalStorage",
        storages = [Storage(Constants.Preferences.STRING_WIELDER_PREFERENCES)])

object LocalStorage : PersistentStateComponent<LocalStorage.State> {

    object State {
        var dataMap = mutableMapOf<String, String>()
    }

    private var stateCopy: State? = State

    override fun getState() = stateCopy

    override fun loadState(state: State) {
        stateCopy = state
    }

    fun getData(key: String): String? {
        return State.dataMap[key]
    }

    fun setData(key: String, value: String) {
        State.dataMap[key] = value
    }

}
