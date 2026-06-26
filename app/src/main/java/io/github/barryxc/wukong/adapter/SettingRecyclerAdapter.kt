package io.github.barryxc.wukong.adapter

import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import io.github.barryxc.wukong.R
import io.github.barryxc.wukong.model.ItemData
import io.github.barryxc.wukong.shared.DEFAULT_BRAND
import io.github.barryxc.wukong.shared.DEFAULT_MODEL
import io.github.barryxc.wukong.shared.DeviceProfiles

class SettingRecyclerAdapter : BaseRecyclerAdapter<ItemData> {
    companion object {
        @JvmField
        val TYPE_SWITCH = 0

        @JvmField
        val TYPE_BUTTON = 1

        @JvmField
        val TYPE_EDITTEXT = 2

        @JvmField
        val TYPE_SELECT = 3

        @JvmField
        val TYPE_READONLY = 4

        const val ANDROID_ID_TITLE = "Android ID"
        const val LOCATION_TITLE = "Location (lat,lng,alt,accuracy)"
        const val BRAND_TITLE = "Brand"
        const val MODEL_TITLE = "Model"
        const val PACKAGE_NAME_TITLE = "Mock package name"
        const val PROXY_TITLE = "HTTP proxy (host:port)"

        fun defaultModelForBrand(brand: String?): String {
            return DeviceProfiles.defaultModelForBrand(brand)
        }

        fun isModelForBrand(brand: String?, model: String?): Boolean {
            return DeviceProfiles.isModelForBrand(brand, model)
        }

        private fun modelOptionsForBrand(brand: String?): List<SelectOption> {
            if (brand.isNullOrBlank()) {
                return listOf(SelectOption("不修改 / 原值", ""))
            }
            return DeviceProfiles.devicesForBrand(brand)
                .map { SelectOption(it.label, it.model) }
        }
    }

    private var mOnItemClickListener: OnItemClickListener? = null

    constructor(data: List<ItemData>?) : super(data)

    override fun getLayoutId(viewType: Int): Int {
        return when (viewType) {
            TYPE_BUTTON -> R.layout.item_setting_button
            TYPE_EDITTEXT -> R.layout.item_setting_editext
            TYPE_SELECT -> R.layout.item_setting_select
            TYPE_READONLY -> R.layout.item_setting_readonly
            else -> R.layout.item_setting_switch
        }
    }

    override fun getItemViewType(position: Int): Int {
        return mData!![position].type
    }

    override fun convert(holder: BaseViewHolder?, data: ItemData, position: Int) {
        val type = getItemViewType(position)
        when (type) {
            TYPE_SWITCH -> holder?.let { setTypeSwitchView(it, data, position) }
            TYPE_BUTTON -> holder?.let { setTypeButtonView(it, data, position) }
            TYPE_EDITTEXT -> holder?.let { setTypeEdittextView(it, data, position) }
            TYPE_SELECT -> holder?.let { setTypeSelectView(it, data, position) }
            TYPE_READONLY -> holder?.let { setTypeReadonlyView(it, data, position) }
        }
    }

    private fun setTypeReadonlyView(holder: BaseViewHolder, data: ItemData, position: Int) {
        holder.setText(R.id.tv_key, data.key)
        val valueView = holder.getView<TextView>(R.id.tv_value)
        val enabled = data.value as? Boolean ?: false
        valueView?.text = enabled.toString()
        valueView?.alpha = if (enabled) 1.0f else 0.62f

        val describeView = holder.getView<TextView>(R.id.tv_describe)
        describeView?.text = data.describe.orEmpty()
        holder.itemView.setOnClickListener {
            mOnItemClickListener?.onItemClick(position, false, null)
        }
    }

    private fun setTypeSwitchView(holder: BaseViewHolder, data: ItemData, position: Int) {
        holder.setText(R.id.tv_key, data.key)
        val tv_describe = holder.getView<TextView>(R.id.tv_describe)
        if (!TextUtils.isEmpty(data.describe)) {
            tv_describe!!.visibility = View.VISIBLE
            tv_describe.text = data.describe
        } else {
            tv_describe!!.visibility = View.GONE
        }
        val switchCompat = holder.getView<SwitchCompat>(R.id.setting_switch)
        switchCompat!!.isChecked = data.value as Boolean
        switchCompat.isEnabled = data.enabled
        switchCompat.alpha = if (data.enabled) 1.0f else 0.55f
        switchCompat.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked -> //防止setChecked的时候触发监听
            if (!data.enabled) {
                return@OnCheckedChangeListener
            }
            if (!buttonView.isPressed) {
                return@OnCheckedChangeListener
            }
            if (mOnItemClickListener != null) {
                mOnItemClickListener!!.onItemClick(position, isChecked, null)
            }
        })
    }

    private fun setTypeButtonView(holder: BaseViewHolder, data: ItemData, position: Int) {
        holder.setText(R.id.tv_key, data.key)
        val tv_describe = holder.getView<TextView>(R.id.tv_describe)
        tv_describe!!.visibility = View.GONE
        val button = holder.getView<AppCompatButton>(R.id.setting_button)
        button!!.text = data.describe
        button.setOnClickListener {
            if (mOnItemClickListener != null) {
                mOnItemClickListener!!.onItemClick(position, false, null)
            }
        }
    }

    private fun setTypeEdittextView(holder: BaseViewHolder, data: ItemData, position: Int) {
        val editText = holder.getView<EditText>(R.id.tv_key)
        val title = data.describe.orEmpty()
        editText!!.hint = buildHint(title)
        (editText.tag as? TextWatcher)?.let {
            editText.removeTextChangedListener(it)
        }
        editText.setText(data.value as? String ?: "")
        editText.setSelection(editText.text?.length ?: 0)
        editText.isEnabled = data.enabled
        editText.alpha = if (data.enabled) 1.0f else 0.65f
        if (title.contains("Target", ignoreCase = true)) {
            editText.minLines = 2
            editText.maxLines = 4
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            editText.minLines = 1
            editText.maxLines = 1
            editText.inputType = InputType.TYPE_CLASS_TEXT
        }
        val tvDescribe = holder.getView<TextView>(R.id.tv_describe)
        if (!TextUtils.isEmpty(data.describe)) {
            tvDescribe?.text = data.describe
        }
        val button = holder.getView<AppCompatButton>(R.id.setting_button)
        if (data.enabled && (
            title == ANDROID_ID_TITLE ||
            title == LOCATION_TITLE ||
            title == PACKAGE_NAME_TITLE ||
            title == PROXY_TITLE
        )) {
            button?.visibility = View.VISIBLE
            button?.text = when (title) {
                LOCATION_TITLE -> "定位"
                PROXY_TITLE -> "MAC IP"
                else -> "随机"
            }
            button?.setOnClickListener {
                mOnItemClickListener?.onItemClick(position, false, editText.text.toString())
            }
        } else {
            button?.visibility = View.GONE
            button?.setOnClickListener(null)
        }
        val clearButton = holder.getView<AppCompatImageButton>(R.id.clear_button)
        clearButton?.visibility =
            if (data.enabled && !editText.text.isNullOrEmpty()) View.VISIBLE else View.GONE
        clearButton?.setOnClickListener {
            editText.text?.clear()
            editText.requestFocus()
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!data.enabled) {
                    return
                }
                data.value = s?.toString().orEmpty()
                clearButton?.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }
        editText.addTextChangedListener(watcher)
        editText.tag = watcher
    }

    private fun setTypeSelectView(holder: BaseViewHolder, data: ItemData, adapterPosition: Int) {
        val title = data.describe.orEmpty()
        val tvDescribe = holder.getView<TextView>(R.id.tv_describe)
        tvDescribe?.text = title

        val spinner = holder.getView<AppCompatSpinner>(R.id.setting_spinner) ?: return
        val options = selectOptions(title)
        if (options.isEmpty()) {
            return
        }
        val adapter = ArrayAdapter(
            spinner.context,
            android.R.layout.simple_spinner_item,
            options.map { it.label }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.onItemSelectedListener = null
        spinner.adapter = adapter
        val currentValue = data.value as? String
        val selectedIndex = options.indexOfFirst {
            it.value == currentValue || it.value.equals(currentValue, ignoreCase = true)
        }.takeIf { it >= 0 } ?: 0
        data.value = options[selectedIndex].value
        spinner.setSelection(selectedIndex, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                val selectedValue = options.getOrNull(position)?.value.orEmpty()
                val previousValue = data.value as? String
                data.value = selectedValue
                if (selectedValue != previousValue) {
                    mOnItemClickListener?.onItemClick(adapterPosition, false, selectedValue)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun selectOptions(title: String): List<SelectOption> {
        return when (title) {
            BRAND_TITLE -> listOf(SelectOption("不修改 / 原值", "")) +
                    DeviceProfiles.brands.map { SelectOption(it.label, it.brand) }

            MODEL_TITLE -> modelOptionsForBrand(currentBrand())
            else -> emptyList()
        }
    }

    private fun currentBrand(): String? {
        return mData
            ?.firstOrNull { it.describe == BRAND_TITLE }
            ?.value as? String
    }

    private fun buildHint(title: String): String {
        return when {
            title.contains("Location", ignoreCase = true) -> "30,120,0,0.5"
            title.contains("Android", ignoreCase = true) -> "1234567890abcdef"
            title.contains("Target", ignoreCase = true) ->
                "com.example.app, com.example.demo"

            title == PACKAGE_NAME_TITLE -> "com.example.mock"
            title == BRAND_TITLE -> DEFAULT_BRAND
            title == MODEL_TITLE -> DEFAULT_MODEL
            title.contains("Proxy", ignoreCase = true) -> "点击按钮解析 barry-mac.local"
            else -> "请输入"
        }
    }

    fun updateData(data: List<ItemData?>?) {
        mData = data as List<ItemData>?
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        mOnItemClickListener = listener
    }

    private data class SelectOption(
        val label: String,
        val value: String,
    )
}
