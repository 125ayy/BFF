package com.fcapps.bff

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class CountrySpinnerAdapter(context: Context, private val countries: List<Country>) :
    ArrayAdapter<Country>(context, R.layout.item_spinner_country_selected, countries) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val tv = view.findViewById<TextView>(R.id.tvCountrySelected)
            ?: (view as? TextView)
            ?: return view
        tv.text = countries[position].toString()
        tv.setTextColor(0xFFFFFFFF.toInt())
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = android.view.LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.item_spinner_country, parent, false)
        val tv = view.findViewById<TextView>(R.id.tvCountryItem)
        tv.text = countries[position].toString()
        tv.setTextColor(0xFFFFFFFF.toInt())
        tv.setBackgroundColor(0xFF1A1A1A.toInt())
        return view
    }
}
