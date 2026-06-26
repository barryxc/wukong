package io.github.barryxc.wukong.model

import io.github.barryxc.wukong.adapter.SettingRecyclerAdapter


class ItemData {
    var key: String? = null
    var value: Any? = false
    var describe: String? = null
    var type = SettingRecyclerAdapter.TYPE_SWITCH
    var enabled = true

    constructor(key: String?, value: Any?) {
        this.key = key
        this.value = value
    }

    constructor(key: String?, value: Any?, describe: String?) : this(key, value) {
        this.describe = describe
    }

    constructor(key: String?, value: Any?, describe: String?, type: Int) : this(
        key,
        value,
        describe
    ) {
        this.type = type
    }

    constructor(
        key: String?,
        value: Any?,
        describe: String?,
        type: Int,
        enabled: Boolean,
    ) : this(key, value, describe, type) {
        this.enabled = enabled
    }

    override fun toString(): String {
        return "SwitchData{" +
                "key='" + key + '\'' +
                ", value=" + value +
                ", describe='" + describe + '\'' +
                ", type=" + type +
                '}'
    }
}
