package com.example.hospitalmanagement

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class DoctorHomeFragment : Fragment() {
    private var tvLiveTranscript: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_doctor_home, container, false)
        tvLiveTranscript = view.findViewById(R.id.tvLiveTranscript)
        return view
    }

    // Helper method to update text from Activity
    fun updateTranscript(text: String) {
        tvLiveTranscript?.text = text
    }
}