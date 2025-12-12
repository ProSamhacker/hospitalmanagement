package com.example.hospitalmanagement.ADAPTER

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.hospitalmanagement.Prescription
import com.example.hospitalmanagement.R

class PrescriptionAdapter(
    private var items: List<Prescription>,
    private val tts: TextToSpeech?
) : RecyclerView.Adapter<PrescriptionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMedName)
        val tvInstructions: TextView = view.findViewById(R.id.tvInstructions)
        val btnPlay: LinearLayout = view.findViewById(R.id.btnPlayAudio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // This links to the row layout 'item_prescription.xml' you created earlier
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prescription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text = item.medicationName
        holder.tvInstructions.text = item.instructions

        // NICHE FEATURE: Patient can click "Listen" to hear the doctor's instructions
        holder.btnPlay.setOnClickListener {
            val speech = "Take ${item.medicationName}. ${item.instructions}"
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Prescription>) {
        items = newItems
        notifyDataSetChanged()
    }
}