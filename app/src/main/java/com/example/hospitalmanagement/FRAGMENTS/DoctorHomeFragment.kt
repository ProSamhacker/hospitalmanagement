package com.example.hospitalmanagement.FRAGMENTS

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hospitalmanagement.AppointmentStatus
import com.example.hospitalmanagement.MainViewModel
import com.example.hospitalmanagement.R

// ===== Doctor Home Fragment =====
class DoctorHomeFragment : Fragment() {
    private var tvLiveTranscript: TextView? = null
    private var tvDoctorName: TextView? = null
    private var tvScheduleCount: TextView? = null
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_doctor_home, container, false)

        tvLiveTranscript = view.findViewById(R.id.tvLiveTranscript)
        tvDoctorName = view.findViewById(R.id.tvDoctorName)
        tvScheduleCount = view.findViewById(R.id.tvScheduleCount)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        observeData()

        return view
    }

    private fun observeData() {
        viewModel.currentDoctor.observe(viewLifecycleOwner) { doctor ->
            doctor?.let {
                tvDoctorName?.text = it.name
            }
        }

        viewModel.upcomingAppointments.observe(viewLifecycleOwner) { appointments ->
            val scheduledCount = appointments.count { it.status == AppointmentStatus.SCHEDULED }
            tvScheduleCount?.text = "$scheduledCount Appointments Remaining"
        }

        viewModel.consultationTranscript.observe(viewLifecycleOwner) { transcript ->
            updateTranscript(transcript)
        }
    }

    fun updateTranscript(text: String) {
        tvLiveTranscript?.text = if (text.isBlank()) {
            "Tap the mic below to start a consultation session..."
        } else {
            text
        }
    }
}
