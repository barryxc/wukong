package io.github.barryxc.wukong.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter


class ViewPagerAdapter : FragmentPagerAdapter {
    private var titleList: List<String> = ArrayList()
    private var fragmentList: List<Fragment> = ArrayList()

    constructor (
        fm: FragmentManager,
        titleList: List<String>,
        fragmentList: List<Fragment>
    ) : super(fm) {
        this.titleList = titleList
        this.fragmentList = fragmentList
    }

    override fun getPageTitle(position: Int): CharSequence {
        return titleList[position]
    }

    override fun getItem(position: Int): Fragment {
        return fragmentList[position]
    }

    override fun getCount(): Int {
        return fragmentList.size
    }
}