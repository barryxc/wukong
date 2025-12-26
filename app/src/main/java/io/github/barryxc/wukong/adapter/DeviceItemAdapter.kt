package io.github.barryxc.wukong.adapter

import android.content.Context
import android.view.View
import android.widget.TextView
import io.github.barryxc.wukong.R
import io.github.barryxc.wukong.model.FieldData

class DeviceItemAdapter(context: Context?, data: MutableList<FieldData?>?) :
    SimpleBaseAdapter<FieldData?>(context, data) {
    override fun getItemResource(): Int {
        return R.layout.device_info
    }

    public override fun getItemView(
        position: Int,
        convertView: View?,
        viewHolder: ViewHolder
    ): View? {
        val tvDevicekey: TextView = viewHolder.getView(R.id.tv_device_key)
        val tvDeviceValue: TextView = viewHolder.getView(R.id.tv_device_value)
        //tvDeviceValue.setLines(3);
        tvDeviceValue.setSingleLine(false)
        //tvDeviceValue.setEllipsize(null);
        tvDevicekey.setText((getItem(position) as FieldData).key)
        tvDeviceValue.setText((getItem(position) as FieldData).value)
        return convertView
    }
}
