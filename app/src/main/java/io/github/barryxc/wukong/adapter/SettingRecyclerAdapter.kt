package io.github.barryxc.wukong.adapter

import android.text.TextUtils
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import io.github.barryxc.wukong.R
import io.github.barryxc.wukong.model.ItemData

class SettingRecyclerAdapter : BaseRecyclerAdapter<ItemData> {
    companion object {
        @JvmField
        val TYPE_SWITCH = 0

        @JvmField
        val TYPE_BUTTON = 1

        @JvmField
        val TYPE_EDITTEXT = 2
    }

    private var mOnItemClickListener: OnItemClickListener? = null

    constructor(data: List<ItemData>?) : super(data)

    override fun getLayoutId(viewType: Int): Int {
        return if (viewType == TYPE_BUTTON) {
            R.layout.item_setting_button
        } else if (viewType == TYPE_EDITTEXT) {
            R.layout.item_setting_editext
        } else {
            R.layout.item_setting_switch
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
        switchCompat.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked -> //防止setChecked的时候触发监听
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
        editText!!.hint = "请输入"
        if (!(TextUtils.isEmpty(data.value as? String))) {
            editText.setText(data.value as? String)
        }
        val tvDescribe = holder.getView<TextView>(R.id.tv_describe)
        if (!TextUtils.isEmpty(data.describe)) {
            tvDescribe?.text = data.describe
        }
        val button = holder.getView<AppCompatButton>(R.id.setting_button)
        button!!.text = data.key
        button.setOnClickListener {
            if (mOnItemClickListener != null) {
                val partnerCode = editText.text.toString()
                mOnItemClickListener!!.onItemClick(position, false, partnerCode)
            }
        }
    }

    fun updateData(data: List<ItemData?>?) {
        mData = data as List<ItemData>?
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        mOnItemClickListener = listener
    }
}