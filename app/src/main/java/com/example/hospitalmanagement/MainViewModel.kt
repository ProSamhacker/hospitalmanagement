package com.example.hospitalmanagement

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(private val repository: MedicationRepository) : ViewModel() {

    private val _medications = MutableLiveData<List<Medication>>()
    val medications: LiveData<List<Medication>> = _medications

    private val _responseMessage = MutableLiveData<String>()
    val responseMessage: LiveData<String> = _responseMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadMedications()
    }

    fun loadMedications() {
        viewModelScope.launch {
            _medications.value = repository.getAllMedications()
        }
    }

    fun addMedicationManually(name: String, section: String) {
        viewModelScope.launch {
            val finalSection = if (section.isBlank()) "General" else section
            repository.insertMedication(Medication(name = name, section = finalSection))
            _responseMessage.value = "Added '$name' to section $finalSection."
            loadMedications()
        }
    }

    fun processVoiceCommand(command: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val processedCommand = command.replace(" 2 ", " to ", ignoreCase = true)
                var response: String
                val id = extractIdFromCommand(processedCommand)

                when {
                    id != null -> {
                        val listIndex = id - 1
                        val currentMeds = _medications.value ?: emptyList()
                        if (listIndex >= 0 && listIndex < currentMeds.size) {
                            val selectedMed = currentMeds[listIndex]
                            when {
                                processedCommand.contains("delete", ignoreCase = true) -> {
                                    repository.deleteMedication(selectedMed)
                                    response = "Deleted ID ${id}: '${selectedMed.name}'."
                                }
                                processedCommand.contains("update", ignoreCase = true) || processedCommand.contains("change", ignoreCase = true) -> {
                                    val newInfoPattern = Regex("(?i)\\bto\\b\\s+(.*)")
                                    val newInfoMatch = newInfoPattern.find(processedCommand)
                                    if (newInfoMatch != null) {
                                        val newInfoRaw = newInfoMatch.groupValues[1].trim()
                                        val newName = repository.correctMedicationSpelling(extractMedicationName(newInfoRaw, ""))
                                        val newSection = extractSection(newInfoRaw, "")
                                        val updatedMed = selectedMed.copy(
                                            name = if(newName.isNotBlank()) newName else selectedMed.name,
                                            section = if(newSection != "General") newSection else selectedMed.section
                                        )
                                        repository.updateMedication(updatedMed)
                                        response = "Updated ID ${id} to '${updatedMed.name}' in section ${updatedMed.section}."
                                    } else {
                                        response = "Please specify what to update ID $id to."
                                    }
                                }
                                else -> response = "Command for ID $id not understood. Please say 'delete' or 'update'."
                            }
                        } else {
                            response = "ID number $id is not in the list."
                        }
                    }
                    processedCommand.contains("add", ignoreCase = true) -> {
                        val section = extractSection(processedCommand, "add")
                        val rawMedName = extractMedicationName(processedCommand, "add")
                        if (rawMedName.isBlank() || rawMedName.equals("medication", ignoreCase = true)) {
                            response = "Couldn’t detect the medication name. Please try again."
                        } else {
                            val correctedName = repository.correctMedicationSpelling(rawMedName)
                            repository.insertMedication(Medication(name = correctedName, section = section))
                            response = "Medication '$correctedName' added to $section."
                        }
                    }
                    processedCommand.contains("delete all", ignoreCase = true) -> {
                        repository.deleteAllMedications()
                        response = "All medications have been deleted."
                    }
                    processedCommand.contains("delete", ignoreCase = true) -> {
                        val rawMedName = extractMedicationName(processedCommand, "delete")
                        val allMeds = repository.getAllMedications()
                        val bestMatch = findBestMatch(rawMedName, allMeds)
                        if (bestMatch != null) {
                            repository.deleteMedication(bestMatch)
                            response = "Medication '${bestMatch.name}' deleted."
                        } else {
                            response = "No medication similar to '$rawMedName' found to delete."
                        }
                    }
                    processedCommand.contains("update", ignoreCase = true) || processedCommand.contains("change", ignoreCase = true) -> {
                        val action = if (processedCommand.contains("update", ignoreCase = true)) "update" else "change"
                        val pattern = Regex("(?i)\\b$action\\b\\s+(.*?)\\s+\\bto\\b\\s+(.*)")
                        val match = pattern.find(processedCommand)
                        if (match != null) {
                            val oldNameRaw = match.groupValues[1].trim()
                            val newInfoRaw = match.groupValues[2].trim()
                            val allMeds = repository.getAllMedications()
                            val bestMatch = findBestMatch(oldNameRaw, allMeds)
                            if (bestMatch != null) {
                                var newName = repository.correctMedicationSpelling(extractMedicationName(newInfoRaw, ""))
                                var newSection = extractSection(newInfoRaw, "")
                                if (newName.isBlank()) newName = bestMatch.name
                                if (newSection == "General") newSection = bestMatch.section
                                val updatedMed = bestMatch.copy(name = newName, section = newSection)
                                repository.updateMedication(updatedMed)
                                response = "Updated '${bestMatch.name}' to '${updatedMed.name}' in section ${updatedMed.section}."
                            } else {
                                response = "No medication similar to '$oldNameRaw' found to update."
                            }
                        } else {
                            response = "Couldn't understand. Please say: 'update [old name] to [new name]'."
                        }
                    }
                    processedCommand.contains("move", ignoreCase = true) -> {
                        val rawMedName = extractMedicationName(processedCommand, "move")
                        val newSection = extractSection(processedCommand, "move")
                        val allMeds = repository.getAllMedications()
                        val bestMatch = findBestMatch(rawMedName, allMeds)
                        if (bestMatch != null) {
                            if (newSection != "General") {
                                val updatedMed = bestMatch.copy(section = newSection)
                                repository.updateMedication(updatedMed)
                                response = "Medication '${bestMatch.name}' moved to section $newSection."
                            } else {
                                response = "Please specify which section to move '${bestMatch.name}' to."
                            }
                        } else {
                            response = "No medication similar to '$rawMedName' found to move."
                        }
                    }
                    else -> {
                        response = repository.queryGemini(processedCommand)
                    }
                }
                _responseMessage.value = response
                loadMedications()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun findBestMatch(name: String, medications: List<Medication>): Medication? {
        if (name.isBlank()) return null
        var bestMatch: Medication? = null
        var minDistance = Int.MAX_VALUE
        val threshold = 3
        for (med in medications) {
            val distance = calculateLevenshteinDistance(name.lowercase(), med.name.lowercase())
            if (distance < minDistance) {
                minDistance = distance
                bestMatch = med
            }
        }
        return if (minDistance <= threshold) bestMatch else null
    }

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) {
            for (j in 0..s2.length) {
                when {
                    i == 0 -> dp[i][j] = j
                    j == 0 -> dp[i][j] = i
                    else -> {
                        val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                        dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                    }
                }
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun extractIdFromCommand(command: String): Int? {
        val pattern = Regex("(?i)\\b(?:ID|number|item)\\b\\s+(\\d+)")
        val match = pattern.find(command)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractSection(command: String, action: String): String {
        val relevantText = if (action.isBlank()) command else command.substringAfter(action)
        val pattern = Regex("(?i)\\b(?:to|in|into|at)\\b\\s+([\\w\\s\\-]+)")
        val match = pattern.find(relevantText)
        return match?.groups?.get(1)?.value?.trim() ?: "General"
    }

    private fun extractMedicationName(command: String, action: String): String {
        val relevantText = if (action.isBlank()) command else command.substringAfter(action)
        val pattern = Regex("(?i)^\\s*(.*?)(?:\\s+\\b(?:to|in|into|at)\\b|$)")
        val match = pattern.find(relevantText)
        return match?.groups?.get(1)?.value?.trim() ?: ""
    }

    class Factory(private val repo: MedicationRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}