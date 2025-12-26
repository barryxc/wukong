package io.github.barryxc.wukong.adapter

import android.util.SparseArray
import android.view.View

class ViewHolder(private val convertView: View) {
    private val views = SparseArray<View?>()
    fun <T : View?> getView(resId: Int): T {
        var view = views[resId]
        if (view == null) {
            view = convertView.findViewById(resId)
            views.put(resId, view)
        }
        return view as T
    }
}